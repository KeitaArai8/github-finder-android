/*
 * Abstract:
 * キーワード検索の状態管理を担当する ViewModel。
 *
 * 設計の二段構え:
 *  1. 内部状態 = SearchPhase（フェーズ + データ）+ お気に入り ID 集合 + 検索履歴 Flow
 *  2. 公開 State = combine + stateIn でリアクティブに合成した SearchUiState
 *
 * combine による合成のメリット:
 *  - 検索履歴 / お気に入り ID が更新されたら、現在のフェーズに自動的に反映される
 *  - Loaded → Idle 遷移時に latestHistory を都度参照する手動キャッシュが不要
 *  - 全ての非同期入力源が「単一の純粋な変換関数」に集約され、テストしやすい
 *
 * 一度きりの通知（SnackBar 等）は userMessages（StateFlow<List<UserMessage>>）に積み、
 * View が表示し終えたら userMessageShown(id) で消す（公式ガイドの UI イベント → State 化パターン）。
 */

package com.example.githubfinder.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.githubfinder.core.data.FavoriteStore
import com.example.githubfinder.core.data.datasource.SearchHistoryDataSource
import com.example.githubfinder.core.data.repository.SearchRepository
import com.example.githubfinder.core.model.AppError
import com.example.githubfinder.core.model.RepositoryDisplayItem
import com.example.githubfinder.core.model.RepositoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 検索画面の内部フェーズ。
 * これは ViewModel 内部表現で、UI 層には一切公開しない（最終的に combine で UiState に変換される）。
 */
private sealed interface SearchPhase {
    val query: String

