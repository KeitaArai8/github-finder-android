package com.example.githubfinder.core.data

import com.example.githubfinder.core.data.repository.FavoriteRepository
import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteStoreTest {

    private class StubRepository(override val favorites: Flow<List<FavoriteEntity>>) : FavoriteRepository {
        override suspend fun add(repository: RepositoryEntity) = Unit
        override suspend fun remove(id: Int) = Unit
    }

    private fun favorite(id: Int) = FavoriteEntity(id, "owner/repo$id", null, 1, null, null, 0L)

    @Test
    fun `Roomの最初のemissionが届くまでfavoritesはnullでfavoriteIDsは空集合`() = runTest {
        // Arrange: まだ何も emit していない Flow を持つ Repository
        val source = MutableSharedFlow<List<FavoriteEntity>>()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val store = FavoriteStore(StubRepository(source), scope)

        // Assert: 読み込み中は null（空リストと区別できる）、ID 集合は空
        assertNull(store.favorites.value)
        assertEquals(emptySet<Int>(), store.favoriteIDs.value)

        // Act: 最初の emission として id=3 のお気に入りが届く
        source.emit(listOf(favorite(3)))

        // Assert: null が解消され、ID 集合にも反映される
        assertEquals(listOf(3), store.favorites.value?.map { it.id })
        assertEquals(setOf(3), store.favoriteIDs.value)
        scope.cancel()
    }

    @Test
    fun `Roomの購読が例外で失敗しても落ちずに空リストへ倒れる`() = runTest {
        // Arrange: collect 開始時に例外を投げる Flow
        val failing = flow<List<FavoriteEntity>> { throw IllegalStateException("room failure") }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

        // Act: ストアを作る（Eagerly なので即購読される。catch が無ければ scope が例外で落ちる）
        val store = FavoriteStore(StubRepository(failing), scope)

        // Assert: 例外は伝播せず、favorites は空リスト（null ではない）になる
        assertEquals(emptyList<FavoriteEntity>(), store.favorites.value)
        assertEquals(emptySet<Int>(), store.favoriteIDs.value)
        scope.cancel()
    }
}
