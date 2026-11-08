package com.example.githubfinder.feature.widget

import com.example.githubfinder.core.data.FavoriteStore
import com.example.githubfinder.core.data.repository.FavoriteRepository
import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteWidgetUpdaterTest {

    private class FakeRepository : FavoriteRepository {
        val flow = MutableStateFlow<List<FavoriteEntity>>(emptyList())
        override val favorites: Flow<List<FavoriteEntity>> = flow
        override suspend fun add(repository: RepositoryEntity) = Unit
        override suspend fun remove(id: Int) = Unit
    }

    @Test
    fun `お気に入りが変わるたびにウィジェットが更新される`() = runTest {
        // Arrange: 空のお気に入りを持つ Store と、更新回数を数える updater を購読開始する
        val repository = FakeRepository()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val store = FavoriteStore(repository, scope)
        var updateCount = 0
        FavoriteWidgetUpdater(store, scope) { updateCount++ }.start()
        // 起動直後の現在値（空）でも 1 回更新される（updateAll は冪等なので drop しない）
        assertEquals(1, updateCount)

        // Act: Room 相当の emission で id=1 のお気に入りが追加される
        repository.flow.value = listOf(FavoriteEntity(1, "owner/repo", null, 1, null, null, 0L))

        // Assert: 変更を受けてもう 1 回（合計 2 回）更新された
        assertEquals(2, updateCount)
        scope.cancel()
    }

    @Test
    fun `更新が失敗しても購読は続き次の変更で再び更新される`() = runTest {
        // Arrange: 1 回目の更新だけ例外を投げる updater
        val repository = FakeRepository()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val store = FavoriteStore(repository, scope)
        var updateCount = 0
        FavoriteWidgetUpdater(store, scope) {
            updateCount++
            if (updateCount == 1) error("boom")
        }.start()

        // Act: 初回更新の失敗後に、お気に入りが変わる
        repository.flow.value = listOf(FavoriteEntity(1, "owner/repo", null, 1, null, null, 0L))

        // Assert: 購読が止まらず 2 回目の更新が走る
        assertEquals(2, updateCount)
        scope.cancel()
    }
}
