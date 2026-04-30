/*
 * Abstract:
 * SearchHistoryDataSource のテスト用 Fake 実装（Compose UI Test 用）。
 * src/test 配下の同名 Fake と同じ内容（src/test と src/androidTest は別 source set のため複製している）。
 */

package com.example.githubfinder.feature.search

import com.example.githubfinder.core.data.datasource.SearchHistoryDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSearchHistoryDataSource(
    private val maxEntries: Int = 10,
) : SearchHistoryDataSource {

    private val _history = MutableStateFlow<List<String>>(emptyList())
    override val history: Flow<List<String>> = _history.asStateFlow()

    override suspend fun add(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) return
        val current = _history.value
        _history.value = (listOf(query) + current.filter { it != query }).take(maxEntries)
    }

    override suspend fun remove(query: String) {
        _history.value = _history.value.filter { it != query }
    }

    override suspend fun clear() {
        _history.value = emptyList()
    }

    /** テスト用の即時セット（永続化フローを通さず履歴を直接差し込む）。 */
    fun setHistoryForTest(items: List<String>) {
        _history.value = items
    }
}
