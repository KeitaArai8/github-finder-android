package com.example.githubfinder.feature.favorite

import com.example.githubfinder.core.testing.MainDispatcherRule
import com.example.githubfinder.core.testing.FakeFavoriteRepository
import com.example.githubfinder.core.data.FavoriteStore
import com.example.githubfinder.core.designsystem.UiText
import com.example.githubfinder.core.model.FavoriteEntity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var fakeFavoriteRepository: FakeFavoriteRepository
    private lateinit var favoriteStore: FavoriteStore
    private lateinit var storeScope: CoroutineScope

    @Before
    fun setup() {
        fakeFavoriteRepository = FakeFavoriteRepository()
        // FavoriteStore（Eagerly の stateIn）用のアプリ寿命スコープ。Unconfined なので Fake の更新が即 store.favorites に届く
        storeScope = CoroutineScope(UnconfinedTestDispatcher(testDispatcher.scheduler))
        favoriteStore = FavoriteStore(fakeFavoriteRepository, storeScope)
    }

    private fun createViewModel(applicationScope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testDispatcher.scheduler))) =
        FavoriteViewModel(favoriteStore, applicationScope)

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    @Test
    fun `最初の emission を処理するまではLoadingである`() = runTest {
        // Arrange & Act: お気に入り1件がある状態で ViewModel を作る（購読はまだ進めない）
        fakeFavoriteRepository.stored = listOf(FavoriteEntity(1, "owner/repo", null, 1, null, null, 0L))
        val viewModel = createViewModel()

        // Assert: スケジューラを進める前は Loading（Empty や Loaded を先取りしない）
        assertTrue(viewModel.uiState.value is FavoriteUiState.Loading)
    }

    @Test
    fun `Roomの初回emissionが届くまでEmptyにならずLoadingのままで届いた後に遷移する`() = runTest {
        // Arrange: まだ emit しない Flow を持つ Repository から作った Store と ViewModel
        val source = kotlinx.coroutines.flow.MutableSharedFlow<List<FavoriteEntity>>()
        val repository = object : com.example.githubfinder.core.data.repository.FavoriteRepository {
            override val favorites: kotlinx.coroutines.flow.Flow<List<FavoriteEntity>> = source
            override suspend fun add(repository: com.example.githubfinder.core.model.RepositoryEntity) = Unit
            override suspend fun remove(id: Int) = Unit
        }
        val viewModel = FavoriteViewModel(
            FavoriteStore(repository, storeScope),
            CoroutineScope(UnconfinedTestDispatcher(testDispatcher.scheduler)),
        )

        // Act: 初回 emission の前にスケジューラを進める
        advanceUntilIdle()

        // Assert: 初期値の空リストを Empty と誤認せず Loading のまま
        assertTrue(viewModel.uiState.value is FavoriteUiState.Loading)

        // Act: Room の初回 emission（0 件）が届く
        source.emit(emptyList())
        advanceUntilIdle()

        // Assert: ここで初めて Empty になる
        assertTrue(viewModel.uiState.value is FavoriteUiState.Empty)
    }

    @Test
    fun `お気に入りがある場合Loaded状態になる`() = runTest {
        // Arrange: 1件のお気に入りを持つ Repository
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(
                id = 1,
                fullName = "owner/repo",
                description = null,
                starCount = 10,
                language = null,
                ownerAvatarURL = null,
                savedAt = 0L,
            )
        )
        val viewModel = createViewModel()

        // Act: ViewModel の購読が最初の emission を処理するまで進める
        advanceUntilIdle()

        // Assert: Loaded 状態で 1 件を保持している
        val state = viewModel.uiState.value
        assertTrue(state is FavoriteUiState.Loaded)
        assertEquals(1, (state as FavoriteUiState.Loaded).favorites.size)
    }

    @Test
    fun `お気に入りが空の場合Empty状態になる`() = runTest {
        // Arrange: お気に入りが 0 件の Repository
        fakeFavoriteRepository.stored = listOf()
        val viewModel = createViewModel()

        // Act: ViewModel の購読が最初の emission を処理するまで進める
        advanceUntilIdle()

        // Assert: Empty 状態になる
        assertTrue(viewModel.uiState.value is FavoriteUiState.Empty)
    }

    @Test
    fun `removeFavoriteは即座にpendingDeleteIDsへ追加されdisplayedからは消えるが favorites本体は残る（Gmail スタイル楽観的更新）`() = runTest {
        // Arrange: 2件（id=1,2）を読み込み済みにする
        val fav1 = FavoriteEntity(
            id = 1,
            fullName = "owner/repo1",
            description = null,
            starCount = 10,
            language = null,
            ownerAvatarURL = null,
            savedAt = 0L,
        )
        val fav2 = FavoriteEntity(
            id = 2,
            fullName = "owner/repo2",
            description = null,
            starCount = 5,
            language = null,
            ownerAvatarURL = null,
            savedAt = 0L,
        )
        fakeFavoriteRepository.stored = listOf(fav1, fav2)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Act: スワイプ削除（Undo 猶予は経過させない = commit はまだ）
        viewModel.removeFavorite(fav1)
        runCurrent()

        // Assert: UI（displayedFavorites）からは消えるが、Room は触っていない（commit 待ち）
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(setOf(1), state.pendingDeleteIDs)
        assertEquals(2, state.favorites.size) // 本体は残る
        assertEquals(listOf(2), state.displayedFavorites.map { it.id }) // 表示からは消える
        assertEquals(0, fakeFavoriteRepository.removeCallCount) // Room 未到達
    }

    @Test
    fun `Undo猶予が経過するとRoomから実削除されpendingがクリアされfavorites本体からも消える`() = runTest {
        // Arrange: 2件（id=1,2）を読み込み済みにする
        val fav1 = FavoriteEntity(1, "owner/repo1", null, 10, null, null, 0L)
        val fav2 = FavoriteEntity(2, "owner/repo2", null, 5, null, null, 0L)
        fakeFavoriteRepository.stored = listOf(fav1, fav2)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.removeFavorite(fav1)
        runCurrent()

        // Act: Undo せずに猶予時間を経過させる → ViewModel のタイマーが実削除
        advanceTimeBy(UNDO_WINDOW_MS)
        runCurrent()

        // Assert: Room から削除され、pendingDeleteIDs もクリアされる
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertTrue(state.pendingDeleteIDs.isEmpty())
        assertEquals(1, state.favorites.size)
        assertEquals(2, state.favorites[0].id)
        assertEquals(1, fakeFavoriteRepository.removeCallCount)
    }

    @Test
    fun `undoDeleteでpendingから外れUIに行が復元される（Roomは触らない）`() = runTest {
        // Arrange: 1件（id=1）を読み込み済みにする
        val fav = FavoriteEntity(1, "owner/repo", null, 10, null, null, 0L)
        fakeFavoriteRepository.stored = listOf(fav)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.removeFavorite(fav)
        runCurrent()

        // Act: 猶予内に「元に戻す」を実行し、その後に猶予時間を経過させる
        viewModel.undoDelete(fav.id)
        advanceTimeBy(UNDO_WINDOW_MS)
        runCurrent()

        // Assert: pending クリア、タイマーもキャンセルされているので Room には触れず removeCallCount = 0
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertTrue(state.pendingDeleteIDs.isEmpty())
        assertEquals(1, state.displayedFavorites.size)
        assertEquals(0, fakeFavoriteRepository.removeCallCount)
    }

    @Test
    fun `最後の1件のUndo猶予が経過するとEmpty状態になる`() = runTest {
        // Arrange: 1件（id=1）を読み込み済みにする
        val fav = FavoriteEntity(
            id = 1,
            fullName = "owner/repo",
            description = null,
            starCount = 10,
            language = null,
            ownerAvatarURL = null,
            savedAt = 0L,
        )
        fakeFavoriteRepository.stored = listOf(fav)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Act: スワイプ → 猶予経過で commit
        viewModel.removeFavorite(fav)
        runCurrent()
        advanceTimeBy(UNDO_WINDOW_MS)
        runCurrent()

        // Assert: 最後の 1 件が実削除され Empty 状態になる
        assertTrue(viewModel.uiState.value is FavoriteUiState.Empty)
    }

    @Test
    fun `removeFavoriteでundoID付きの通知が積まれuserMessageShownで消える`() = runTest {
        // Arrange: 1件読み込み済み
        val fav = FavoriteEntity(42, "owner/repo", null, 10, null, null, 0L)
        fakeFavoriteRepository.stored = listOf(fav)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Act: スワイプ削除
        viewModel.removeFavorite(fav)
        runCurrent()

        // Assert: 削除対象 ID が pending に入り、undoID=42 の通知が 1 件積まれている
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertTrue(42 in state.pendingDeleteIDs)
        val messages = viewModel.userMessages.value
        assertEquals(1, messages.size)
        assertEquals(42, messages[0].undoID)
        assertEquals(
            UiText.Resource(R.string.favorite_deleted_with_name, listOf("owner/repo")),
            messages[0].text,
        )

        // Act: View が表示し終えたことを通知
        viewModel.userMessageShown(messages[0].id)

        // Assert: キューが空になる
        assertTrue(viewModel.userMessages.value.isEmpty())
    }

    @Test
    fun `連続して2件スワイプ削除し猶予が経過すると両方がRoomから実削除される`() = runTest {
        // Arrange: 3件読み込み済み
        val fav1 = FavoriteEntity(1, "owner/repo1", null, 10, null, null, 0L)
        val fav2 = FavoriteEntity(2, "owner/repo2", null, 5, null, null, 0L)
        val fav3 = FavoriteEntity(3, "owner/repo3", null, 1, null, null, 0L)
        fakeFavoriteRepository.stored = listOf(fav1, fav2, fav3)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Act: SnackBar の表示完了を待たずに 2 件続けてスワイプし、猶予を経過させる
        viewModel.removeFavorite(fav1)
        viewModel.removeFavorite(fav2)
        runCurrent()
        advanceTimeBy(UNDO_WINDOW_MS)
        runCurrent()

        // Assert: 両方とも実削除され pending は空、残りは fav3 のみ（pending 取り残しなし）
        assertEquals(2, fakeFavoriteRepository.removeCallCount)
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertTrue(state.pendingDeleteIDs.isEmpty())
        assertEquals(listOf(3), state.favorites.map { it.id })
    }

    @Test
    fun `スワイプ削除の猶予中に ViewModel が破棄されると onCleared で実削除が確定する`() = runTest {
        // Arrange: 2件読み込み済みで、ViewModelStore 管理下の ViewModel が id=1 をスワイプ削除した（猶予は未経過）
        val fav1 = FavoriteEntity(1, "owner/repo1", null, 10, null, null, 0L)
        val fav2 = FavoriteEntity(2, "owner/repo2", null, 5, null, null, 0L)
        fakeFavoriteRepository.stored = listOf(fav1, fav2)
        val viewModelStore = ViewModelStore()
        val viewModel = ViewModelProvider(
            viewModelStore,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = createViewModel() as T
            },
        )[FavoriteViewModel::class.java]
        advanceUntilIdle()
        viewModel.removeFavorite(fav1)
        runCurrent()
        assertEquals(0, fakeFavoriteRepository.removeCallCount)

        // Act: タブ切替相当で ViewModel を破棄し、アプリ寿命スコープの処理まで進める（猶予時間は進めない）
        viewModelStore.clear()
        advanceUntilIdle()

        // Assert: 猶予を待たずに id=1 だけが 1 回実削除されている
        assertEquals(1, fakeFavoriteRepository.removeCallCount)
        assertEquals(listOf(2), fakeFavoriteRepository.stored.map { it.id })
    }

    @Test
    fun `Undo 猶予経過後の Room 削除中に ViewModel が破棄されても実削除は完了する`() = runTest {
        // Arrange: id=1 を含む 2 件を読み込み済みで、Room 書き込みが完了待ちで止まる Repository + ViewModelStore 管理下の ViewModel
        val fav1 = FavoriteEntity(1, "owner/repo1", null, 10, null, null, 0L)
        val fav2 = FavoriteEntity(2, "owner/repo2", null, 5, null, null, 0L)
        fakeFavoriteRepository.stored = listOf(fav1, fav2)
        val gate = CompletableDeferred<Unit>()
        fakeFavoriteRepository.removeGate = gate
        val viewModelStore = ViewModelStore()
        val viewModel = ViewModelProvider(
            viewModelStore,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = createViewModel() as T
            },
        )[FavoriteViewModel::class.java]
        advanceUntilIdle()
        viewModel.removeFavorite(fav1)
        advanceTimeBy(UNDO_WINDOW_MS + 1)
        runCurrent()
        assertEquals(0, fakeFavoriteRepository.removeCallCount)

        // Act: 実削除が Room 書き込みで中断している最中に ViewModel を破棄し、その後に書き込みを完了させる
        viewModelStore.clear()
        gate.complete(Unit)
        advanceUntilIdle()

        // Assert: viewModelScope の取り消しに巻き込まれず、id=1 だけが 1 回実削除されている
        assertEquals(1, fakeFavoriteRepository.removeCallCount)
        assertEquals(listOf(2), fakeFavoriteRepository.stored.map { it.id })
    }

    @Test
    fun `一括削除の確定時に猶予中のスワイプ削除は非表示のまま維持される`() = runTest {
        // Arrange: 3件読み込み済みで、id=1 をスワイプ削除（猶予中）し、編集モードで id=2 を選択した
        val fav1 = FavoriteEntity(1, "owner/repo1", null, 10, null, null, 0L)
        val fav2 = FavoriteEntity(2, "owner/repo2", null, 5, null, null, 0L)
        val fav3 = FavoriteEntity(3, "owner/repo3", null, 1, null, null, 0L)
        fakeFavoriteRepository.stored = listOf(fav1, fav2, fav3)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.removeFavorite(fav1)
        viewModel.enterEditMode()
        viewModel.toggleSelection(2)
        runCurrent()

        // Act: 猶予内に一括削除を確定する（実削除されるのは id=2 のみ）
        viewModel.confirmRemoveSelected()
        runCurrent()

        // Assert: id=1 は pending のまま表示に戻らず、編集モードも維持されている
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(setOf(1), state.pendingDeleteIDs)
        assertEquals(listOf(3), state.displayedFavorites.map { it.id })
        assertTrue(state.isEditMode)
    }

    @Test
    fun `実削除の失敗時にpendingが戻され失敗通知が積まれる`() = runTest {
        // Arrange: 1件読み込み済みで、実削除が失敗するよう設定
        val fav = FavoriteEntity(
            id = 1,
            fullName = "owner/repo",
            description = null,
            starCount = 10,
            language = null,
            ownerAvatarURL = null,
            savedAt = 0L,
        )
        fakeFavoriteRepository.stored = listOf(fav)
        val viewModel = createViewModel()
        advanceUntilIdle()
        fakeFavoriteRepository.shouldThrowOnRemove = true

        // Act: スワイプ削除し、Undo せずに猶予を経過させる（実削除が失敗する）
        viewModel.removeFavorite(fav)
        runCurrent()
        advanceTimeBy(UNDO_WINDOW_MS)
        runCurrent()

        // Assert: pending から戻って行が復元され、削除通知に続いて失敗通知が積まれている
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertTrue(state.pendingDeleteIDs.isEmpty())
        assertEquals(1, state.displayedFavorites.size)
        assertEquals(
            listOf(
                UiText.Resource(R.string.favorite_deleted_with_name, listOf("owner/repo")),
                UiText.Resource(R.string.favorite_error_delete),
            ),
            viewModel.userMessages.value.map { it.text },
        )
    }

    @Test
    fun `enterEditModeでisEditModeがtrueになる`() = runTest {
        // Arrange: Loaded状態にする
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(1, "owner/repo", null, 10, null, null, 0L),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Act: 編集モード開始
        viewModel.enterEditMode()

        // Assert: isEditMode=true, selectedIDs空
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertTrue(state.isEditMode)
        assertTrue(state.selectedIDs.isEmpty())
    }

    @Test
    fun `exitEditModeでisEditModeがfalseになり選択がクリアされる`() = runTest {
        // Arrange: 編集モードで1件選択
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(1, "owner/repo", null, 10, null, null, 0L),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.enterEditMode()
        viewModel.toggleSelection(1)

        // Act: 編集モード終了
        viewModel.exitEditMode()

        // Assert: 編集モードオフ、選択もクリア
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertFalse(state.isEditMode)
        assertTrue(state.selectedIDs.isEmpty())
    }

    @Test
    fun `toggleSelectionで選択状態がトグルされる`() = runTest {
        // Arrange: 編集モード
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(42, "owner/repo", null, 10, null, null, 0L),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.enterEditMode()

        // Act & Assert: 2回トグルして選択→解除
        viewModel.toggleSelection(42)
        assertEquals(setOf(42), (viewModel.uiState.value as FavoriteUiState.Loaded).selectedIDs)

        viewModel.toggleSelection(42)
        assertTrue((viewModel.uiState.value as FavoriteUiState.Loaded).selectedIDs.isEmpty())
    }

    @Test
    fun `selectAllで全IDが選択される`() = runTest {
        // Arrange: 3件のお気に入り
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(1, "owner/a", null, 0, null, null, 0L),
            FavoriteEntity(2, "owner/b", null, 0, null, null, 0L),
            FavoriteEntity(3, "owner/c", null, 0, null, null, 0L),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.enterEditMode()

        // Act: 全選択
        viewModel.selectAll()

        // Assert: 全IDが選択される
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(setOf(1, 2, 3), state.selectedIDs)
    }

    @Test
    fun `selectAllはフィルタ後の表示中アイテムだけを選択対象にする`() = runTest {
        // Arrange: Swift と Kotlin が混在し、Swift にフィルタを掛けた状態
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(1, "a/swift1", null, 0, "Swift", null, 0L),
            FavoriteEntity(2, "b/kotlin1", null, 0, "Kotlin", null, 0L),
            FavoriteEntity(3, "c/swift2", null, 0, "Swift", null, 0L),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.enterEditMode()
        viewModel.setFilterLanguage("Swift")

        // Act: selectAll を実行する
        viewModel.selectAll()

        // Assert: 表示中の Swift 2 件のみが選択され、非表示の Kotlin は含まれない
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(setOf(1, 3), state.selectedIDs)
    }

    @Test
    fun `toggleSelectAllは全未選択から全選択へトグルする`() = runTest {
        // Arrange: 編集モード、未選択状態
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(1, "owner/a", null, 0, null, null, 0L),
            FavoriteEntity(2, "owner/b", null, 0, null, null, 0L),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.enterEditMode()

        // Act: toggleSelectAll を実行する
        viewModel.toggleSelectAll()

        // Assert: 全選択 + isAllSelected が true
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(setOf(1, 2), state.selectedIDs)
        assertTrue(state.isAllSelected)
    }

    @Test
    fun `toggleSelectAllは全選択状態から全解除へトグルする`() = runTest {
        // Arrange: 全選択済み状態
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(1, "owner/a", null, 0, null, null, 0L),
            FavoriteEntity(2, "owner/b", null, 0, null, null, 0L),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.enterEditMode()
        viewModel.selectAll()
        assertTrue((viewModel.uiState.value as FavoriteUiState.Loaded).isAllSelected)

        // Act: toggleSelectAll を実行する
        viewModel.toggleSelectAll()

        // Assert: 全解除 + isAllSelected が false
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertTrue(state.selectedIDs.isEmpty())
        assertFalse(state.isAllSelected)
    }

    @Test
    fun `toggleSelectAllは部分選択状態から全選択へトグルする（iOS Photos 同等の挙動）`() = runTest {
        // Arrange: 3件中1件のみ選択
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(1, "owner/a", null, 0, null, null, 0L),
            FavoriteEntity(2, "owner/b", null, 0, null, null, 0L),
            FavoriteEntity(3, "owner/c", null, 0, null, null, 0L),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.enterEditMode()
        viewModel.toggleSelection(2)

        // Act: 部分選択中のトグル → iOS Photos.app では「全選択」扱い
        viewModel.toggleSelectAll()

        // Assert: 部分選択から全 3 件が選択され isAllSelected が true になる
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(setOf(1, 2, 3), state.selectedIDs)
        assertTrue(state.isAllSelected)
    }

    @Test
    fun `requestRemoveSelectedで選択ゼロ件なら確認ダイアログが出ない`() = runTest {
        // Arrange: 編集モードで選択なし
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(1, "owner/repo", null, 0, null, null, 0L),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.enterEditMode()

        // Act: 削除要求
        viewModel.requestRemoveSelected()

        // Assert: 確認ダイアログは出ない
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertFalse(state.isDeleteConfirmationShown)
    }

    @Test
    fun `confirmRemoveSelectedで選択中の項目が一括削除される`() = runTest {
        // Arrange: 3件中2件を選択
        val initial = mutableListOf(
            FavoriteEntity(1, "owner/a", null, 0, null, null, 0L),
            FavoriteEntity(2, "owner/b", null, 0, null, null, 0L),
            FavoriteEntity(3, "owner/c", null, 0, null, null, 0L),
        )
        fakeFavoriteRepository.stored = initial
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.enterEditMode()
        viewModel.toggleSelection(1)
        viewModel.toggleSelection(2)

        // Act: 一括削除（FakeFavoriteRepository.remove はID指定で該当を削除する）
        viewModel.confirmRemoveSelected()
        advanceUntilIdle()

        // Assert: removeCallCount=2, 残り1件, 選択クリア, 編集モード維持
        assertEquals(2, fakeFavoriteRepository.removeCallCount)
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(1, state.favorites.size)
        assertEquals(3, state.favorites[0].id)
        assertTrue(state.selectedIDs.isEmpty())
        assertFalse(state.isDeleteConfirmationShown)
        // 一括削除成功の確認通知が積まれている
        assertEquals(
            listOf(UiText.Resource(R.string.favorite_bulk_deleted, listOf(2))),
            viewModel.userMessages.value.map { it.text },
        )
    }

    @Test
    fun `デフォルトのソートはSAVED_AT_DESCで新しい順に並ぶ`() = runTest {
        // Arrange: savedAt が異なる 3 件
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(1, "a/old", null, 100, "Kotlin", null, 1_000L),
            FavoriteEntity(2, "b/mid", null, 200, "Kotlin", null, 2_000L),
            FavoriteEntity(3, "c/new", null, 300, "Kotlin", null, 3_000L),
        )
        val viewModel = createViewModel()

        // Act: ViewModel の購読が最初の emission を処理するまで進める
        advanceUntilIdle()

        // Assert: 新しい順 (id=3, 2, 1) で並ぶ
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(listOf(3, 2, 1), state.displayedFavorites.map { it.id })
        assertEquals(FavoriteSortKey.SAVED_AT_DESC, state.sortKey)
    }

    @Test
    fun `setSortKeyでSTAR_COUNT_DESCにするとStar多い順に並ぶ`() = runTest {
        // Arrange: starCount が異なる 3 件
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(1, "a/repo", null, 100, "Kotlin", null, 0L),
            FavoriteEntity(2, "b/repo", null, 5_000, "Kotlin", null, 0L),
            FavoriteEntity(3, "c/repo", null, 500, "Kotlin", null, 0L),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Act: setSortKey(STAR_COUNT_DESC) を実行する
        viewModel.setSortKey(FavoriteSortKey.STAR_COUNT_DESC)

        // Assert: Star 多い順 (5000, 500, 100)
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(listOf(5_000, 500, 100), state.displayedFavorites.map { it.starCount })
    }

    @Test
    fun `setSortKeyでFULL_NAME_ASCにすると名前昇順に並ぶ`() = runTest {
        // Arrange: 名前順でない 3 件（zoo / apple / microsoft）を読み込み済みにする
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(1, "zoo/repo", null, 0, null, null, 0L),
            FavoriteEntity(2, "apple/swift", null, 0, null, null, 0L),
            FavoriteEntity(3, "microsoft/vscode", null, 0, null, null, 0L),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Act: setSortKey(FULL_NAME_ASC) を実行する
        viewModel.setSortKey(FavoriteSortKey.FULL_NAME_ASC)

        // Assert: 名前昇順（apple, microsoft, zoo）に並ぶ
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(
            listOf("apple/swift", "microsoft/vscode", "zoo/repo"),
            state.displayedFavorites.map { it.fullName },
        )
    }

    @Test
    fun `setFilterLanguageで指定言語のみに絞り込まれる`() = runTest {
        // Arrange: Swift と Kotlin が混在
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(1, "a/swift1", null, 0, "Swift", null, 0L),
            FavoriteEntity(2, "b/kotlin1", null, 0, "Kotlin", null, 0L),
            FavoriteEntity(3, "c/swift2", null, 0, "Swift", null, 0L),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Act: setFilterLanguage("Swift") を実行する
        viewModel.setFilterLanguage("Swift")

        // Assert: 表示が Swift 2 件のみで filterLanguage が Swift になる
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(2, state.displayedFavorites.size)
        assertTrue(state.displayedFavorites.all { it.language == "Swift" })
        assertEquals("Swift", state.filterLanguage)
    }

    @Test
    fun `setFilterLanguage(null)でフィルタが解除される`() = runTest {
        // Arrange: フィルタ済み状態
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(1, "a/swift", null, 0, "Swift", null, 0L),
            FavoriteEntity(2, "b/kotlin", null, 0, "Kotlin", null, 0L),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setFilterLanguage("Swift")

        // Act: setFilterLanguage(null) を実行する
        viewModel.setFilterLanguage(null)

        // Assert: フィルタが解除され全 2 件が表示される
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(2, state.displayedFavorites.size)
        assertEquals(null, state.filterLanguage)
    }

    @Test
    fun `フィルタ対象の言語が実削除で消失したら自動でクリアされる`() = runTest {
        // Arrange: Swift にフィルタ
        val swiftFav = FavoriteEntity(1, "a/swift", null, 0, "Swift", null, 0L)
        fakeFavoriteRepository.stored = listOf(
            swiftFav,
            FavoriteEntity(2, "b/kotlin", null, 0, "Kotlin", null, 0L),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setFilterLanguage("Swift")

        // Act: Swift のお気に入りをスワイプ → 猶予経過で commit
        viewModel.removeFavorite(swiftFav)
        runCurrent()
        advanceTimeBy(UNDO_WINDOW_MS)
        runCurrent()

        // Assert: filterLanguage が自動クリアされ、Kotlin の 1 件が表示される
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(null, state.filterLanguage)
        assertEquals(1, state.displayedFavorites.size)
    }

    @Test
    fun `availableLanguagesが重複なくソート済みで返る`() = runTest {
        // Arrange: 重複あり、null あり
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(1, "a/repo", null, 0, "Swift", null, 0L),
            FavoriteEntity(2, "b/repo", null, 0, "Kotlin", null, 0L),
            FavoriteEntity(3, "c/repo", null, 0, "Swift", null, 0L),
            FavoriteEntity(4, "d/repo", null, 0, null, null, 0L),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Act: Loaded 状態の availableLanguages を取得する
        val state = viewModel.uiState.value as FavoriteUiState.Loaded

        // Assert: Swift の重複と null が除かれ Kotlin, Swift の昇順になる
        assertEquals(listOf("Kotlin", "Swift"), state.availableLanguages)
    }

    @Test
    fun `全件削除すると編集モードが自動終了しEmpty状態になる`() = runTest {
        // Arrange: 2件のお気に入りを全選択
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(1, "owner/a", null, 0, null, null, 0L),
            FavoriteEntity(2, "owner/b", null, 0, null, null, 0L),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.enterEditMode()
        viewModel.selectAll()

        // Act: 全件削除
        viewModel.confirmRemoveSelected()
        advanceUntilIdle()

        // Assert: Empty 状態に遷移（編集モードも当然終了）
        assertTrue(viewModel.uiState.value is FavoriteUiState.Empty)
    }

    @Test
    fun `Loaded後にRoomが更新されるとLoadingを経由せずに一覧が更新される`() = runTest {
        // Arrange: まずLoaded状態にする
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(
                id = 1,
                fullName = "owner/repo1",
                description = null,
                starCount = 10,
                language = null,
                ownerAvatarURL = null,
                savedAt = 0L,
            )
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is FavoriteUiState.Loaded)

        // Act: Room 側のデータが増える（load() なしで Fake の emission だけを流す）
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(
                id = 1,
                fullName = "owner/repo1",
                description = null,
                starCount = 10,
                language = null,
                ownerAvatarURL = null,
                savedAt = 0L,
            ),
            FavoriteEntity(
                id = 2,
                fullName = "owner/repo2",
                description = null,
                starCount = 5,
                language = null,
                ownerAvatarURL = null,
                savedAt = 0L,
            ),
        )
        advanceUntilIdle()

        // Assert: 2件に更新
        val state = viewModel.uiState.value
        assertTrue(state is FavoriteUiState.Loaded)
        assertEquals(2, (state as FavoriteUiState.Loaded).favorites.size)
    }

    @Test
    fun `一括削除後にdisplayedFavoritesとavailableLanguagesが再計算される`() = runTest {
        // Arrange: Swift 1件・Kotlin 2件を読み込み、Swift の1件だけを選択する
        fakeFavoriteRepository.stored = listOf(
            FavoriteEntity(1, "a/swift", null, 0, "Swift", null, 0L),
            FavoriteEntity(2, "b/kotlin", null, 0, "Kotlin", null, 0L),
            FavoriteEntity(3, "c/kotlin", null, 0, "Kotlin", null, 0L),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.enterEditMode()
        viewModel.toggleSelection(1)

        // Act: 選択中の Swift を一括削除する
        viewModel.requestRemoveSelected()
        viewModel.confirmRemoveSelected()
        advanceUntilIdle()

        // Assert: 事前計算済みの表示リストと言語チップから Swift が消えている
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(listOf(2, 3), state.displayedFavorites.map { it.id }.sorted())
        assertEquals(listOf("Kotlin"), state.availableLanguages)
    }

    @Test
    fun `Search 相当の store add だけでFavorite一覧が更新される（load不要）`() = runTest {
        // Arrange: 空の一覧を表示中（Empty）の ViewModel
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is FavoriteUiState.Empty)

        // Act: 検索画面のお気に入りトグルと同じ経路（FavoriteStore.add）で id=7 を追加する
        favoriteStore.add(
            com.example.githubfinder.core.model.RepositoryEntity(
                id = 7,
                fullName = "owner/added",
                description = null,
                starCount = 3,
                language = "Kotlin",
                ownerAvatarURL = null,
            )
        )
        advanceUntilIdle()

        // Assert: ViewModel の API を何も呼ばずに Loaded へ遷移し、追加した id=7 が表示されている
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(listOf(7), state.displayedFavorites.map { it.id })
    }

    @Test
    fun `猶予経過後のRoom emissionで削除済みの行が一度も再表示されない`() = runTest {
        // Arrange: 2件（id=1,2）を表示中で、uiState の全遷移を記録する
        val fav1 = FavoriteEntity(1, "owner/repo1", null, 10, null, null, 0L)
        val fav2 = FavoriteEntity(2, "owner/repo2", null, 5, null, null, 0L)
        fakeFavoriteRepository.stored = listOf(fav1, fav2)
        val viewModel = createViewModel()
        advanceUntilIdle()
        val displayedHistory = mutableListOf<List<Int>>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { state ->
                if (state is FavoriteUiState.Loaded) displayedHistory.add(state.displayedFavorites.map { it.id })
            }
        }

        // Act: id=1 をスワイプ削除し、猶予を経過させて Room から実削除（行なしの emission が流れる）
        viewModel.removeFavorite(fav1)
        runCurrent()
        advanceTimeBy(UNDO_WINDOW_MS)
        advanceUntilIdle()
        collector.cancel()

        // Assert: 表示は [1,2]（スワイプ前）→ [2] の 1 遷移だけで、id=1 を含む表示はスワイプ前の 1 回きり（再表示のちらつきなし）
        assertEquals(listOf(listOf(1, 2), listOf(2)), displayedHistory.distinct())
        assertEquals(1, displayedHistory.count { 1 in it })
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertTrue(state.pendingDeleteIDs.isEmpty())
        assertEquals(listOf(2), state.favorites.map { it.id })
    }

    @Test
    fun `refreshはisRefreshingを閉じて一覧を維持する`() = runTest {
        // Arrange: 1件を表示中
        fakeFavoriteRepository.stored = listOf(FavoriteEntity(1, "owner/repo1", null, 10, null, null, 0L))
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Act: プル・トゥ・リフレッシュ
        viewModel.refresh()
        advanceUntilIdle()

        // Assert: スピナーは閉じ、一覧は同じ 1 件のまま
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertFalse(state.isRefreshing)
        assertEquals(listOf(1), state.displayedFavorites.map { it.id })
    }

}
