package com.example.githubfinder.core.data.repository

import com.example.githubfinder.core.data.FakeFavoriteDao
import com.example.githubfinder.core.data.makeFavoriteLocalDataSource
import com.example.githubfinder.core.data.makeRepositoryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteRepositoryImplTest {

    @Test
    fun `add後にfavoritesで取得できる`() = runTest {
        // Arrange: 空の Repository
        val repository = makeRepository()

        // Act: id=1 を add 後に favorites を取る
        repository.add(makeRepositoryEntity(id = 1))
        val all = repository.favorites.first()

        // Assert: 追加した1件が取得できる
        assertEquals(1, all.size)
        assertEquals(1, all[0].id)
    }

    @Test
    fun `removeで削除される`() = runTest {
        // Arrange: 2件追加済みの Repository
        val repository = makeRepository()
        repository.add(makeRepositoryEntity(id = 1))
        repository.add(makeRepositoryEntity(id = 2))

        // Act: id=1 を削除
        repository.remove(1)
        val all = repository.favorites.first()

        // Assert: id=2 だけ残る
        assertEquals(1, all.size)
        assertEquals(2, all[0].id)
    }

    private fun makeRepository(): FavoriteRepositoryImpl = FavoriteRepositoryImpl(makeFavoriteLocalDataSource(FakeFavoriteDao()))
}
