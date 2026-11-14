/*
 * Abstract:
 * キーワード検索の状態管理を担当する ViewModel。
 *
 * 設計の二段構え:
 *  1. 内部状態 = SearchPhase（フェーズ + データ）+ 検索クエリ + お気に入り ID 集合 + 検索履歴 Flow
 *  2. 公開 State = combine + stateIn でリアクティブに合成した SearchUiState
 *
 * combine による合成のメリット:
 *  - 検索履歴 / お気に入り ID が更新されたら、現在のフェーズに自動的に反映される
 *  - Loaded → Idle 遷移時に latestHistory を都度参照する手動キャッシュが不要
 *  - 全ての非同期入力源が「単一の純粋な変換関数」に集約され、テストしやすい
 *
 * 検索クエリは SavedStateHandle に持つ（プロセス死後の復元で入力中の文字列を失わないため）。
 * 復元されたクエリが空でなければ、debounce 後に通常の入力時と同じ経路で再検索される。
 * ページング位置（currentPage / hasNextPage）は結果と一体の値なので Loaded フェーズに載せる。
 *
 * 一度きりの通知（SnackBar 等）は userMessages（StateFlow<List<UserMessage>>）に積み、
 * View が表示し終えたら userMessageShown(id) で消す（公式ガイドの UI イベント → State 化パターン）。
 */

package com.example.githubfinder.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.githubfinder.core.data.FavoriteStore
import com.example.githubfinder.core.data.datasource.SearchHistoryDataSource
import com.example.githubfinder.core.data.repository.SearchRepository
import com.example.githubfinder.core.designsystem.UiText
import com.example.githubfinder.core.designsystem.toUiText
import com.example.githubfinder.core.model.AppError
import com.example.githubfinder.core.model.RepositoryDisplayItem
import com.example.githubfinder.core.model.RepositoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 検索画面の内部フェーズ。
 * これは ViewModel 内部表現で、UI 層には一切公開しない（最終的に combine で UiState に変換される）。
 */
private sealed interface SearchPhase {
    data object Idle : SearchPhase
    data object Loading : SearchPhase
    data class Loaded(
        val items: List<RepositoryEntity>,
        /** 取得済みの最後のページ番号（1 始まり）。 */
        val currentPage: Int = 1,
        val hasNextPage: Boolean = false,
        val isLoadingMore: Boolean = false,
        val isRefreshing: Boolean = false,
    ) : SearchPhase
    data object Empty : SearchPhase
    data class Error(val message: UiText) : SearchPhase
}

/**
 * 検索の実行要求。
 * [isImmediate] が true の要求（履歴選択・プル・トゥ・リフレッシュ）は debounce を通さず、同じクエリの重複判定も受けない。
 */
