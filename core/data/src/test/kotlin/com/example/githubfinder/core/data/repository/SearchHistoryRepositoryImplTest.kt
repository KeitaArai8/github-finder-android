package com.example.githubfinder.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.example.githubfinder.core.datastore.SearchHistoryLocalDataSource
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SearchHistoryRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun TestScope.makeDataSource(): SearchHistoryLocalDataSource {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            produceFile = { File(tempFolder.root, "search_history.preferences_pb") },
        )
        return SearchHistoryLocalDataSource(dataStore)
    }

    @Test
    fun `historyはデータソースの履歴をそのまま流す`() = runTest {
        // Arrange: データソースに 2 件追加済みの Repository
        val dataSource = makeDataSource()
        val repository = SearchHistoryRepositoryImpl(dataSource)
        dataSource.add("kotlin")
        dataSource.add("swift")

        // Act: Repository の history を読む
        val history = repository.history.first()

        // Assert: データソースと同じ新しい順の 2 件が流れる
        assertEquals(listOf("swift", "kotlin"), history)
    }

    @Test
    fun `addはデータソースへ届く`() = runTest {
        // Arrange: 空のデータソースを持つ Repository
        val dataSource = makeDataSource()
        val repository = SearchHistoryRepositoryImpl(dataSource)

        // Act: Repository から追加する
        repository.add("kotlin")
        val stored = dataSource.history.first()

        // Assert: データソースの履歴に入っている
        assertEquals(listOf("kotlin"), stored)
    }

    @Test
    fun `removeはデータソースへ届く`() = runTest {
        // Arrange: データソースに 2 件追加済みの Repository
        val dataSource = makeDataSource()
        val repository = SearchHistoryRepositoryImpl(dataSource)
        dataSource.add("kotlin")
        dataSource.add("swift")

        // Act: Repository から kotlin を削除する
        repository.remove("kotlin")
        val stored = dataSource.history.first()

        // Assert: データソースの履歴から kotlin だけが消えている
        assertEquals(listOf("swift"), stored)
    }

    @Test
    fun `clearはデータソースへ届く`() = runTest {
        // Arrange: データソースに 2 件追加済みの Repository
        val dataSource = makeDataSource()
        val repository = SearchHistoryRepositoryImpl(dataSource)
        dataSource.add("kotlin")
        dataSource.add("swift")

        // Act: Repository から全消去する
        repository.clear()
        val stored = dataSource.history.first()

        // Assert: データソースの履歴が空になっている
        assertTrue(stored.isEmpty())
    }
}
