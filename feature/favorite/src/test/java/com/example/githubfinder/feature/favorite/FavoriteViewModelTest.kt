package com.example.githubfinder.feature.favorite

import app.cash.turbine.test
import com.example.githubfinder.core.data.FavoriteStore
import com.example.githubfinder.core.model.FavoriteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeFavoriteRepository: FakeFavoriteRepository
    private lateinit var favoriteStore: FavoriteStore

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeFavoriteRepository = FakeFavoriteRepository()
        favoriteStore = FavoriteStore(fakeFavoriteRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `初期状態はIdleである`() = runTest {
        val viewModel = FavoriteViewModel(favoriteStore)
        assertTrue(viewModel.uiState.value is FavoriteUiState.Idle)
    }

    @Test
    fun `お気に入りがある場合Loaded状態になる`() = runTest {
        // Arrange:
        fakeFavoriteRepository.favorites = mutableListOf(
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
        val viewModel = FavoriteViewModel(favoriteStore)

        // Act:
        viewModel.load()
        advanceUntilIdle()

        // Assert:
        val state = viewModel.uiState.value
        assertTrue(state is FavoriteUiState.Loaded)
        assertEquals(1, (state as FavoriteUiState.Loaded).favorites.size)
    }

    @Test
    fun `お気に入りが空の場合Empty状態になる`() = runTest {
        // Arrange:
        fakeFavoriteRepository.favorites = mutableListOf()
        val viewModel = FavoriteViewModel(favoriteStore)

        // Act:
        viewModel.load()
        advanceUntilIdle()

        // Assert:
        assertTrue(viewModel.uiState.value is FavoriteUiState.Empty)
    }

    @Test
    fun `removeFavoriteは即座にpendingDeleteIDsへ追加されdisplayedからは消えるが favorites本体は残る（Gmail スタイル楽観的更新）`() = runTest {
        // Arrange:
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
        fakeFavoriteRepository.favorites = mutableListOf(fav1, fav2)
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
        advanceUntilIdle()

        // Act: スワイプ削除（commit はまだ）
        viewModel.removeFavorite(fav1)
        advanceUntilIdle()

        // Assert: UI（displayedFavorites）からは消えるが、Room は触っていない（commit 待ち）
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(setOf(1), state.pendingDeleteIDs)
        assertEquals(2, state.favorites.size) // 本体は残る
        assertEquals(listOf(2), state.displayedFavorites.map { it.id }) // 表示からは消える
        assertEquals(0, fakeFavoriteRepository.removeCallCount) // Room 未到達
    }

    @Test
    fun `commitDeleteでRoomから実削除されpendingがクリアされfavorites本体からも消える`() = runTest {
        // Arrange:
        val fav1 = FavoriteEntity(1, "owner/repo1", null, 10, null, null, 0L)
        val fav2 = FavoriteEntity(2, "owner/repo2", null, 5, null, null, 0L)
        fakeFavoriteRepository.favorites = mutableListOf(fav1, fav2)
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
        advanceUntilIdle()
        viewModel.removeFavorite(fav1)
        advanceUntilIdle()

        // Act: SnackBar timeout 相当 → 実削除
        viewModel.commitDelete(fav1.id)
        advanceUntilIdle()

        // Assert: Room から削除され、pendingDeleteIDs もクリアされる
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertTrue(state.pendingDeleteIDs.isEmpty())
        assertEquals(1, state.favorites.size)
        assertEquals(2, state.favorites[0].id)
        assertEquals(1, fakeFavoriteRepository.removeCallCount)
    }

    @Test
    fun `undoDeleteでpendingから外れUIに行が復元される（Roomは触らない）`() = runTest {
        // Arrange:
        val fav = FavoriteEntity(1, "owner/repo", null, 10, null, null, 0L)
        fakeFavoriteRepository.favorites = mutableListOf(fav)
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
        advanceUntilIdle()
        viewModel.removeFavorite(fav)
        advanceUntilIdle()

        // Act: 「元に戻す」アクション
        viewModel.undoDelete(fav.id)

        // Assert: pending クリア、Room には commit していないので removeCallCount = 0
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertTrue(state.pendingDeleteIDs.isEmpty())
        assertEquals(1, state.displayedFavorites.size)
        assertEquals(0, fakeFavoriteRepository.removeCallCount)
    }

    @Test
    fun `最後の1件をcommitDeleteするとEmpty状態になる`() = runTest {
        // Arrange:
        val fav = FavoriteEntity(
            id = 1,
            fullName = "owner/repo",
            description = null,
            starCount = 10,
            language = null,
            ownerAvatarURL = null,
            savedAt = 0L,
        )
        fakeFavoriteRepository.favorites = mutableListOf(fav)
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
        advanceUntilIdle()

        // Act: スワイプ → commit
        viewModel.removeFavorite(fav)
        advanceUntilIdle()
        viewModel.commitDelete(fav.id)
        advanceUntilIdle()

        // Assert:
        assertTrue(viewModel.uiState.value is FavoriteUiState.Empty)
    }

    @Test
    fun `removeFavoriteでShowUndoDeleteイベントが発火する`() = runTest {
        // Arrange:
        val fav = FavoriteEntity(42, "owner/repo", null, 10, null, null, 0L)
        fakeFavoriteRepository.favorites = mutableListOf(fav)
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
        advanceUntilIdle()

        // Act + Assert:
        viewModel.events.test {
            viewModel.removeFavorite(fav)
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is FavoriteUiEvent.ShowUndoDelete)
            val undoEvent = event as FavoriteUiEvent.ShowUndoDelete
            assertEquals(42, undoEvent.deletedID)
            assertTrue(undoEvent.message.contains("owner/repo"))
        }
    }

    @Test
    fun `読み込み失敗時にError状態になる`() = runTest {
        // Arrange:
        fakeFavoriteRepository.shouldThrowOnGetAll = true
        val viewModel = FavoriteViewModel(favoriteStore)

        // Act:
        viewModel.load()
        advanceUntilIdle()

        // Assert:
        assertTrue(viewModel.uiState.value is FavoriteUiState.Error)
    }

    @Test
    fun `commitDelete失敗時にpendingが戻されShowSnackBarイベントが発火する`() = runTest {
        // Arrange: 一旦スワイプ削除を発火（pending に追加）
        val fav = FavoriteEntity(
            id = 1,
            fullName = "owner/repo",
            description = null,
            starCount = 10,
            language = null,
            ownerAvatarURL = null,
            savedAt = 0L,
        )
        fakeFavoriteRepository.favorites = mutableListOf(fav)
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
        advanceUntilIdle()

        // Act + Assert:
        viewModel.events.test {
            // スワイプ削除イベント（ShowUndoDelete）を消化
            viewModel.removeFavorite(fav)
            advanceUntilIdle()
            assertTrue(awaitItem() is FavoriteUiEvent.ShowUndoDelete)

            // commit が失敗するように設定 → commit 実行
            fakeFavoriteRepository.shouldThrowOnRemove = true
            viewModel.commitDelete(fav.id)
            advanceUntilIdle()

            // 失敗時は pending から戻り、エラー SnackBar が出る
            val errorEvent = awaitItem()
            assertTrue(errorEvent is FavoriteUiEvent.ShowSnackBar)
            assertEquals("お気に入りの削除に失敗しました", (errorEvent as FavoriteUiEvent.ShowSnackBar).message)

            val state = viewModel.uiState.value as FavoriteUiState.Loaded
            assertTrue(state.pendingDeleteIDs.isEmpty())
            // 行が UI に復元される
            assertEquals(1, state.displayedFavorites.size)
        }
    }

    @Test
    fun `読み込み失敗時にAppErrorのメッセージがError状態に反映される`() = runTest {
        // Arrange:
        fakeFavoriteRepository.getAllError = com.example.githubfinder.core.model.AppError.Network
        val viewModel = FavoriteViewModel(favoriteStore)

        // Act:
        viewModel.load()
        advanceUntilIdle()

        // Assert:
        val state = viewModel.uiState.value
        assertTrue(state is FavoriteUiState.Error)
        assertTrue((state as FavoriteUiState.Error).message.contains("ネットワーク"))
    }

    @Test
    fun `enterEditModeでisEditModeがtrueになる`() = runTest {
        // Arrange: Loaded状態にする
        fakeFavoriteRepository.favorites = mutableListOf(
            FavoriteEntity(1, "owner/repo", null, 10, null, null, 0L),
        )
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
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
        fakeFavoriteRepository.favorites = mutableListOf(
            FavoriteEntity(1, "owner/repo", null, 10, null, null, 0L),
        )
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
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
        fakeFavoriteRepository.favorites = mutableListOf(
            FavoriteEntity(42, "owner/repo", null, 10, null, null, 0L),
        )
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
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
        fakeFavoriteRepository.favorites = mutableListOf(
            FavoriteEntity(1, "owner/a", null, 0, null, null, 0L),
            FavoriteEntity(2, "owner/b", null, 0, null, null, 0L),
            FavoriteEntity(3, "owner/c", null, 0, null, null, 0L),
        )
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
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
        fakeFavoriteRepository.favorites = mutableListOf(
            FavoriteEntity(1, "a/swift1", null, 0, "Swift", null, 0L),
            FavoriteEntity(2, "b/kotlin1", null, 0, "Kotlin", null, 0L),
            FavoriteEntity(3, "c/swift2", null, 0, "Swift", null, 0L),
        )
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
        advanceUntilIdle()
        viewModel.enterEditMode()
        viewModel.setFilterLanguage("Swift")

        // Act:
        viewModel.selectAll()

        // Assert: 表示中の Swift 2 件のみが選択され、非表示の Kotlin は含まれない
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(setOf(1, 3), state.selectedIDs)
    }

    @Test
    fun `toggleSelectAllは全未選択から全選択へトグルする`() = runTest {
        // Arrange: 編集モード、未選択状態
        fakeFavoriteRepository.favorites = mutableListOf(
            FavoriteEntity(1, "owner/a", null, 0, null, null, 0L),
            FavoriteEntity(2, "owner/b", null, 0, null, null, 0L),
        )
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
        advanceUntilIdle()
        viewModel.enterEditMode()

        // Act:
        viewModel.toggleSelectAll()

        // Assert: 全選択 + isAllSelected が true
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(setOf(1, 2), state.selectedIDs)
        assertTrue(state.isAllSelected)
    }

    @Test
    fun `toggleSelectAllは全選択状態から全解除へトグルする`() = runTest {
        // Arrange: 全選択済み状態
        fakeFavoriteRepository.favorites = mutableListOf(
            FavoriteEntity(1, "owner/a", null, 0, null, null, 0L),
            FavoriteEntity(2, "owner/b", null, 0, null, null, 0L),
        )
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
        advanceUntilIdle()
        viewModel.enterEditMode()
        viewModel.selectAll()
        assertTrue((viewModel.uiState.value as FavoriteUiState.Loaded).isAllSelected)

        // Act:
        viewModel.toggleSelectAll()

        // Assert: 全解除 + isAllSelected が false
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertTrue(state.selectedIDs.isEmpty())
        assertFalse(state.isAllSelected)
    }

    @Test
    fun `toggleSelectAllは部分選択状態から全選択へトグルする（iOS Photos 同等の挙動）`() = runTest {
        // Arrange: 3件中1件のみ選択
        fakeFavoriteRepository.favorites = mutableListOf(
            FavoriteEntity(1, "owner/a", null, 0, null, null, 0L),
            FavoriteEntity(2, "owner/b", null, 0, null, null, 0L),
            FavoriteEntity(3, "owner/c", null, 0, null, null, 0L),
        )
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
        advanceUntilIdle()
        viewModel.enterEditMode()
        viewModel.toggleSelection(2)

        // Act: 部分選択中のトグル → iOS Photos.app では「全選択」扱い
        viewModel.toggleSelectAll()

        // Assert:
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(setOf(1, 2, 3), state.selectedIDs)
        assertTrue(state.isAllSelected)
    }

    @Test
    fun `requestRemoveSelectedで選択ゼロ件なら確認ダイアログが出ない`() = runTest {
        // Arrange: 編集モードで選択なし
        fakeFavoriteRepository.favorites = mutableListOf(
            FavoriteEntity(1, "owner/repo", null, 0, null, null, 0L),
        )
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
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
        fakeFavoriteRepository.favorites = initial
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
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
    }

    @Test
    fun `デフォルトのソートはSAVED_AT_DESCで新しい順に並ぶ`() = runTest {
        // Arrange: savedAt が異なる 3 件
        fakeFavoriteRepository.favorites = mutableListOf(
            FavoriteEntity(1, "a/old", null, 100, "Kotlin", null, 1_000L),
            FavoriteEntity(2, "b/mid", null, 200, "Kotlin", null, 2_000L),
            FavoriteEntity(3, "c/new", null, 300, "Kotlin", null, 3_000L),
        )
        val viewModel = FavoriteViewModel(favoriteStore)

        // Act:
        viewModel.load()
        advanceUntilIdle()

        // Assert: 新しい順 (id=3, 2, 1) で並ぶ
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(listOf(3, 2, 1), state.displayedFavorites.map { it.id })
        assertEquals(FavoriteSortKey.SAVED_AT_DESC, state.sortKey)
    }

    @Test
    fun `setSortKeyでSTAR_COUNT_DESCにするとStar多い順に並ぶ`() = runTest {
        // Arrange: starCount が異なる 3 件
        fakeFavoriteRepository.favorites = mutableListOf(
            FavoriteEntity(1, "a/repo", null, 100, "Kotlin", null, 0L),
            FavoriteEntity(2, "b/repo", null, 5_000, "Kotlin", null, 0L),
            FavoriteEntity(3, "c/repo", null, 500, "Kotlin", null, 0L),
        )
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
        advanceUntilIdle()

        // Act:
        viewModel.setSortKey(FavoriteSortKey.STAR_COUNT_DESC)

        // Assert: Star 多い順 (5000, 500, 100)
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(listOf(5_000, 500, 100), state.displayedFavorites.map { it.starCount })
    }

    @Test
    fun `setSortKeyでFULL_NAME_ASCにすると名前昇順に並ぶ`() = runTest {
        // Arrange:
        fakeFavoriteRepository.favorites = mutableListOf(
            FavoriteEntity(1, "zoo/repo", null, 0, null, null, 0L),
            FavoriteEntity(2, "apple/swift", null, 0, null, null, 0L),
            FavoriteEntity(3, "microsoft/vscode", null, 0, null, null, 0L),
        )
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
        advanceUntilIdle()

        // Act:
        viewModel.setSortKey(FavoriteSortKey.FULL_NAME_ASC)

        // Assert:
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(
            listOf("apple/swift", "microsoft/vscode", "zoo/repo"),
            state.displayedFavorites.map { it.fullName },
        )
    }

    @Test
    fun `setFilterLanguageで指定言語のみに絞り込まれる`() = runTest {
        // Arrange: Swift と Kotlin が混在
        fakeFavoriteRepository.favorites = mutableListOf(
            FavoriteEntity(1, "a/swift1", null, 0, "Swift", null, 0L),
            FavoriteEntity(2, "b/kotlin1", null, 0, "Kotlin", null, 0L),
            FavoriteEntity(3, "c/swift2", null, 0, "Swift", null, 0L),
        )
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
        advanceUntilIdle()

        // Act:
        viewModel.setFilterLanguage("Swift")

        // Assert:
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(2, state.displayedFavorites.size)
        assertTrue(state.displayedFavorites.all { it.language == "Swift" })
        assertEquals("Swift", state.filterLanguage)
    }

    @Test
    fun `setFilterLanguage(null)でフィルタが解除される`() = runTest {
        // Arrange: フィルタ済み状態
        fakeFavoriteRepository.favorites = mutableListOf(
            FavoriteEntity(1, "a/swift", null, 0, "Swift", null, 0L),
            FavoriteEntity(2, "b/kotlin", null, 0, "Kotlin", null, 0L),
        )
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
        advanceUntilIdle()
        viewModel.setFilterLanguage("Swift")

        // Act:
        viewModel.setFilterLanguage(null)

        // Assert:
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(2, state.displayedFavorites.size)
        assertEquals(null, state.filterLanguage)
    }

    @Test
    fun `フィルタ対象の言語がcommitDeleteで消失したら自動でクリアされる`() = runTest {
        // Arrange: Swift にフィルタ
        val swiftFav = FavoriteEntity(1, "a/swift", null, 0, "Swift", null, 0L)
        fakeFavoriteRepository.favorites = mutableListOf(
            swiftFav,
            FavoriteEntity(2, "b/kotlin", null, 0, "Kotlin", null, 0L),
        )
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
        advanceUntilIdle()
        viewModel.setFilterLanguage("Swift")

        // Act: Swift のお気に入りをスワイプ → commit
        viewModel.removeFavorite(swiftFav)
        advanceUntilIdle()
        viewModel.commitDelete(swiftFav.id)
        advanceUntilIdle()

        // Assert: filterLanguage が自動クリアされ、Kotlin の 1 件が表示される
        val state = viewModel.uiState.value as FavoriteUiState.Loaded
        assertEquals(null, state.filterLanguage)
        assertEquals(1, state.displayedFavorites.size)
    }

    @Test
    fun `availableLanguagesが重複なくソート済みで返る`() = runTest {
        // Arrange: 重複あり、null あり
        fakeFavoriteRepository.favorites = mutableListOf(
            FavoriteEntity(1, "a/repo", null, 0, "Swift", null, 0L),
            FavoriteEntity(2, "b/repo", null, 0, "Kotlin", null, 0L),
            FavoriteEntity(3, "c/repo", null, 0, "Swift", null, 0L),
            FavoriteEntity(4, "d/repo", null, 0, null, null, 0L),
        )
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
        advanceUntilIdle()

        // Act:
        val state = viewModel.uiState.value as FavoriteUiState.Loaded

        // Assert:
        assertEquals(listOf("Kotlin", "Swift"), state.availableLanguages)
    }

    @Test
    fun `全件削除すると編集モードが自動終了しEmpty状態になる`() = runTest {
        // Arrange: 2件のお気に入りを全選択
        fakeFavoriteRepository.favorites = mutableListOf(
            FavoriteEntity(1, "owner/a", null, 0, null, null, 0L),
            FavoriteEntity(2, "owner/b", null, 0, null, null, 0L),
        )
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
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
    fun `Loaded状態からloadを呼ぶとLoading状態を経由せずに結果が更新される`() = runTest {
        // Arrange: まずLoaded状態にする
        fakeFavoriteRepository.favorites = mutableListOf(
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
        val viewModel = FavoriteViewModel(favoriteStore)
        viewModel.load()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is FavoriteUiState.Loaded)

        // Act: データを変えてリロード
        fakeFavoriteRepository.favorites = mutableListOf(
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
        viewModel.load()
        advanceUntilIdle()

        // Assert: 2件に更新
        val state = viewModel.uiState.value
        assertTrue(state is FavoriteUiState.Loaded)
        assertEquals(2, (state as FavoriteUiState.Loaded).favorites.size)
    }

}
