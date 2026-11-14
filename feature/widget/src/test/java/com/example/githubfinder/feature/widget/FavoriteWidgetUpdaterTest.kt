package com.example.githubfinder.feature.widget

import com.example.githubfinder.core.data.FavoriteStore
import com.example.githubfinder.core.testing.FakeFavoriteRepository
import com.example.githubfinder.core.testing.testFavorite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteWidgetUpdaterTest {

    // テスト中に作った購読用スコープ。assert が失敗しても確実に cancel して購読を残さないよう @After で閉じる
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    private fun TestScope.newScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)).also { scopes += it }

    private val addedFavorite = testFavorite(id = 1, starCount = 1)

    @Test
    fun `お気に入りが変わるたびにウィジェットが更新される`() = runTest {
        // Arrange: 空のお気に入りを持つ Store と、更新回数を数える updater を購読開始する
        val repository = FakeFavoriteRepository()
        val scope = newScope()
        var updateCount = 0
        FavoriteWidgetUpdater(FavoriteStore(repository, scope), scope) { updateCount++ }.start()
        // 起動直後の現在値（空）でも 1 回更新される（updateAll は冪等なので drop しない）
        assertEquals(1, updateCount)

        // Act: Room 相当の emission で id=1（owner/repo）のお気に入りが追加される
        repository.stored = listOf(addedFavorite)

        // Assert: 変更を受けてもう 1 回（合計 2 回）更新された
        assertEquals(2, updateCount)
    }

    @Test
    fun `更新が失敗しても購読は続き次の変更で再び更新される`() = runTest {
        // Arrange: 1 回目の更新だけ IllegalStateException("boom") を投げる updater
        val repository = FakeFavoriteRepository()
        val scope = newScope()
        var updateCount = 0
        FavoriteWidgetUpdater(FavoriteStore(repository, scope), scope) {
            updateCount++
            if (updateCount == 1) error("boom")
        }.start()

        // Act: 初回更新の失敗後に、お気に入り owner/repo が追加される
        repository.stored = listOf(addedFavorite)

        // Assert: 購読が止まらず 2 回目の更新が走る
        assertEquals(2, updateCount)
    }
}
