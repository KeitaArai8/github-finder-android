package com.example.githubfinder.core.database

import com.example.githubfinder.core.testing.FakeFavoriteDao
import com.example.githubfinder.core.testing.testFavoriteDb
import com.example.githubfinder.core.testing.testRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteLocalDataSourceTest {

    @Test
    fun `空の時favoritesは空配列を流す`() = runTest {
        // Arrange: 何も追加していない DataSource
        val dataSource = makeDataSource()

        // Act: favorites の最初の emission を取る
        val all = dataSource.favorites.first()

        // Assert: 空
        assertTrue(all.isEmpty())
    }

    @Test
    fun `add後にfavoritesで取得できる`() = runTest {
        // Arrange: 空の DataSource
        val dataSource = makeDataSource()

        // Act: id=1 / fullName="owner/repo" を add 後に favorites を取る
        dataSource.add(testRepository(id = 1, fullName = "owner/repo"))
        val all = dataSource.favorites.first()

        // Assert: 追加した1件が取得でき、内容も正しい
        assertEquals(1, all.size)
        assertEquals(1, all[0].id)
        assertEquals("owner/repo", all[0].fullName)
    }

    @Test
    fun `複数件add後はsavedAt降順で流れる`() = runTest {
        // Arrange: 明示的な savedAt（1000/2000/3000）で 3件を直接挿入
        val dao = FakeFavoriteDao()
        val dataSource = FavoriteLocalDataSource(dao)
        dao.insertWithSavedAt(testFavoriteDb(1), savedAt = 1000L)
        dao.insertWithSavedAt(testFavoriteDb(2), savedAt = 2000L)
        dao.insertWithSavedAt(testFavoriteDb(3), savedAt = 3000L)

        // Act: favorites を取る
        val all = dataSource.favorites.first()

        // Assert: savedAt 降順（3, 2, 1）で流れる
        assertEquals(listOf(3, 2, 1), all.map { it.id })
    }

    @Test
    fun `購読中にadd・removeするとfavoritesが更新のたびに再送される`() = runTest {
        // Arrange: 空の DataSource を購読して emission を集める
        val dataSource = makeDataSource()
        val emissions = mutableListOf<List<Int>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            dataSource.favorites.collect { list -> emissions.add(list.map { it.id }) }
        }

        // Act: id=1, id=2 を add してから id=1 を remove
        dataSource.add(testRepository(id = 1))
        dataSource.add(testRepository(id = 2))
        dataSource.remove(1)
        job.cancel()

        // Assert: 空 → [1] → [2, 1]（savedAt 降順）→ [2] の順に流れた
        assertEquals(listOf(emptyList(), listOf(1), listOf(2, 1), listOf(2)), emissions)
    }

    @Test
    fun `removeで該当IDが削除される`() = runTest {
        // Arrange: 2件追加済み
        val dataSource = makeDataSource()
        dataSource.add(testRepository(id = 1))
        dataSource.add(testRepository(id = 2))

        // Act: id=1 を削除
        dataSource.remove(1)
        val all = dataSource.favorites.first()

        // Assert: id=2 だけ残る
        assertEquals(1, all.size)
        assertEquals(2, all[0].id)
    }

    @Test
    fun `存在しないIDのremoveは例外を投げない`() = runTest {
        // Arrange: 空の DataSource
        val dataSource = makeDataSource()

        // Act: 存在しない id=999 を削除
        dataSource.remove(999)
        val all = dataSource.favorites.first()

        // Assert: 例外なく空のまま
        assertTrue(all.isEmpty())
    }

    private fun makeDataSource(): FavoriteLocalDataSource = FavoriteLocalDataSource(FakeFavoriteDao())
}
