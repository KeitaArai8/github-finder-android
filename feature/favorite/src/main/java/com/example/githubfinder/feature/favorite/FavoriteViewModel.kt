/*
 * Abstract:
 * お気に入り一覧の状態管理を担当するViewModel。UDF単一状態原則に従い、
 * FavoriteUiState 1つに全状態（フェーズ・リフレッシュ・編集モード・選択）を集約して公開する。
 *
 * お気に入りの持ち主は FavoriteStore（Room）なので、ここには一覧のコピーを保存しない。
 * ViewModel が持つのは画面自身の操作状態（OwnedState）だけで、
 * 公開 uiState は FavoriteStore.favorites × OwnedState を combine + stateIn で導出する（SearchViewModel と同じ形）。
 *
 * 一度きりの通知（SnackBar 表示等）は userMessages（StateFlow<List<FavoriteUserMessage>>）に積み、
 * View が表示し終えたら userMessageShown(id) で消す（公式ガイドの UI イベント → State 化パターン）。
 * 削除の Undo 猶予タイマーと実削除の commit も ViewModel が持つ（View の寿命に依存させない）。
 * ViewModel が破棄される時は、猶予中の削除を onCleared でアプリ寿命スコープから即 commit する。
 */

package com.example.githubfinder.feature.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.githubfinder.core.common.network.di.ApplicationScope
import com.example.githubfinder.core.data.repository.FavoriteStore
import com.example.githubfinder.core.model.Favorite
import com.example.githubfinder.core.ui.UiText
import com.example.githubfinder.core.ui.UserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** Undo 猶予時間（ミリ秒）。Material の SnackbarDuration.Short（約 4 秒）に合わせ、経過後に Room から実削除する。 */
internal const val UNDO_WINDOW_MS = 4_000L

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val favoriteStore: FavoriteStore,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    /** この画面自身が持つ操作状態。お気に入り一覧そのものは持たない（持ち主は FavoriteStore）。 */
    private val _owned = MutableStateFlow(OwnedState())

    /**
     * 公開 UiState: favorites × owned を combine で合成したリアクティブ State。
     * 最初の非 null emission（Room の初回読み込み完了）が届くまでは Loading。
     */
    val uiState: StateFlow<FavoriteUiState> = combine(
        favoriteStore.favorites,
        _owned,
    ) { favorites, owned ->
        deriveUiState(favorites, owned)
    }.stateIn(
        scope = viewModelScope,
        // WhileSubscribed: subscriber 0 になっても 5 秒は state を維持（画面回転耐性）
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FavoriteUiState.Loading,
    )

    /**
     * 未表示の通知キュー（先頭から順に表示する FIFO）。
     * uiState とは別の StateFlow にしている: 通知はどのフェーズでも発生し得る（一括削除成功と Empty 遷移が同時など）ため。
     * View が表示し終えたら [userMessageShown] で取り除く。
     */
    private val _userMessages = MutableStateFlow<List<FavoriteUserMessage>>(emptyList())
    val userMessages: StateFlow<List<FavoriteUserMessage>> = _userMessages.asStateFlow()

    private var nextMessageID = 0L

    /** Undo 猶予タイマー（削除対象の ID ごと）。undoDelete でキャンセルする。 */
    private val pendingDeleteJobs = mutableMapOf<Int, Job>()

    init {
        // 一覧の変化に合わせて owned を整える（一覧自体はコピーしない）。uiState の購読者に依存しないよう init で開始する。
        // FavoriteStore.favorites は例外を流さない（失敗時は store 側で空リストに倒す）ため、ここでは catch しない。
        // 画面側の load() 呼び出しは不要で、検索画面での追加など他画面からの変更も自動で届く。
        // 既知の制限: uiState とこの収集は同じ StateFlow を別々に購読するため、空の値を観測できないまま非空に戻ると（conflation）操作状態はリセットされない。
        viewModelScope.launch {
            favoriteStore.favorites.filterNotNull().collect { syncOwned(it) }
        }
    }

    /**
     * プル・トゥ・リフレッシュ。Room が SSOT で常に最新のため再取得は不要で、
     * スピナーを開いて閉じるだけ。
     */
    fun refresh() {
        viewModelScope.launch {
            setRefreshing(true)
            setRefreshing(false)
        }
    }

    /**
     * スワイプ削除のエントリーポイント（Gmail スタイル楽観的更新）。
     *
     * 1. pendingDeleteIDs に追加 → UI から即座に行が消える（displayedFavorites から除外）
     * 2. undoID 付きの FavoriteUserMessage を積む → SnackBar に「元に戻す」を表示
     * 3. [UNDO_WINDOW_MS] の猶予タイマーを ViewModel のスコープで開始し、経過したら [commitDelete]。
     *    猶予中に ViewModel が破棄される場合（タブ切替で back stack から外れる等）は [onCleared] が残りを即 commit する
     * 4. View で「元に戻す」が押されたら [undoDelete] がタイマーをキャンセルして行を復元
     *
     * Room からの実削除は猶予経過まで遅延される。
     */
    fun removeFavorite(favorite: Favorite) {
        // UI からは二重削除できない。万一来ても猶予を延長せず無視する。
        if (favorite.id in pendingDeleteJobs) return
        updateOwned { it.copy(pendingDeleteIDs = it.pendingDeleteIDs + favorite.id) }
        enqueueMessage(UiText.Resource(R.string.favorite_deleted_with_name, listOf(favorite.fullName)), undoID = favorite.id)
        pendingDeleteJobs[favorite.id] = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            pendingDeleteJobs.remove(favorite.id)
            // ViewModel が破棄されても Room の実削除が中断されないよう、アプリ寿命スコープで実行する
            applicationScope.launch { commitDelete(favorite.id) }
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
        updateOwned { it.copy(pendingDeleteIDs = it.pendingDeleteIDs - id) }
        Timber.tag(TAG).i("Favorite delete undone: id=%d", id)
    }

    /** Undo 猶予が経過した場合: Room から実削除。 */
    private suspend fun commitDelete(id: Int) {
        try {
            favoriteStore.remove(id)
            Timber.tag(TAG).i("Favorite delete committed: id=%d", id)
            // pendingDeleteIDs はここでは外さない。Room の emission（行なし）で syncOwned が
            // 存在しない ID を落とすため、先に外すと emission 到着前に行が一瞬復活してしまう。
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Favorite delete commit failed: id=%d", id)
            // 失敗時は pending から戻して、UI に行を復元
            updateOwned { it.copy(pendingDeleteIDs = it.pendingDeleteIDs - id) }
            enqueueMessage(UiText.Resource(R.string.favorite_error_delete))
        }
    }

    /** View が通知を表示し終えたら呼ぶ。該当 ID の通知をキューから取り除く。 */
    fun userMessageShown(id: Long) {
        _userMessages.update { messages -> messages.filterNot { it.message.id == id } }
    }

    private fun enqueueMessage(text: UiText, undoID: Int? = null) {
        _userMessages.update { it + FavoriteUserMessage(UserMessage(id = nextMessageID++, text = text), undoID) }
    }

    fun setSortKey(key: FavoriteSortKey) {
        updateOwned { it.copy(sortKey = key) }
    }

    /** 言語フィルタを設定（null でクリア）。 */
    fun setFilterLanguage(language: String?) {
        updateOwned { it.copy(filterLanguage = language) }
    }

    fun enterEditMode() {
        updateOwned { it.copy(isEditMode = true, selectedIDs = emptySet()) }
    }

    fun exitEditMode() {
        updateOwned {
            it.copy(
                isEditMode = false,
                selectedIDs = emptySet(),
                isDeleteConfirmationShown = false,
            )
        }
    }

    fun toggleSelection(id: Int) {
        updateOwned { owned ->
            val newIDs = if (owned.selectedIDs.contains(id)) {
                owned.selectedIDs - id
            } else {
                owned.selectedIDs + id
            }
            owned.copy(selectedIDs = newIDs)
        }
    }

    fun selectAll() {
        // 表示中（filter 後）の集合のみを選択対象にする。フィルタで非表示のお気に入りは含めない。
        val loaded = currentLoaded() ?: return
        updateOwned { it.copy(selectedIDs = loaded.displayedFavorites.map { favorite -> favorite.id }.toSet()) }
    }

    fun deselectAll() {
        updateOwned { it.copy(selectedIDs = emptySet()) }
    }

    /**
     * 全選択 ⇔ 全解除のトグル。
     * すべて選択済みなら deselectAll、それ以外なら selectAll を呼ぶ。
     * Passive View 原則に従い、判定ロジックは ViewModel に集約する（View で if を書かない）。
     */
    fun toggleSelectAll() {
        val loaded = currentLoaded() ?: return
        if (loaded.isAllSelected) deselectAll() else selectAll()
    }

    fun requestRemoveSelected() {
        updateOwned { owned ->
            if (owned.selectedIDs.isNotEmpty()) {
                owned.copy(isDeleteConfirmationShown = true)
            } else {
                owned
            }
        }
    }

    fun cancelRemoveSelected() {
        updateOwned { it.copy(isDeleteConfirmationShown = false) }
    }

    fun confirmRemoveSelected() {
        val idsToRemove = currentLoaded()?.selectedIDs ?: return
        if (idsToRemove.isEmpty()) return

        viewModelScope.launch {
            updateOwned { it.copy(isDeleteConfirmationShown = false) }
            Timber.tag(TAG).i("Bulk delete started: count=%d", idsToRemove.size)
            try {
                idsToRemove.forEach { id -> favoriteStore.remove(id) }
                Timber.tag(TAG).i("Bulk delete succeeded: count=%d", idsToRemove.size)
                // 一覧本体・Empty 遷移・フィルタ自動クリアは Room の emission（uiState の導出と syncOwned）が担う。
                // 選択だけはここで解除する（猶予中のスワイプ削除の pendingDeleteIDs は保持される）。
                updateOwned { it.copy(selectedIDs = emptySet()) }
                // 一括削除完了の確認 SnackBar
                enqueueMessage(UiText.Resource(R.string.favorite_bulk_deleted, listOf(idsToRemove.size)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Bulk delete failed")
                enqueueMessage(UiText.Resource(R.string.favorite_error_delete))
            }
        }
    }

    /** 現在の入力（favorites + owned）から Loaded を導出する。ハンドラが uiState の購読有無に依存しないための入口。 */
    private fun currentLoaded(): FavoriteUiState.Loaded? = deriveUiState(favoriteStore.favorites.value, _owned.value) as? FavoriteUiState.Loaded

    private fun setRefreshing(isRefreshing: Boolean) {
        updateOwned { it.copy(isRefreshing = isRefreshing) }
    }

    /** Loaded（お気に入りが 1 件以上）の間だけ操作状態を更新する。Loading / Empty 中の操作は無視する。 */
    private fun updateOwned(transform: (OwnedState) -> OwnedState) {
        if (favoriteStore.favorites.value.isNullOrEmpty()) return
        _owned.update(transform)
    }

    /**
     * 一覧が変わったときに、一覧に依存する owned の値を整える。
     * - 空になったら編集モード・選択・ソート・フィルタ等をすべて捨てる（Empty は前の Loaded を引き継がない）
     * - 実削除などで一覧から消えた pendingDeleteIDs を外す（同じ ID が後で再追加されても隠さない）
     * - 削除等でフィルタ対象の言語が消えたら自動クリアする（言語が再び現れても復活させない）
     *
     * 既知の制限: uiState とこの収集は同じ StateFlow を別々に購読している。お気に入りが空 → 非空に戻るまでの間に
     * この収集が空の値を観測できなかった場合（StateFlow の conflation）、画面の操作状態はリセットされない。
     */
    private fun syncOwned(favorites: List<Favorite>) {
        if (favorites.isEmpty()) {
            _owned.value = OwnedState()
            return
        }
        _owned.update { it.reconciledWith(favorites) }
    }

    private companion object {
        const val TAG = "Favorite"
    }
}

/** ViewModel が持つ操作状態。お気に入り一覧（持ち主は FavoriteStore）は含めない。 */
private data class OwnedState(
    val isRefreshing: Boolean = false,
    val isEditMode: Boolean = false,
    val selectedIDs: Set<Int> = emptySet(),
    val isDeleteConfirmationShown: Boolean = false,
    val sortKey: FavoriteSortKey = FavoriteSortKey.SAVED_AT_DESC,
    val filterLanguage: String? = null,
    val pendingDeleteIDs: Set<Int> = emptySet(),
)

/** 一覧から消えた ID / 言語を owned から落とす。syncOwned（書き込み側）と deriveUiState（読み取り側）で共有する。 */
private fun OwnedState.reconciledWith(favorites: List<Favorite>): OwnedState {
    val visibleIDs = favorites.map { it.id }.toSet()
    return copy(
        pendingDeleteIDs = pendingDeleteIDs.intersect(visibleIDs),
        filterLanguage = filterLanguage?.takeIf { language -> favorites.any { it.language == language } },
    )
}

/**
 * favorites（null = 読み込み中）と owned から FavoriteUiState を導出する純粋関数。
 * combine と、現在値を必要とするイベントハンドラ（currentLoaded）が同じ関数を通す。
 * displayedFavorites / availableLanguages もここで計算する。
 */
private fun deriveUiState(favorites: List<Favorite>?, owned: OwnedState): FavoriteUiState {
    if (favorites == null) return FavoriteUiState.Loading
    if (favorites.isEmpty()) return FavoriteUiState.Empty

    // combine は init の収集（syncOwned）が _owned を書く前に走り得るため、読む側でも同じ整合を取る
    val reconciled = owned.reconciledWith(favorites)
    val pendingDeleteIDs = reconciled.pendingDeleteIDs
    val filterLanguage = reconciled.filterLanguage

    val visible = favorites.filter { it.id !in pendingDeleteIDs }
    val filtered = if (filterLanguage != null) {
        visible.filter { it.language == filterLanguage }
    } else {
        visible
    }
    val sorted = when (owned.sortKey) {
        FavoriteSortKey.SAVED_AT_DESC -> filtered.sortedByDescending { it.savedAt }
        FavoriteSortKey.STAR_COUNT_DESC -> filtered.sortedByDescending { it.starCount }
        FavoriteSortKey.FULL_NAME_ASC -> filtered.sortedBy { it.fullName.lowercase() }
    }
    return FavoriteUiState.Loaded(
        favorites = favorites,
        isRefreshing = owned.isRefreshing,
        isEditMode = owned.isEditMode,
        selectedIDs = owned.selectedIDs,
        isDeleteConfirmationShown = owned.isDeleteConfirmationShown,
        sortKey = owned.sortKey,
        filterLanguage = filterLanguage,
        pendingDeleteIDs = pendingDeleteIDs,
        displayedFavorites = sorted,
        availableLanguages = favorites.mapNotNull { it.language }.distinct().sorted(),
    )
}