    data class Idle(override val query: String = "") : SearchPhase
    data class Loading(override val query: String) : SearchPhase
    data class Loaded(
        override val query: String,
        val items: List<RepositoryEntity>,
        val isLoadingMore: Boolean = false,
        val isRefreshing: Boolean = false,
    ) : SearchPhase
    data class Empty(override val query: String) : SearchPhase
    data class Error(override val query: String, val message: String) : SearchPhase
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val favoriteStore: FavoriteStore,
    private val searchHistoryDataSource: SearchHistoryDataSource,
) : ViewModel() {

    /** 内部フェーズ。UI 層からは隠蔽される。 */
    private val _phase = MutableStateFlow<SearchPhase>(SearchPhase.Idle())

    /** 公開 UiState: phase × favoriteIDs × history を combine で合成したリアクティブ State。 */
    val uiState: StateFlow<SearchUiState> = combine(
        _phase,
        favoriteStore.favoriteIDs,
        searchHistoryDataSource.history,
    ) { phase, favoriteIDs, history ->
        phase.toUiState(favoriteIDs, history)
    }.stateIn(
        scope = viewModelScope,
        // WhileSubscribed: subscriber 0 になっても 5 秒は state を維持（画面回転耐性）
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState.Idle(),
    )

    /**
     * 未表示の通知キュー（先頭から順に表示する FIFO）。
     * uiState とは別の StateFlow にしている: 通知はどのフェーズでも発生し得るため。
     * View が表示し終えたら [userMessageShown] で取り除く。
     */
    private val _userMessages = MutableStateFlow<List<UserMessage>>(emptyList())
    val userMessages: StateFlow<List<UserMessage>> = _userMessages.asStateFlow()

    private var nextMessageID = 0L

    private var currentPage = 1
    private var hasNextPage = false

    init {
        observeSearchQuery()
        refreshFavoriteIDs()
    }

    fun onQueryChanged(query: String) {
        _phase.update { current ->
            when (current) {
                is SearchPhase.Idle -> current.copy(query = query)
                is SearchPhase.Loading -> current.copy(query = query)
                is SearchPhase.Loaded -> current.copy(query = query)
                is SearchPhase.Empty -> current.copy(query = query)
                is SearchPhase.Error -> current.copy(query = query)
            }
        }
    }

    fun selectHistory(query: String) {
        onQueryChanged(query)
        viewModelScope.launch { performSearch() }
    }

    fun removeHistory(query: String) {
        viewModelScope.launch { searchHistoryDataSource.remove(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { searchHistoryDataSource.clear() }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        _phase
            .map { it.query }
            .distinctUntilChanged()
            .debounce(500)
            .onEach { query ->
                if (query.isBlank()) resetSearch() else performSearch()
            }
            .launchIn(viewModelScope)
    }

    private fun resetSearch() {
        currentPage = 1
        hasNextPage = false
        _phase.update { SearchPhase.Idle(query = it.query) }
    }

    fun onRefresh() {
        val query = _phase.value.query
        if (query.isBlank()) return
        viewModelScope.launch {
            updateLoaded { it.copy(isRefreshing = true) }
            try {
                currentPage = 1
                performSearch()
            } finally {
                updateLoaded { it.copy(isRefreshing = false) }
            }
        }
    }

    fun onLoadMore() {
        val loaded = _phase.value as? SearchPhase.Loaded ?: return
        if (!hasNextPage || loaded.isLoadingMore) return
        viewModelScope.launch { loadNextPage() }
    }

    fun toggleFavorite(item: RepositoryDisplayItem) {
        viewModelScope.launch {
            try {
                val loaded = _phase.value as? SearchPhase.Loaded ?: return@launch
                val entity = loaded.items.find { it.id == item.id } ?: return@launch
                if (favoriteStore.favoriteIDs.value.contains(item.id)) {
                    favoriteStore.remove(item.id)
                } else {
                    favoriteStore.add(entity)
                }
                // favoriteStore.favoriteIDs は StateFlow で combine に流れているため、
                // ここで明示的に UI を更新する必要なし（リアクティブに自動反映）
            } catch (_: Exception) {
                enqueueMessage("お気に入りの更新に失敗しました")
            }
        }
    }

    /** View が通知を表示し終えたら呼ぶ。該当 ID の通知をキューから取り除く。 */
    fun userMessageShown(id: Long) {
        _userMessages.update { messages -> messages.filterNot { it.id == id } }
    }

    private fun enqueueMessage(text: String) {
        _userMessages.update { it + UserMessage(id = nextMessageID++, text = text) }
    }

    fun refreshFavoriteIDs() {
        viewModelScope.launch {
            try {
                favoriteStore.refresh()
            } catch (_: Exception) { }
        }
    }

    private suspend fun performSearch() {
        val query = _phase.value.query
        currentPage = 1
        _phase.value = SearchPhase.Loading(query = query)

        Timber.tag(TAG).i("Search started: query=%s, page=1", query)
        try {
            val result = searchRepository.search(query, 1)
            hasNextPage = result.hasNextPage
            Timber.tag(TAG).i(
                "Search succeeded: count=%d, totalCount=%d, hasNextPage=%s",
                result.items.size, result.totalCount, result.hasNextPage,
            )
            // 結果が 0 件でもユーザの意図的な検索なので履歴に追加する
            searchHistoryDataSource.add(query)
            _phase.value = if (result.items.isEmpty()) {
                SearchPhase.Empty(query = query)
            } else {
                SearchPhase.Loaded(query = query, items = result.items)
            }
        } catch (e: AppError) {
            Timber.tag(TAG).w(e, "Search failed (AppError)")
            _phase.value = SearchPhase.Error(query = query, message = e.toDisplayMessage())
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Search failed (unknown)")
            _phase.value = SearchPhase.Error(query = query, message = e.message ?: "Unknown error")
        }
    }

    private suspend fun loadNextPage() {
        updateLoaded { it.copy(isLoadingMore = true) }
        try {
            val nextPage = currentPage + 1
            val result = searchRepository.search(_phase.value.query, nextPage)
            currentPage = nextPage
            hasNextPage = result.hasNextPage
            updateLoaded { it.copy(items = it.items + result.items, isLoadingMore = false) }
        } catch (_: Exception) {
            enqueueMessage("追加読み込みに失敗しました")
            updateLoaded { it.copy(isLoadingMore = false) }
        }
    }

    private fun updateLoaded(transform: (SearchPhase.Loaded) -> SearchPhase.Loaded) {
        _phase.update { current ->
            if (current is SearchPhase.Loaded) transform(current) else current
        }
    }

    /** Phase + favoriteIDs + history → 公開 UiState への純粋な変換。 */
    private fun SearchPhase.toUiState(
        favoriteIDs: Set<Int>,
        history: List<String>,
    ): SearchUiState = when (this) {
        is SearchPhase.Idle -> SearchUiState.Idle(searchQuery = query, history = history)
        is SearchPhase.Loading -> SearchUiState.Loading(searchQuery = query)
        is SearchPhase.Loaded -> SearchUiState.Loaded(
            searchQuery = query,
            items = items.map { repo -> RepositoryDisplayItem.from(repo, favoriteIDs.contains(repo.id)) },
            isLoadingMore = isLoadingMore,
            isRefreshing = isRefreshing,
        )
        is SearchPhase.Empty -> SearchUiState.Empty(searchQuery = query)
        is SearchPhase.Error -> SearchUiState.Error(searchQuery = query, message = message)
    }

    private companion object {
        const val TAG = "Search"
    }

}
