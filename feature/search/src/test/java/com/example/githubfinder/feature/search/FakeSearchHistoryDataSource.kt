/*
 * Abstract:
 * SearchHistoryDataSource のテスト用 Fake 実装。
 * MutableStateFlow で in-memory に履歴を保持し、本番実装と同じ重複排除・上限制御を行う。
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

    var addCallCount = 0
        private set
    var lastAddedQuery: String? = null
        private set

    override suspend fun add(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) return
        addCallCount++
        lastAddedQuery = query
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
