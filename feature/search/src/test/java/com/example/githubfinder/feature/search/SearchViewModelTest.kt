package com.example.githubfinder.feature.search

import com.example.githubfinder.core.testing.MainDispatcherRule
import com.example.githubfinder.core.testing.FakeFavoriteRepository
import com.example.githubfinder.core.testing.FakeSearchRepository
import com.example.githubfinder.core.testing.FakeSearchHistoryDataSource
import androidx.lifecycle.SavedStateHandle
import com.example.githubfinder.core.data.FavoriteStore
import com.example.githubfinder.core.designsystem.R as DesignR
import com.example.githubfinder.core.designsystem.UiText
import com.example.githubfinder.core.model.AppError
import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity
import com.example.githubfinder.core.model.SearchResultEntity
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var fakeSearchRepository: FakeSearchRepository
    private lateinit var fakeFavoriteRepository: FakeFavoriteRepository
    private lateinit var favoriteStore: FavoriteStore
    private lateinit var applicationScope: CoroutineScope
    private lateinit var fakeSearchHistoryDataSource: FakeSearchHistoryDataSource

    @Before
    fun setup() {
        fakeSearchRepository = FakeSearchRepository()
        fakeFavoriteRepository = FakeFavoriteRepository()
        applicationScope = CoroutineScope(UnconfinedTestDispatcher(testDispatcher.scheduler))
        favoriteStore = FavoriteStore(fakeFavoriteRepository, applicationScope)
        fakeSearchHistoryDataSource = FakeSearchHistoryDataSource()
    }

    @After
    fun tearDown() {
        applicationScope.cancel()
    }

    /**
     * uiState は stateIn(WhileSubscribed) のため、購読者がいないと upstream が動かず value が更新されない。
     * 画面（collectAsStateWithLifecycle）相当の購読者を backgroundScope に常駐させる。
     */
    private fun TestScope.createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) = SearchViewModel(
        searchRepository = fakeSearchRepository,
        favoriteStore = favoriteStore,
        searchHistoryDataSource = fakeSearchHistoryDataSource,
        savedStateHandle = savedStateHandle,
    ).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun `SavedStateHandleに保存済みのクエリで起動すると入力欄に復元され、debounce後に再検索される`() = runTest {
        // Arrange: プロセス死後の復元を想定し、query="kotlin" を持つ SavedStateHandle と、1 件返す検索結果
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(RepositoryEntity(1, "JetBrains/kotlin", null, 10, "Kotlin", null)), hasNextPage = false)
        )

        // Act: そのハンドルで ViewModel を作る
        val viewModel = createViewModel(SavedStateHandle(mapOf("query" to "kotlin")))

        // Assert: 検索前でも入力欄のクエリは復元値（Idle のまま searchQuery="kotlin"）
        assertEquals("kotlin", viewModel.uiState.value.searchQuery)
        assertTrue(viewModel.uiState.value is SearchUiState.Idle)

        // Act: debounce（500ms）を進める
        advanceUntilIdle()

        // Assert: 通常入力時と同じ経路で再検索され、結果が復元される
        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Loaded)
        assertEquals("kotlin", state.searchQuery)
        assertEquals(listOf("JetBrains/kotlin"), (state as SearchUiState.Loaded).items.map { it.fullName })
    }

    @Test
    fun `onQueryChangedは入力をSavedStateHandleへ書き込む`() = runTest {
        // Arrange: 空の SavedStateHandle で作った ViewModel
        val handle = SavedStateHandle()
        val viewModel = createViewModel(handle)

        // Act: ユーザが "swift" と入力する
        viewModel.onQueryChanged("swift")
        runCurrent()

        // Assert: プロセス死後に復元できるよう、ハンドルの "query" に保存されている
        assertEquals("swift", handle.get<String>("query"))
        assertEquals("swift", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `初期状態はIdleである`() = runTest {
        // Arrange & Act: 何も操作していない ViewModel を作る
        val viewModel = createViewModel()

        // Assert: 検索前なので Idle
        assertTrue(viewModel.uiState.value is SearchUiState.Idle)
    }

    @Test
    fun `検索成功時にLoaded状態になる`() = runTest {
        // Arrange: owner/repo 1 件（★100・Kotlin）を返す検索結果と ViewModel
        val repo = RepositoryEntity(
            id = 1,
            fullName = "owner/repo",
            description = "test",
            starCount = 100,
            language = "Kotlin",
            ownerAvatarURL = null,
        )
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false)
        )
        val viewModel = createViewModel()

        // Act: "kotlin" で検索する
        viewModel.onQueryChanged("kotlin")
        advanceUntilIdle()

        // Assert: Loaded 状態で、owner/repo の 1 件が items に入っている
        val state = viewModel.uiState.value
        assertTrue("Expected Loaded, got $state", state is SearchUiState.Loaded)
        val items = (state as SearchUiState.Loaded).items
        assertEquals(1, items.size)
        assertEquals("owner/repo", items[0].fullName)
    }

    @Test
    fun `検索結果が空の場合Empty状態になる`() = runTest {
        // Arrange: 0 件（hasNextPage なし）を返す検索結果
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 0, items = emptyList(), hasNextPage = false)
        )
        val viewModel = createViewModel()

        // Act: ヒットしないクエリ "nonexistent" で検索する
        viewModel.onQueryChanged("nonexistent")
        advanceUntilIdle()

        // Assert: Empty 状態になる
        assertTrue(viewModel.uiState.value is SearchUiState.Empty)
    }

    @Test
    fun `検索失敗時にError状態になる`() = runTest {
        // Arrange: ネットワークエラー（AppError.Network）で失敗する検索
        fakeSearchRepository.searchResult = Result.failure(AppError.Network)
        val viewModel = createViewModel()

        // Act: "test" で検索する
        viewModel.onQueryChanged("test")
        advanceUntilIdle()

        // Assert: Error 状態で、文言は日本語ではなく error_network のリソース ID を指す
        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Error)
        assertEquals(UiText.Resource(DesignR.string.error_network), (state as SearchUiState.Error).message)
    }

    @Test
    fun `サーバーエラーはステータスコードを引数に持つerror_serverになる`() = runTest {
        // Arrange: HTTP 503 で失敗する検索
        fakeSearchRepository.searchResult = Result.failure(AppError.Server(503))
        val viewModel = createViewModel()

        // Act: 検索を実行する
        viewModel.onQueryChanged("test")
        advanceUntilIdle()

        // Assert: error_server のリソース ID + 引数 [503]（文字列化は UI 層）
        val state = viewModel.uiState.value as SearchUiState.Error
        assertEquals(UiText.Resource(DesignR.string.error_server, listOf(503)), state.message)
    }

    @Test
    fun `クエリを空にするとIdle状態に戻る`() = runTest {
        // Arrange: 検索成功（owner/repo 1 件）で Loaded にした ViewModel
        val repo = RepositoryEntity(
            id = 1,
            fullName = "owner/repo",
            description = null,
            starCount = 10,
            language = null,
            ownerAvatarURL = null,
        )
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false)
        )
        val viewModel = createViewModel()
        viewModel.onQueryChanged("test")
        advanceUntilIdle()

        // Act: クエリを空文字にする
        viewModel.onQueryChanged("")
        advanceUntilIdle()

        // Assert: Idle 状態に戻る
        assertTrue(viewModel.uiState.value is SearchUiState.Idle)
    }

    @Test
    fun `お気に入りトグルでisFavoriteがtrueになる`() = runTest {
        // Arrange: id=42 の 1 件が Loaded になっていて、お気に入りは未登録
        val repo = RepositoryEntity(
            id = 42,
            fullName = "owner/repo",
            description = null,
            starCount = 10,
            language = null,
            ownerAvatarURL = null,
        )
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false)
        )
        val viewModel = createViewModel()
        viewModel.onQueryChanged("test")
        advanceUntilIdle()

        // Act: 先頭の項目をお気に入りトグルする
        val item = (viewModel.uiState.value as SearchUiState.Loaded).items[0]
        viewModel.toggleFavorite(item)
        advanceUntilIdle()

        // Assert: 先頭項目が isFavorite=true になり、Repository への add が 1 回呼ばれる
        val updatedItem = (viewModel.uiState.value as SearchUiState.Loaded).items[0]
        assertTrue(updatedItem.isFavorite)
        assertEquals(1, fakeFavoriteRepository.addCallCount)
    }

    @Test
    fun `お気に入り解除でisFavoriteがfalseになる`() = runTest {
        // Arrange: id=42 が検索結果にあり、お気に入りにも登録済みの状態で Loaded にした ViewModel
        val repo = RepositoryEntity(
            id = 42,
            fullName = "owner/repo",
            description = null,
            starCount = 10,
            language = null,
            ownerAvatarURL = null,
        )
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false)
        )
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(
                id = 42,
                fullName = "owner/repo",
                description = null,
                starCount = 10,
                language = null,
                ownerAvatarURL = null,
                savedAt = 0L,
            )
        )
        val viewModel = createViewModel()
        viewModel.onQueryChanged("test")
        advanceUntilIdle()

        // Act: 登録済みの先頭項目をトグルする（解除）
        val item = (viewModel.uiState.value as SearchUiState.Loaded).items[0]
        assertTrue(item.isFavorite)
        viewModel.toggleFavorite(item)
        advanceUntilIdle()

        // Assert: 先頭項目が isFavorite=false になり、Repository への remove が 1 回呼ばれる
        val updatedItem = (viewModel.uiState.value as SearchUiState.Loaded).items[0]
        assertTrue(!updatedItem.isFavorite)
        assertEquals(1, fakeFavoriteRepository.removeCallCount)
    }

    @Test
    fun `onLoadMoreで次ページが追加される`() = runTest {
        // Arrange: page1/repo 1 件・次ページありの Loaded 状態
        val page1Repo = RepositoryEntity(
            id = 1,
            fullName = "page1/repo",
            description = null,
            starCount = 10,
            language = null,
            ownerAvatarURL = null,
        )
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 60, items = listOf(page1Repo), hasNextPage = true)
        )
        val viewModel = createViewModel()
        viewModel.onQueryChanged("test")
        advanceUntilIdle()

        // Act: 次ページ（page2/repo・最終ページ）を返す設定にして onLoadMore する
        val page2Repo = RepositoryEntity(
            id = 2,
            fullName = "page2/repo",
            description = null,
            starCount = 5,
            language = null,
            ownerAvatarURL = null,
        )
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 60, items = listOf(page2Repo), hasNextPage = false)
        )
        viewModel.onLoadMore()
        advanceUntilIdle()

        // Assert: items が page1→page2 の順に 2 件になり、2 ページ目が要求されている
        val items = (viewModel.uiState.value as SearchUiState.Loaded).items
        assertEquals(2, items.size)
        assertEquals("page1/repo", items[0].fullName)
        assertEquals("page2/repo", items[1].fullName)
        assertEquals(2, fakeSearchRepository.lastSearchPage)
    }

    @Test
    fun `スペースのみのクエリはIdleにリセットされる`() = runTest {
        // Arrange: 検索済み状態を作る
        val repo = RepositoryEntity(
            id = 1,
            fullName = "owner/repo",
            description = null,
            starCount = 10,
            language = null,
            ownerAvatarURL = null,
        )
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false)
        )
        val viewModel = createViewModel()
        viewModel.onQueryChanged("test")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is SearchUiState.Loaded)

        // Act: スペースのみ入力
        viewModel.onQueryChanged("   ")
        advanceUntilIdle()

        // Assert: Idle にリセット
        assertTrue(viewModel.uiState.value is SearchUiState.Idle)
    }

    @Test
    fun `ページ追加読み込み失敗時に既存データが維持される`() = runTest {
        // Arrange: 1ページ目を表示
        val repo = RepositoryEntity(
            id = 1,
            fullName = "page1/repo",
            description = null,
            starCount = 10,
            language = null,
            ownerAvatarURL = null,
        )
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 60, items = listOf(repo), hasNextPage = true)
        )
        val viewModel = createViewModel()
        viewModel.onQueryChanged("test")
        advanceUntilIdle()

        // Act: 2ページ目でエラー
        fakeSearchRepository.searchResult = Result.failure(AppError.Network)
        viewModel.onLoadMore()
        advanceUntilIdle()

        // Assert: 1ページ目のデータが維持される + isLoadingMore=false
        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Loaded)
        val loaded = state as SearchUiState.Loaded
        assertEquals(1, loaded.items.size)
        assertEquals("page1/repo", loaded.items[0].fullName)
        assertEquals(false, loaded.isLoadingMore)
    }

    @Test
    fun `お気に入り追加失敗時にuserMessagesへ通知が積まれuserMessageShownで消える`() = runTest {
        // Arrange: 1件ヒットした検索結果を表示し、お気に入り追加が失敗するよう設定
        val repo = RepositoryEntity(
            id = 42,
            fullName = "owner/repo",
            description = null,
            starCount = 10,
            language = null,
            ownerAvatarURL = null,
        )
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false)
        )
        fakeFavoriteRepository.shouldThrowOnAdd = true
        val viewModel = createViewModel()
        viewModel.onQueryChanged("test")
        advanceUntilIdle()

        // Act: お気に入りをトグルして失敗させる
        val item = (viewModel.uiState.value as SearchUiState.Loaded).items[0]
        viewModel.toggleFavorite(item)
        advanceUntilIdle()

        // Assert: 失敗文言の通知が 1 件だけ積まれている
        val messages = viewModel.userMessages.value
        assertEquals(1, messages.size)
        assertEquals(UiText.Resource(R.string.search_error_favorite_update), messages[0].text)

        // Act: View が表示し終えたことを通知
        viewModel.userMessageShown(messages[0].id)

        // Assert: キューが空になる
        assertTrue(viewModel.userMessages.value.isEmpty())
    }

    @Test
    fun `追加読み込み失敗時にuserMessagesへ通知が積まれuserMessageShownで消える`() = runTest {
        // Arrange: 次ページありの 1 ページ目を表示
        val repo = RepositoryEntity(1, "page1/repo", null, 10, null, null)
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 60, items = listOf(repo), hasNextPage = true)
        )
        val viewModel = createViewModel()
        viewModel.onQueryChanged("test")
        advanceUntilIdle()

        // Act: 2 ページ目の取得を失敗させる
        fakeSearchRepository.searchResult = Result.failure(AppError.Network)
        viewModel.onLoadMore()
        advanceUntilIdle()

        // Assert: 失敗文言の通知が 1 件だけ積まれている
        val messages = viewModel.userMessages.value
        assertEquals(1, messages.size)
        assertEquals(UiText.Resource(R.string.search_error_load_more), messages[0].text)

        // Act: 表示完了を通知
        viewModel.userMessageShown(messages[0].id)

        // Assert: キューが空になる
        assertTrue(viewModel.userMessages.value.isEmpty())
    }

    @Test
    fun `onRefreshで再検索が実行され結果が更新される`() = runTest {
        // Arrange: 初回検索
        val repo1 = RepositoryEntity(
            id = 1,
            fullName = "old/repo",
            description = null,
            starCount = 10,
            language = null,
            ownerAvatarURL = null,
        )
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(repo1), hasNextPage = false)
        )
        val viewModel = createViewModel()
        viewModel.onQueryChanged("test")
        advanceUntilIdle()

        // Act: 新しいデータで再検索
        val repo2 = RepositoryEntity(
            id = 2,
            fullName = "new/repo",
            description = null,
            starCount = 5,
            language = null,
            ownerAvatarURL = null,
        )
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(repo2), hasNextPage = false)
        )
        viewModel.onRefresh()
        advanceUntilIdle()

        // Assert: 新しいデータに更新
        val items = (viewModel.uiState.value as SearchUiState.Loaded).items
        assertEquals(1, items.size)
        assertEquals("new/repo", items[0].fullName)
    }

    @Test
    fun `hasNextPageがfalseの場合onLoadMoreは何もしない`() = runTest {
        // Arrange: hasNextPage = false
        val repo = RepositoryEntity(
            id = 1,
            fullName = "owner/repo",
            description = null,
            starCount = 10,
            language = null,
            ownerAvatarURL = null,
        )
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false)
        )
        val viewModel = createViewModel()
        viewModel.onQueryChanged("test")
        advanceUntilIdle()
        val callCountBefore = fakeSearchRepository.searchCallCount

        // Act: 次ページが無い状態で onLoadMore する
        viewModel.onLoadMore()
        advanceUntilIdle()

        // Assert: 追加のAPI呼び出しなし
        assertEquals(callCountBefore, fakeSearchRepository.searchCallCount)
    }

    @Test
    fun `検索成功時に履歴に追加される`() = runTest {
        // Arrange: owner/repo 1 件を返す検索結果と ViewModel
        val repo = RepositoryEntity(1, "owner/repo", null, 10, null, null)
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false)
        )
        val viewModel = createViewModel()

        // Act: "kotlin" で検索する
        viewModel.onQueryChanged("kotlin")
        advanceUntilIdle()

        // Assert: 履歴に "kotlin" が追加されている
        assertEquals(1, fakeSearchHistoryDataSource.addCallCount)
        assertEquals("kotlin", fakeSearchHistoryDataSource.lastAddedQuery)
    }

    @Test
    fun `Idle状態でhistoryが反映される`() = runTest {
        // Arrange: 履歴をシード
        fakeSearchHistoryDataSource.setHistoryForTest(listOf("swift", "compose"))
        val viewModel = createViewModel()

        // Act: ViewModel 起動後に履歴 Flow が反映される
        advanceUntilIdle()

        // Assert: Idle.history に履歴が反映される
        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Idle)
        assertEquals(listOf("swift", "compose"), (state as SearchUiState.Idle).history)
    }

    @Test
    fun `selectHistoryで即時検索が実行される`() = runTest {
        // Arrange: owner/repo 1 件を返す検索結果と ViewModel
        val repo = RepositoryEntity(1, "owner/repo", null, 10, null, null)
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false)
        )
        val viewModel = createViewModel()

        // Act: 履歴から選択
        viewModel.selectHistory("kotlin")
        advanceUntilIdle()

        // Assert: 検索クエリが反映され、Loaded 状態に遷移する
        val state = viewModel.uiState.value
        assertTrue("Expected Loaded, got $state", state is SearchUiState.Loaded)
        assertEquals("kotlin", state.searchQuery)
    }

    @Test
    fun `selectHistoryは検索を1回だけ実行し履歴追加も1回だけである`() = runTest {
        // Arrange: owner/repo 1 件を返す検索結果と ViewModel
        val repo = RepositoryEntity(1, "owner/repo", null, 10, null, null)
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false)
        )
        val viewModel = createViewModel()

        // Act: 履歴から "kotlin" を選び、debounce（500ms）が経過するまで仮想時間を進める
        viewModel.selectHistory("kotlin")
        advanceUntilIdle()

        // Assert: 即時検索のあとで debounce 経由の検索が重複して走らず、リクエストも履歴追加も 1 回
        assertEquals(1, fakeSearchRepository.searchCallCount)
        assertEquals(1, fakeSearchHistoryDataSource.addCallCount)
        assertEquals("kotlin", fakeSearchHistoryDataSource.lastAddedQuery)
    }

    @Test
    fun `onRefreshは同じクエリを debounce 待ちなしで1回だけ再検索する`() = runTest {
        // Arrange: "kotlin" で検索済み（Loaded）の ViewModel
        val repo = RepositoryEntity(1, "owner/repo", null, 10, null, null)
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false)
        )
        val viewModel = createViewModel()
        viewModel.onQueryChanged("kotlin")
        advanceUntilIdle()
        assertEquals(1, fakeSearchRepository.searchCallCount)

        // Act: プル・トゥ・リフレッシュ
        viewModel.onRefresh()
        advanceUntilIdle()

        // Assert: 再検索はちょうど 1 回増え（計 2 回）、結果は Loaded でスピナーは閉じている
        assertEquals(2, fakeSearchRepository.searchCallCount)
        val state = viewModel.uiState.value as SearchUiState.Loaded
        assertEquals(false, state.isRefreshing)
    }

    @Test
    fun `次ページ取得中にクエリが変わったら古いページの結果は破棄される`() = runTest {
        // Arrange: "a" の 1 ページ目（a/page1・次ページあり）を表示中
        val pageOne = RepositoryEntity(1, "a/page1", null, 10, null, null)
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 60, items = listOf(pageOne), hasNextPage = true)
        )
        val viewModel = createViewModel()
        viewModel.onQueryChanged("a")
        advanceUntilIdle()

        // Act: "a" の 2 ページ目を通信中のまま止め、その間にクエリを "b" へ変えて "b" の検索を完了させる
        val gate = CompletableDeferred<Unit>()
        fakeSearchRepository.searchGate = gate
        viewModel.onLoadMore()
        runCurrent()
        fakeSearchRepository.searchGate = null
        val newResult = RepositoryEntity(2, "b/page1", null, 5, null, null)
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(newResult), hasNextPage = false)
        )
        viewModel.onQueryChanged("b")
        advanceUntilIdle()
        // その後で "a" の 2 ページ目（a/page2）の通信が完了する
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 60, items = listOf(RepositoryEntity(3, "a/page2", null, 1, null, null)), hasNextPage = false)
        )
        gate.complete(Unit)
        advanceUntilIdle()

        // Assert: "b" の結果だけが残り、a/page2 は混ざらず、読み込み中表示も残らない
        val state = viewModel.uiState.value as SearchUiState.Loaded
        assertEquals(listOf("b/page1"), state.items.map { it.fullName })
        assertEquals(false, state.isLoadingMore)
    }

    @Test
    fun `clearHistoryで履歴が全消去される`() = runTest {
        // Arrange: 履歴 [a, b, c] をシードした ViewModel
        fakeSearchHistoryDataSource.setHistoryForTest(listOf("a", "b", "c"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Act: 履歴を全消去する
        viewModel.clearHistory()
        advanceUntilIdle()

        // Assert: history Flow が空になる → Idle.history も空
        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Idle)
        assertTrue((state as SearchUiState.Idle).history.isEmpty())
    }

    @Test
    fun `Loaded状態からクエリ消去でIdleに戻る際 履歴が反映される（combine 経由）`() = runTest {
        // Arrange: 初期履歴をシード + 検索成功で Loaded 状態にする
        fakeSearchHistoryDataSource.setHistoryForTest(listOf("kotlin", "swift"))
        val repo = RepositoryEntity(1, "owner/repo", null, 10, null, null)
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false)
        )
        val viewModel = createViewModel()
        viewModel.onQueryChanged("compose")
        advanceUntilIdle()
        // 検索成功で Loaded、かつ DataStore に "compose" が追加されている状態
        assertTrue(viewModel.uiState.value is SearchUiState.Loaded)

        // Act: クエリを空にして Idle に戻す
        viewModel.onQueryChanged("")
        advanceUntilIdle()

        // Assert: Idle.history に最新スナップショット（"compose" が先頭の履歴）が反映される
        // ※ combine + stateIn により、phase 遷移と同時に履歴 Flow の最新値が自動的に合成される
        //   （手動キャッシュ不要・宣言的なリアクティブパターン）
        val state = viewModel.uiState.value
        assertTrue("Expected Idle, got $state", state is SearchUiState.Idle)
        val idle = state as SearchUiState.Idle
        assertEquals(listOf("compose", "kotlin", "swift"), idle.history)
    }

    @Test
    fun `存在しないIDのtoggleFavoriteは何もしない`() = runTest {
        // Arrange: owner/repo 1 件が Loaded になっている ViewModel
        val repo = RepositoryEntity(
            id = 1,
            fullName = "owner/repo",
            description = null,
            starCount = 10,
            language = null,
            ownerAvatarURL = null,
        )
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false)
        )
        val viewModel = createViewModel()
        viewModel.onQueryChanged("test")
        advanceUntilIdle()

        // Act: 存在しないIDでtoggle
        val fakeItem = com.example.githubfinder.core.model.RepositoryDisplayItem.from(
            RepositoryEntity(
                id = 999,
                fullName = "fake/repo",
                description = null,
                starCount = 0,
                language = null,
                ownerAvatarURL = null,
            ),
            isFavorite = false,
        )
        viewModel.toggleFavorite(fakeItem)
        advanceUntilIdle()

        // Assert: addもremoveも呼ばれない
        assertEquals(0, fakeFavoriteRepository.addCallCount)
        assertEquals(0, fakeFavoriteRepository.removeCallCount)
    }

    @Test
    fun `追加読み込み中にスコープがキャンセルされても失敗通知は積まれない`() = runTest {
        // Arrange: 次ページありの 1 ページ目を表示し、2 ページ目の取得を中断させておく
        val repo = RepositoryEntity(1, "page1/repo", null, 10, null, null)
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 60, items = listOf(repo), hasNextPage = true)
        )
        val viewModel = createViewModel()
        viewModel.onQueryChanged("test")
        advanceUntilIdle()
        fakeSearchRepository.searchGate = CompletableDeferred()
        viewModel.onLoadMore()
        runCurrent()
        assertEquals(2, fakeSearchRepository.searchCallCount)

        // Act: 通信中に画面が閉じられた想定で viewModelScope をキャンセルする
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()

        // Assert: キャンセルは失敗ではないので「追加読み込みに失敗しました」が積まれない
        assertTrue(viewModel.userMessages.value.isEmpty())
    }

    @Test
    fun `通信中に入力した新しいクエリが検索結果で上書きされない`() = runTest {
        // Arrange: "kotlin" の検索を通信中のまま止めておく（結果は 1 件）
        val repo = RepositoryEntity(1, "owner/kotlin", null, 10, null, null)
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false)
        )
        val gate = CompletableDeferred<Unit>()
        fakeSearchRepository.searchGate = gate
        val viewModel = createViewModel()
        viewModel.onQueryChanged("kotlin")
        advanceTimeBy(501)
        runCurrent()
        assertTrue(viewModel.uiState.value is SearchUiState.Loading)

        // Act: 通信中（デバウンス未経過）にクエリを "kotlin coroutines" へ更新し、そのあと古い検索が完了する
        viewModel.onQueryChanged("kotlin coroutines")
        gate.complete(Unit)
        runCurrent()

        // Assert: 古い結果は表示されるが、入力欄のクエリは新しい "kotlin coroutines" のまま
        val state = viewModel.uiState.value
        assertTrue("Expected Loaded, got $state", state is SearchUiState.Loaded)
        assertEquals("kotlin coroutines", state.searchQuery)
    }

    @Test
    fun `新しいクエリが確定すると通信中の古い検索がキャンセルされ最新クエリの結果だけが残る`() = runTest {
        // Arrange: "kotlin" の検索を通信中のまま止めておく
        val repo = RepositoryEntity(2, "owner/compose", null, 10, null, null)
        fakeSearchRepository.searchResult = Result.success(
            SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false)
        )
        val gate = CompletableDeferred<Unit>()
        fakeSearchRepository.searchGate = gate
        val viewModel = createViewModel()
        viewModel.onQueryChanged("kotlin")
        advanceTimeBy(501)
        runCurrent()
        assertEquals("kotlin", fakeSearchRepository.lastSearchQuery)

        // Act: 通信中に "compose" を入力し、デバウンス経過で 2 件目の検索が始まる
        viewModel.onQueryChanged("compose")
        advanceTimeBy(501)
        runCurrent()

        // Assert: 1 件目だけがキャンセルされ、キャンセルは Error にならず 2 件目は Loading のまま
        assertEquals(listOf("kotlin"), fakeSearchRepository.cancelledQueries)
        assertEquals("compose", fakeSearchRepository.lastSearchQuery)
        val loading = viewModel.uiState.value
        assertTrue("Expected Loading, got $loading", loading is SearchUiState.Loading)
        assertEquals("compose", loading.searchQuery)

        // Act: 2 件目の通信が完了する
        gate.complete(Unit)
        advanceUntilIdle()

        // Assert: 最新クエリ "compose" の結果が Loaded になり、検索は計 2 回だけ
        val loaded = viewModel.uiState.value
        assertTrue("Expected Loaded, got $loaded", loaded is SearchUiState.Loaded)
        assertEquals("compose", loaded.searchQuery)
        assertEquals(2, fakeSearchRepository.searchCallCount)
    }

}