private data class SearchRequest(
    val query: String,
    val isImmediate: Boolean = false,
    val isRefresh: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val favoriteStore: FavoriteStore,
    private val searchHistoryDataSource: SearchHistoryDataSource,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** 内部フェーズ。UI 層からは隠蔽される。 */
    private val _phase = MutableStateFlow<SearchPhase>(SearchPhase.Idle)

    /** 検索クエリ。SavedStateHandle 由来なのでプロセス死後も復元される。 */
    private val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    /** 公開 UiState: phase × query × favoriteIDs × history を combine で合成したリアクティブ State。 */
    val uiState: StateFlow<SearchUiState> = combine(
        _phase,
        query,
        favoriteStore.favoriteIDs,
        searchHistoryDataSource.history,
    ) { phase, query, favoriteIDs, history ->
        phase.toUiState(query, favoriteIDs, history)
    }.stateIn(
        scope = viewModelScope,
        // WhileSubscribed: subscriber 0 になっても 5 秒は state を維持（画面回転耐性）
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState.Idle(searchQuery = query.value),
    )

    /**
     * 未表示の通知キュー（先頭から順に表示する FIFO）。
     * uiState とは別の StateFlow にしている: 通知はどのフェーズでも発生し得るため。
     * View が表示し終えたら [userMessageShown] で取り除く。
     */
    private val _userMessages = MutableStateFlow<List<UserMessage>>(emptyList())
    val userMessages: StateFlow<List<UserMessage>> = _userMessages.asStateFlow()

    private var nextMessageID = 0L

    /**
     * debounce を通さず今すぐ検索したい要求の入口（履歴選択・リフレッシュ）。
     * 検索の実行は [observeSearchQuery] のパイプライン 1 本に集約し、ここからは直接 performSearch を呼ばない
     * （直接呼ぶと同じクエリの debounce 検索と二重実行され、リクエスト・履歴追加が 2 回になるため）。
     */
    private val immediateSearchRequests = MutableSharedFlow<SearchRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 直近に検索を要求したクエリ（空白クエリは null）。履歴選択で検索済みのクエリを、後から届く debounce 検索が重複して実行しないために使う。 */
    private var lastRequestedQuery: String? = null

    init {
        observeSearchQuery()
    }

    fun onQueryChanged(query: String) {
        savedStateHandle[KEY_QUERY] = query
    }

    fun selectHistory(query: String) {
        onQueryChanged(query)
        immediateSearchRequests.tryEmit(SearchRequest(query, isImmediate = true))
    }

    fun removeHistory(query: String) {
        viewModelScope.launch { searchHistoryDataSource.remove(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { searchHistoryDataSource.clear() }
    }

    /**
     * 検索の実行を 1 本のパイプラインに集約する。
     * 入力（クエリ変更）は debounce して、履歴選択・リフレッシュは即時に、同じ流れへ合流させる。
     * mapLatest により、前の検索が通信中でも新しい要求が確定した時点でその検索をキャンセルする
     * （遅い古いリクエストが新しい結果を待たせたり後から上書きしたりしない）。
     * 履歴選択で検索済みのクエリは、あとから debounce を通って届いても再検索しない
     * （キャンセルが走らないよう、重複排除は mapLatest の手前で行う）。
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeSearchQuery() {
        merge(
            query.debounce(500).distinctUntilChanged().map { SearchRequest(it) },
            immediateSearchRequests,
        )
            .filter { it.isImmediate || it.query != lastRequestedQuery }
            .onEach { lastRequestedQuery = it.query.takeIf { query -> query.isNotBlank() } }
            .mapLatest { request ->
                when {
                    request.query.isBlank() -> resetSearch()
                    request.isRefresh -> refreshSearch(request.query)
                    else -> performSearch(request.query)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun resetSearch() {
        _phase.value = SearchPhase.Idle
    }

    /** リフレッシュ要求の検索。完了（またはキャンセル）時に必ず isRefreshing を戻す。 */
    private suspend fun refreshSearch(query: String) {
        updateLoaded { it.copy(isRefreshing = true) }
        try {
            performSearch(query)
        } finally {
            updateLoaded { it.copy(isRefreshing = false) }
        }
    }

    fun onRefresh() {
        val currentQuery = query.value
        if (currentQuery.isBlank()) return
        immediateSearchRequests.tryEmit(SearchRequest(currentQuery, isImmediate = true, isRefresh = true))
    }

    fun onLoadMore() {
        val loaded = _phase.value as? SearchPhase.Loaded ?: return
        if (!loaded.hasNextPage || loaded.isLoadingMore) return
        viewModelScope.launch { loadNextPage(loaded.currentPage) }
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                enqueueMessage(UiText.Resource(R.string.search_error_favorite_update))
            }
        }
    }

    /** View が通知を表示し終えたら呼ぶ。該当 ID の通知をキューから取り除く。 */
    fun userMessageShown(id: Long) {
        _userMessages.update { messages -> messages.filterNot { it.id == id } }
    }

    private fun enqueueMessage(text: UiText) {
        _userMessages.update { it + UserMessage(id = nextMessageID++, text = text) }
    }

    private suspend fun performSearch(currentQuery: String) {
        _phase.value = SearchPhase.Loading

        Timber.tag(TAG).i("Search started: query=%s, page=1", currentQuery)
        try {
            val result = searchRepository.search(currentQuery, 1)
            Timber.tag(TAG).i(
                "Search succeeded: count=%d, totalCount=%d, hasNextPage=%s",
                result.items.size,
                result.totalCount,
                result.hasNextPage,
            )
            // 結果が 0 件でもユーザの意図的な検索なので履歴に追加する
            searchHistoryDataSource.add(currentQuery)
            _phase.value = if (result.items.isEmpty()) {
                SearchPhase.Empty
            } else {
                SearchPhase.Loaded(items = result.items, currentPage = 1, hasNextPage = result.hasNextPage)
            }
        } catch (e: AppError) {
            Timber.tag(TAG).w(e, "Search failed (AppError)")
            _phase.value = SearchPhase.Error(message = e.toUiText())
        } catch (e: CancellationException) {
            // キャンセルは失敗ではない。Error 状態にせず呼び出し元へ伝播させる
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Search failed (unknown)")
            _phase.value = SearchPhase.Error(message = e.toUiText())
        }
    }

    private suspend fun loadNextPage(currentPage: Int) {
        // 通信中にクエリが変わったら、古いクエリの 2 ページ目を新しい結果へ混ぜない（取得開始時のクエリと照合する）
        val requestedQuery = query.value
        updateLoaded { it.copy(isLoadingMore = true) }
        try {
            val nextPage = currentPage + 1
            val result = searchRepository.search(requestedQuery, nextPage)
            if (query.value != requestedQuery) {
                updateLoaded { it.copy(isLoadingMore = false) }
                return
            }
            updateLoaded {
                it.copy(
                    items = it.items + result.items,
                    currentPage = nextPage,
                    hasNextPage = result.hasNextPage,
                    isLoadingMore = false,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 古いクエリのページ取得の失敗は、今の検索結果に無関係なので通知しない
            if (query.value == requestedQuery) enqueueMessage(UiText.Resource(R.string.search_error_load_more))
            updateLoaded { it.copy(isLoadingMore = false) }
        }
    }

    private fun updateLoaded(transform: (SearchPhase.Loaded) -> SearchPhase.Loaded) {
        _phase.update { current ->
            if (current is SearchPhase.Loaded) transform(current) else current
        }
    }

    /** Phase + query + favoriteIDs + history → 公開 UiState への純粋な変換。 */
    private fun SearchPhase.toUiState(
        query: String,
        favoriteIDs: Set<Int>,
        history: List<String>,
    ): SearchUiState = when (this) {
        SearchPhase.Idle -> SearchUiState.Idle(searchQuery = query, history = history)

        SearchPhase.Loading -> SearchUiState.Loading(searchQuery = query)

        is SearchPhase.Loaded -> SearchUiState.Loaded(
            searchQuery = query,
            items = items.map { repo -> RepositoryDisplayItem.from(repo, favoriteIDs.contains(repo.id)) },
            isLoadingMore = isLoadingMore,
            isRefreshing = isRefreshing,
        )

        SearchPhase.Empty -> SearchUiState.Empty(searchQuery = query)

        is SearchPhase.Error -> SearchUiState.Error(searchQuery = query, message = message)
    }

    private companion object {
        const val TAG = "Search"

        /** SavedStateHandle 上の検索クエリのキー。 */
        const val KEY_QUERY = "query"
    }
}
