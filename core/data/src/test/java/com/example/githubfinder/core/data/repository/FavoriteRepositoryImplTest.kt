package com.example.githubfinder.core.data.repository

import com.example.githubfinder.core.data.FakeFavoriteDao
import com.example.githubfinder.core.data.makeFavoriteLocalDataSource
import com.example.githubfinder.core.data.makeRepositoryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteRepositoryImplTest {

    @Test
    fun `add後にgetAllで取得できる`() = runTest {
        // Arrange: 空の Repository
        val repository = makeRepository()

        // Act: id=1 を add 後に getAll
        repository.add(makeRepositoryEntity(id = 1))
        val all = repository.getAll()

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
        val all = repository.getAll()

        // Assert: id=2 だけ残る
        assertEquals(1, all.size)
        assertEquals(2, all[0].id)
    }

    @Test
    fun `containsは存在チェックを正しく返す`() = runTest {
        // Arrange: id=42 を追加
        val repository = makeRepository()
        repository.add(makeRepositoryEntity(id = 42))

        // Act & Assert: 存在/未存在を正しく返す
        assertTrue(repository.contains(42))
        assertFalse(repository.contains(99))
    }

    @Test
    fun `allFavoriteIDsは全IDをSetとして返す`() = runTest {
        // Arrange: id=1, 2, 3 を追加
        val repository = makeRepository()
        repository.add(makeRepositoryEntity(id = 1))
        repository.add(makeRepositoryEntity(id = 2))
        repository.add(makeRepositoryEntity(id = 3))

        // Act: allFavoriteIDs を取得
        val ids = repository.allFavoriteIDs()

        // Assert: 追加した3つの ID が Set として返る
        assertEquals(setOf(1, 2, 3), ids)
    }

    private fun makeRepository(): FavoriteRepositoryImpl =
        FavoriteRepositoryImpl(makeFavoriteLocalDataSource(FakeFavoriteDao()))
}
