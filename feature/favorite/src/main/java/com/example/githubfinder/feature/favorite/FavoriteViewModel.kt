/*
 * Abstract:
 * お気に入り一覧の状態管理を担当するViewModel。UDF単一状態原則に従い、
 * FavoriteUiState 1つに全状態（フェーズ・リフレッシュ・編集モード・選択）を集約する。
 *
 * 一度きりの通知（SnackBar 表示等）は userMessages（StateFlow<List<UserMessage>>）に積み、
 * View が表示し終えたら userMessageShown(id) で消す（公式ガイドの UI イベント → State 化パターン）。
 * 削除の Undo 猶予タイマーと実削除の commit も ViewModel が持つ（View の寿命に依存させない）。
 * ViewModel が破棄される時は、猶予中の削除を onCleared でアプリ寿命スコープから即 commit する。
 */

package com.example.githubfinder.feature.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.githubfinder.core.data.FavoriteStore
import com.example.githubfinder.core.data.di.ApplicationScope
import com.example.githubfinder.core.model.AppError
import com.example.githubfinder.core.model.FavoriteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Undo 猶予時間（ミリ秒）。Material の SnackbarDuration.Short（約 4 秒）に合わせ、経過後に Room から実削除する。 */
internal const val UNDO_WINDOW_MS = 4_000L

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val favoriteStore: FavoriteStore,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    // 最初の非 null emission（Room の初回読み込み完了）が届くまでは Loading。以後は Room（FavoriteStore.favorites）の変更で更新される。
    private val _uiState = MutableStateFlow<FavoriteUiState>(FavoriteUiState.Loading)
    val uiState: StateFlow<FavoriteUiState> = _uiState.asStateFlow()

    /**
     * 未表示の通知キュー（先頭から順に表示する FIFO）。
     * uiState とは別の StateFlow にしている: 通知はどのフェーズでも発生し得る（一括削除成功と Empty 遷移が同時など）ため。
     * View が表示し終えたら [userMessageShown] で取り除く。
     */
    private val _userMessages = MutableStateFlow<List<UserMessage>>(emptyList())
    val userMessages: StateFlow<List<UserMessage>> = _userMessages.asStateFlow()

    private var nextMessageID = 0L

    /** Undo 猶予タイマー（削除対象の ID ごと）。undoDelete でキャンセルする。 */
    private val pendingDeleteJobs = mutableMapOf<Int, Job>()

    /** お気に入り（Room）の購読ジョブ。[retry] で張り直す。 */
    private var observeJob: Job? = null

    init {
        startObserving()
    }

    /**
     * FavoriteStore.favorites（Room 由来の StateFlow）を購読し、変化のたびに uiState へ反映する。
     * 画面側の load() 呼び出しは不要で、検索画面での追加など他画面からの変更も自動で届く。
     */
    private fun startObserving() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            // StateFlow への .catch は効かない（SharedFlow は完了しない）ため try/catch で受ける。
            try {
                favoriteStore.favorites.filterNotNull().collect { publishFavorites(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { FavoriteUiState.Error(message = e.toDisplayMessage()) }
            }
        }
    }

    /** Error 画面の「再試行」: 購読を張り直す（StateFlow は購読直後に最新値を流す）。 */
    fun retry() {
        _uiState.value = FavoriteUiState.Loading
        startObserving()
    }

    /**
     * プル・トゥ・リフレッシュ。Room が SSOT で常に最新のため再取得は不要で、
     * 現在値を再公開してスピナーを閉じるだけ（UI 挙動は従来どおり）。
     */
    fun refresh() {
        viewModelScope.launch {
            setRefreshing(true)
            favoriteStore.favorites.value?.let { publishFavorites(it) }
            setRefreshing(false)
        }
    }

    private fun Throwable.toDisplayMessage(): String =
        (this as? AppError)?.toDisplayMessage() ?: message ?: "Unknown error"

    /**
     * スワイプ削除のエントリーポイント（Gmail スタイル楽観的更新）。
     *
     * 1. pendingDeleteIDs に追加 → UI から即座に行が消える（displayedFavorites から除外）
     * 2. undoID 付きの UserMessage を積む → SnackBar に「元に戻す」を表示
     * 3. [UNDO_WINDOW_MS] の猶予タイマーを ViewModel のスコープで開始し、経過したら [commitDelete]。
     *    猶予中に ViewModel が破棄される場合（タブ切替で back stack から外れる等）は [onCleared] が残りを即 commit する
     * 4. View で「元に戻す」が押されたら [undoDelete] がタイマーをキャンセルして行を復元
     *
     * Room からの実削除は猶予経過まで遅延される。
     */
    fun removeFavorite(favorite: FavoriteEntity) {
        // UI からは二重削除できない。万一来ても猶予を延長せず無視する。
        if (favorite.id in pendingDeleteJobs) return
        updateLoaded { it.copy(pendingDeleteIDs = it.pendingDeleteIDs + favorite.id) }
        enqueueMessage("「${favorite.fullName}」を削除しました", undoID = favorite.id)
        pendingDeleteJobs[favorite.id] = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            pendingDeleteJobs.remove(favorite.id)
            commitDelete(favorite.id)
        }
    }

    /**
     * 画面を離れて ViewModel が破棄されると Undo の機会も終わるため、猶予中の削除はここで即 commit する。
     * viewModelScope は既にキャンセルされるので、アプリ寿命スコープで実行する。
     */
    override fun onCleared() {
        pendingDeleteJobs.forEach { (id, job) ->
            job.cancel()
            applicationScope.launch { favoriteStore.remove(id) }
        }
        pendingDeleteJobs.clear()
    }

    /** SnackBar の「元に戻す」アクションが押された場合: 猶予タイマーを止め、pending から外して行を復元。 */
    fun undoDelete(id: Int) {
        pendingDeleteJobs.remove(id)?.cancel()
        updateLoaded { it.copy(pendingDeleteIDs = it.pendingDeleteIDs - id) }
        Timber.tag(TAG).i("Favorite delete undone: id=%d", id)
    }

    /** Undo 猶予が経過した場合: Room から実削除。 */
    private suspend fun commitDelete(id: Int) {
        try {
            favoriteStore.remove(id)
            Timber.tag(TAG).i("Favorite delete committed: id=%d", id)
            // pendingDeleteIDs はここでは外さない。Room の emission（行なし）が publishFavorites で
            // 存在しない ID を落とすため、先に外すと emission 到着前に行が一瞬復活してしまう。
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Favorite delete commit failed: id=%d", id)
            // 失敗時は pending から戻して、UI に行を復元
            updateLoaded { it.copy(pendingDeleteIDs = it.pendingDeleteIDs - id) }
            enqueueMessage("お気に入りの削除に失敗しました")
        }
    }

    /** [id] が Undo 待ち（pendingDeleteIDs に含まれる）か。実削除済みの通知を View が表示しないための判定に使う。 */
    fun isDeletePending(id: Int): Boolean =
        (_uiState.value as? FavoriteUiState.Loaded)?.pendingDeleteIDs?.contains(id) == true

    /** View が通知を表示し終えたら呼ぶ。該当 ID の通知をキューから取り除く。 */
    fun userMessageShown(id: Long) {
        _userMessages.update { messages -> messages.filterNot { it.id == id } }
    }

    private fun enqueueMessage(text: String, undoID: Int? = null) {
        _userMessages.update { it + UserMessage(id = nextMessageID++, text = text, undoID = undoID) }
    }

    fun setSortKey(key: FavoriteSortKey) {
        updateLoaded { it.copy(sortKey = key) }
    }

    /** 言語フィルタを設定（null でクリア）。 */
    fun setFilterLanguage(language: String?) {
        updateLoaded { it.copy(filterLanguage = language) }
    }

    fun enterEditMode() {
        updateLoaded { it.copy(isEditMode = true, selectedIDs = emptySet()) }
    }

    fun exitEditMode() {
        updateLoaded {
            it.copy(
                isEditMode = false,
                selectedIDs = emptySet(),
                isDeleteConfirmationShown = false,
            )
        }
    }

    fun toggleSelection(id: Int) {
        updateLoaded { loaded ->
            val newIDs = if (loaded.selectedIDs.contains(id)) {
                loaded.selectedIDs - id
            } else {
                loaded.selectedIDs + id
            }
            loaded.copy(selectedIDs = newIDs)
        }
    }

    fun selectAll() {
        // 表示中（filter 後）の集合のみを選択対象にする。フィルタで非表示のお気に入りは含めない。
        // iOS の selectAll() と挙動を統一。
        updateLoaded { loaded ->
            loaded.copy(selectedIDs = loaded.displayedFavorites.map { it.id }.toSet())
        }
    }

    fun deselectAll() {
        updateLoaded { it.copy(selectedIDs = emptySet()) }
    }

    /**
     * 全選択 ⇔ 全解除のトグル。
     * すべて選択済みなら deselectAll、それ以外なら selectAll を呼ぶ。
     * Passive View 原則に従い、判定ロジックは ViewModel に集約する（View で if を書かない）。
     */
    fun toggleSelectAll() {
        val loaded = _uiState.value as? FavoriteUiState.Loaded ?: return
        if (loaded.isAllSelected) deselectAll() else selectAll()
    }

    fun requestRemoveSelected() {
        updateLoaded { loaded ->
            if (loaded.selectedIDs.isNotEmpty()) {
                loaded.copy(isDeleteConfirmationShown = true)
            } else {
                loaded
            }
        }
    }

    fun cancelRemoveSelected() {
        updateLoaded { it.copy(isDeleteConfirmationShown = false) }
    }

    fun confirmRemoveSelected() {
        val currentLoaded = _uiState.value as? FavoriteUiState.Loaded ?: return
        val idsToRemove = currentLoaded.selectedIDs
        if (idsToRemove.isEmpty()) return

        viewModelScope.launch {
            updateLoaded { it.copy(isDeleteConfirmationShown = false) }
            Timber.tag(TAG).i("Bulk delete started: count=%d", idsToRemove.size)
            try {
                idsToRemove.forEach { id -> favoriteStore.remove(id) }
                Timber.tag(TAG).i("Bulk delete succeeded: count=%d", idsToRemove.size)
                // 一覧本体・Empty 遷移・フィルタ自動クリアは Room の emission（publishFavorites）が担う。
                // 選択だけはここで解除する（猶予中のスワイプ削除の pendingDeleteIDs は保持される）。
                updateLoaded { it.copy(selectedIDs = emptySet()) }
                // 一括削除完了の確認 SnackBar
                enqueueMessage("${idsToRemove.size}件削除しました")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Bulk delete failed")
                enqueueMessage("お気に入りの削除に失敗しました")
            }
        }
    }

    private fun publishFavorites(favorites: List<FavoriteEntity>) {
        _uiState.update { current ->
            if (favorites.isEmpty()) {
                FavoriteUiState.Empty
            } else {
                // 編集モード・ソート/フィルタ・選択状態 + isRefreshing は既存の Loaded を引き継ぐ
                val existingLoaded = current as? FavoriteUiState.Loaded
                val previousFilter = existingLoaded?.filterLanguage
                // 削除等によりフィルタ対象言語が消えた場合は自動クリア
                val nextFilter = if (previousFilter != null && favorites.none { it.language == previousFilter }) {
                    null
                } else {
                    previousFilter
                }
                // pendingDeleteIDs は保持しつつ、favorites に存在しないものはクリア
                // （commit 完了で Room からも消えた = pending 役目を終えた）
                val existingPending = existingLoaded?.pendingDeleteIDs ?: emptySet()
                val visibleIDs = favorites.map { it.id }.toSet()
                val nextPending = existingPending.intersect(visibleIDs)
                FavoriteUiState.Loaded(
                    favorites = favorites,
                    isEditMode = existingLoaded?.isEditMode ?: false,
                    selectedIDs = existingLoaded?.selectedIDs ?: emptySet(),
                    sortKey = existingLoaded?.sortKey ?: FavoriteSortKey.SAVED_AT_DESC,
                    filterLanguage = nextFilter,
                    pendingDeleteIDs = nextPending,
                    // ★ isRefreshing は明示的に既存値を引き継ぐ。
                    //   これがないと publishFavorites の emission で false にリセットされ、
                    //   Material 3 PullToRefreshBox の hide アニメーションが正しくトリガされない。
                    isRefreshing = existingLoaded?.isRefreshing ?: false,
                ).withDerivedFields()
            }
        }
    }

    private fun setRefreshing(isRefreshing: Boolean) {
        updateLoaded { it.copy(isRefreshing = isRefreshing) }
    }

    private fun updateLoaded(transform: (FavoriteUiState.Loaded) -> FavoriteUiState.Loaded) {
        _uiState.update { current ->
            if (current is FavoriteUiState.Loaded) {
                val next = transform(current)
                // 選択・編集モード等、表示リストの入力が変わらない更新では再ソートしない
                if (next.hasSameDisplayInputs(current)) next else next.withDerivedFields()
            } else {
                current
            }
        }
    }

    private fun FavoriteUiState.Loaded.hasSameDisplayInputs(other: FavoriteUiState.Loaded): Boolean =
        favorites == other.favorites &&
            sortKey == other.sortKey &&
            filterLanguage == other.filterLanguage &&
            pendingDeleteIDs == other.pendingDeleteIDs

    /** 入力（favorites / sortKey / filterLanguage / pendingDeleteIDs）から displayedFavorites / availableLanguages を計算して載せる。 */
    private fun FavoriteUiState.Loaded.withDerivedFields(): FavoriteUiState.Loaded {
        val visible = favorites.filter { it.id !in pendingDeleteIDs }
        val filtered = if (filterLanguage != null) {
            visible.filter { it.language == filterLanguage }
        } else {
            visible
        }
        val sorted = when (sortKey) {
            FavoriteSortKey.SAVED_AT_DESC -> filtered.sortedByDescending { it.savedAt }
            FavoriteSortKey.STAR_COUNT_DESC -> filtered.sortedByDescending { it.starCount }
            FavoriteSortKey.FULL_NAME_ASC -> filtered.sortedBy { it.fullName.lowercase() }
        }
        return copy(
            displayedFavorites = sorted,
            availableLanguages = favorites.mapNotNull { it.language }.distinct().sorted(),
        )
    }

    private companion object {
        const val TAG = "Favorite"
    }

}
