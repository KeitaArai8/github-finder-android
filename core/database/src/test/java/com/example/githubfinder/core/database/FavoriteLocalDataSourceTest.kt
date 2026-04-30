package com.example.githubfinder.core.database

import com.example.githubfinder.core.model.RepositoryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteLocalDataSourceTest {

    @Test
    fun `空の時getAllは空配列を返す`() = runTest {
        // Arrange: 何も追加していない DataSource
        val dataSource = makeDataSource()

        // Act: getAll
        val all = dataSource.getAll()

        // Assert: 空
        assertTrue(all.isEmpty())
    }

    @Test
    fun `add後にgetAllで取得できる`() = runTest {
        // Arrange: 空の DataSource
        val dataSource = makeDataSource()

        // Act: id=1 を add 後に getAll
        dataSource.add(makeRepositoryEntity(id = 1, fullName = "owner/repo"))
        val all = dataSource.getAll()

        // Assert: 追加した1件が取得でき、内容も正しい
        assertEquals(1, all.size)
        assertEquals(1, all[0].id)
        assertEquals("owner/repo", all[0].fullName)
    }

    @Test
    fun `複数件add後はsavedAt降順で取得される`() = runTest {
        // Arrange: 明示的な savedAt（1000/2000/3000）で 3件を直接挿入
        val dao = FakeFavoriteDao()
        val dataSource = FavoriteLocalDataSource(dao)
        dao.insertWithSavedAt(makeDbEntity(1), savedAt = 1000L)
        dao.insertWithSavedAt(makeDbEntity(2), savedAt = 2000L)
        dao.insertWithSavedAt(makeDbEntity(3), savedAt = 3000L)

        // Act: getAll
        val all = dataSource.getAll()

        // Assert: savedAt 降順（3, 2, 1）で取得される
        assertEquals(listOf(3, 2, 1), all.map { it.id })
    }

    @Test
    fun `containsは存在するIDでtrueを返す`() = runTest {
        // Arrange: id=42 を追加
        val dataSource = makeDataSource()
        dataSource.add(makeRepositoryEntity(id = 42))

        // Act & Assert: 存在するので true
        assertTrue(dataSource.contains(42))
    }

    @Test
    fun `containsは存在しないIDでfalseを返す`() = runTest {
        // Arrange: 何も追加していない DataSource
        val dataSource = makeDataSource()

        // Act & Assert: 存在しないので false
        assertFalse(dataSource.contains(999))
    }

    @Test
    fun `removeで該当IDが削除される`() = runTest {
        // Arrange: 2件追加済み
        val dataSource = makeDataSource()
        dataSource.add(makeRepositoryEntity(id = 1))
        dataSource.add(makeRepositoryEntity(id = 2))

        // Act: id=1 を削除
        dataSource.remove(1)
        val all = dataSource.getAll()

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
        val all = dataSource.getAll()

        // Assert: 例外なく空のまま
        assertTrue(all.isEmpty())
    }

    @Test
    fun `allIDsは全IDをSetとして返す`() = runTest {
        // Arrange: id=1, 2, 3 を追加
        val dataSource = makeDataSource()
        dataSource.add(makeRepositoryEntity(id = 1))
        dataSource.add(makeRepositoryEntity(id = 2))
        dataSource.add(makeRepositoryEntity(id = 3))

        // Act: allIDs を取得
        val ids = dataSource.allIDs()

        // Assert: 3つの ID が Set として返る
        assertEquals(setOf(1, 2, 3), ids)
    }

    private fun makeDataSource(): FavoriteLocalDataSource =
        FavoriteLocalDataSource(FakeFavoriteDao())

    private fun makeRepositoryEntity(id: Int, fullName: String = "owner/repo$id"): RepositoryEntity =
        RepositoryEntity(
            id = id,
            fullName = fullName,
            description = null,
            starCount = 0,
            language = null,
            ownerAvatarURL = null,
        )

    private fun makeDbEntity(id: Int): FavoriteDbEntity = FavoriteDbEntity(
        repositoryID = id,
        fullName = "owner/repo$id",
        description = null,
        starCount = 0,
        language = null,
        ownerAvatarURL = null,
    )

}
