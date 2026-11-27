package com.example.githubfinder.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
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
class SearchHistoryLocalDataSourceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun TestScope.makeDataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        produceFile = { File(tempFolder.root, "search_history.preferences_pb") },
    )

    @Test
    fun `未記録のときhistoryは空`() = runTest {
        // Arrange: 何も書き込まれていない DataStore
        val dataSource = SearchHistoryLocalDataSource(makeDataStore())

        // Act: 現在値を読む
        val history = dataSource.history.first()

        // Assert: 空リスト
        assertTrue(history.isEmpty())
    }

    @Test
    fun `addすると新しい順に先頭へ入る`() = runTest {
        // Arrange: 未記録の DataStore
        val dataSource = SearchHistoryLocalDataSource(makeDataStore())

        // Act: kotlin → swift → compose の順に追加する
        dataSource.add("kotlin")
        dataSource.add("swift")
        dataSource.add("compose")
        val history = dataSource.history.first()

        // Assert: 最後に追加したものが先頭の並びになる
        assertEquals(listOf("compose", "swift", "kotlin"), history)
    }

    @Test
    fun `前後の空白はtrimして記録する`() = runTest {
        // Arrange: 未記録の DataStore
        val dataSource = SearchHistoryLocalDataSource(makeDataStore())

        // Act: 前後に空白のあるクエリを追加する
        dataSource.add("  kotlin  ")
        val history = dataSource.history.first()

        // Assert: trim 済みの文字列で記録される
        assertEquals(listOf("kotlin"), history)
    }

    @Test
    fun `既存と同じクエリを追加すると重複せず先頭へ移る`() = runTest {
        // Arrange: kotlin → swift → compose の順に追加済み
        val dataSource = SearchHistoryLocalDataSource(makeDataStore())
        dataSource.add("kotlin")
        dataSource.add("swift")
        dataSource.add("compose")

        // Act: 最も古い kotlin をもう一度追加する
        dataSource.add("kotlin")
        val history = dataSource.history.first()

        // Assert: kotlin は 1 件だけで先頭に移り、残りの順序は保たれる
        assertEquals(listOf("kotlin", "compose", "swift"), history)
    }

    @Test
    fun `空文字と空白のみのクエリは無視される`() = runTest {
        // Arrange: kotlin を 1 件追加済み
        val dataSource = SearchHistoryLocalDataSource(makeDataStore())
        dataSource.add("kotlin")

        // Act: 空文字と空白のみのクエリを追加する
        dataSource.add("")
        dataSource.add("   ")
        val history = dataSource.history.first()

        // Assert: 履歴は kotlin の 1 件のまま
        assertEquals(listOf("kotlin"), history)
    }

    @Test
    fun `11件追加すると最新10件だけが残る`() = runTest {
        // Arrange: 未記録の DataStore
        val dataSource = SearchHistoryLocalDataSource(makeDataStore())

        // Act: q1〜q11 を順に追加する
        (1..11).forEach { dataSource.add("q$it") }
        val history = dataSource.history.first()

        // Assert: 最も古い q1 が落ち、q11 から q2 までの 10 件が新しい順に残る
        assertEquals((11 downTo 2).map { "q$it" }, history)
    }

    @Test
    fun `removeで指定したクエリだけが消える`() = runTest {
        // Arrange: kotlin → swift → compose の順に追加済み
        val dataSource = SearchHistoryLocalDataSource(makeDataStore())
        dataSource.add("kotlin")
        dataSource.add("swift")
        dataSource.add("compose")

        // Act: swift を削除する
        dataSource.remove("swift")
        val history = dataSource.history.first()

        // Assert: swift だけが消え、残りの順序は保たれる
        assertEquals(listOf("compose", "kotlin"), history)
    }

    @Test
    fun `clearで全件が消える`() = runTest {
        // Arrange: 2 件追加済み
        val dataSource = SearchHistoryLocalDataSource(makeDataStore())
        dataSource.add("kotlin")
        dataSource.add("swift")

        // Act: 全消去する
        dataSource.clear()
        val history = dataSource.history.first()

        // Assert: 空リストになる
        assertTrue(history.isEmpty())
    }

    @Test
    fun `キーhistoryに改行区切りの文字列として保存される`() = runTest {
        // Arrange: 実 DataStore を共有するデータソース
        val dataStore = makeDataStore()
        val dataSource = SearchHistoryLocalDataSource(dataStore)

        // Act: kotlin → swift の順に追加し、DataStore の生の値を読む
        dataSource.add("kotlin")
        dataSource.add("swift")
        val stored = dataStore.data.first()[stringPreferencesKey("history")]

        // Assert: 保存形式は新しい順の改行区切り（既存ユーザーの保存済みデータと互換）
        assertEquals("swift\nkotlin", stored)
    }
}
