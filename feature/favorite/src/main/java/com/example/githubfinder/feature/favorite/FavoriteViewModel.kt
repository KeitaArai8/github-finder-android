/*
 * Abstract:
 * お気に入り一覧の状態管理を担当するViewModel。UDF単一状態原則に従い、
 * FavoriteUiState 1つに全状態（フェーズ・リフレッシュ・編集モード・選択）を集約する。
 *
 * 一度きりのイベント（SnackBar 表示等）は Channel<FavoriteUiEvent> 経由で発火し、
 * View で receiveAsFlow().collect で消化する（State には保持しない）。
 */

package com.example.githubfinder.feature.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.githubfinder.core.data.FavoriteStore
import com.example.githubfinder.core.model.AppError
import com.example.githubfinder.core.model.FavoriteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val favoriteStore: FavoriteStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FavoriteUiState>(FavoriteUiState.Idle)
    val uiState: StateFlow<FavoriteUiState> = _uiState.asStateFlow()

    /**
     * 一度きりイベント（SnackBar 表示等）。
     * Channel.BUFFERED は subscriber 不在時もイベントを溜め、過剰時は未消化分を保持する。
     */
    private val _events = Channel<FavoriteUiEvent>(Channel.BUFFERED)
    val events: Flow<FavoriteUiEvent> = _events.receiveAsFlow()

    fun load() {
        viewModelScope.launch {
            val isInitialLoad = _uiState.value is FavoriteUiState.Idle
            if (isInitialLoad) {
                _uiState.value = FavoriteUiState.Loading
            } else {
                setRefreshing(true)
            }
            try {
                favoriteStore.refresh()
                publishFavorites(favoriteStore.favorites.value)
            } catch (e: AppError) {
                _uiState.value = FavoriteUiState.Error(message = e.toDisplayMessage())
            } catch (e: Exception) {
                _uiState.value = FavoriteUiState.Error(message = e.message ?: "Unknown error")
            } finally {
                setRefreshing(false)
            }
        }
    }

    /**
     * スワイプ削除のエントリーポイント（Gmail スタイル楽観的更新）。
     *
     * 1. pendingDeleteIDs に追加 → UI から即座に行が消える（displayedFavorites でフィルタ）
     * 2. ShowUndoDelete を発火 → SnackBar に「元に戻す」を表示
     * 3. View で SnackbarResult.ActionPerformed → [undoDelete]、Dismissed → [commitDelete]
     *
     * Room からの実削除は [commitDelete] まで遅延される。
     */
    fun removeFavorite(favorite: FavoriteEntity) {
        updateLoaded { it.copy(pendingDeleteIDs = it.pendingDeleteIDs + favorite.id) }
        viewModelScope.launch {
            _events.send(
                FavoriteUiEvent.ShowUndoDelete(
                    message = "「${favorite.fullName}」を削除しました",
                    deletedID = favorite.id,
                ),
            )
        }
    }

    /** SnackBar の「元に戻す」アクションが押された場合: pending から外して行を復元。 */
    fun undoDelete(id: Int) {
        updateLoaded { it.copy(pendingDeleteIDs = it.pendingDeleteIDs - id) }
        Timber.tag(TAG).i("Favorite delete undone: id=%d", id)
    }

    /** SnackBar が timeout / 別 SnackBar で置換された場合: Room から実削除。 */
    fun commitDelete(id: Int) {
        viewModelScope.launch {
            try {
                favoriteStore.remove(id)
                Timber.tag(TAG).i("Favorite delete committed: id=%d", id)
                // pending から外しつつ、最新 favorites を反映。
                // 行は既に UI から消えているため、ここで flash は起きない。
                _uiState.update { current ->
                    if (current is FavoriteUiState.Loaded) {
                        current.copy(pendingDeleteIDs = current.pendingDeleteIDs - id)
                    } else {
                        current
                    }
                }
                publishFavorites(favoriteStore.favorites.value)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Favorite delete commit failed: id=%d", id)
                // 失敗時は pending から戻して、UI に行を復元
                updateLoaded { it.copy(pendingDeleteIDs = it.pendingDeleteIDs - id) }
                _events.send(FavoriteUiEvent.ShowSnackBar("お気に入りの削除に失敗しました"))
            }
        }
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
                val favorites = favoriteStore.favorites.value
                _uiState.update { current ->
                    if (favorites.isEmpty()) {
                        // 全件削除されたら編集モードも自動終了（Empty 状態へ）
                        FavoriteUiState.Empty
                    } else {
                        val existingLoaded = current as? FavoriteUiState.Loaded
                        val previousFilter = existingLoaded?.filterLanguage
                        val nextFilter = if (previousFilter != null && favorites.none { it.language == previousFilter }) {
                            null
                        } else {
                            previousFilter
                        }
                        FavoriteUiState.Loaded(
                            favorites = favorites,
                            isEditMode = existingLoaded?.isEditMode ?: false,
                            selectedIDs = emptySet(),
                            sortKey = existingLoaded?.sortKey ?: FavoriteSortKey.SAVED_AT_DESC,
                            filterLanguage = nextFilter,
                        )
                    }
                }
                // 一括削除完了の確認 SnackBar
                _events.send(FavoriteUiEvent.ShowSnackBar("${idsToRemove.size}件削除しました"))
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Bulk delete failed")
                _events.send(FavoriteUiEvent.ShowSnackBar("お気に入りの削除に失敗しました"))
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
                )
            }
        }
    }

    private fun setRefreshing(isRefreshing: Boolean) {
        updateLoaded { it.copy(isRefreshing = isRefreshing) }
    }

    private fun updateLoaded(transform: (FavoriteUiState.Loaded) -> FavoriteUiState.Loaded) {
        _uiState.update { current ->
            if (current is FavoriteUiState.Loaded) transform(current) else current
        }
    }

    private companion object {
        const val TAG = "Favorite"
    }

}
