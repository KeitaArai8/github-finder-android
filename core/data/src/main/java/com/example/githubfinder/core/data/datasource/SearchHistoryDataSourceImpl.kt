/*
 * Abstract:
 * SearchHistoryDataSource の DataStore Preferences ベースの実装。
 * 改行区切りで Set ではなく List として順序を保持する（履歴の並び順を表現するため）。
 * クエリ自体に改行は含まれない前提（UI 層で trim 済み）。
 */

package com.example.githubfinder.core.data.datasource

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

private val Context.searchHistoryDataStore by preferencesDataStore(name = "search_history")

@Singleton
class SearchHistoryDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SearchHistoryDataSource {

    private val historyKey = stringPreferencesKey(KEY_HISTORY)

    override val history: Flow<List<String>> = context.searchHistoryDataStore.data.map { preferences ->
        preferences.toHistoryList()
    }

    override suspend fun add(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) return

        context.searchHistoryDataStore.edit { preferences ->
            val current = preferences.toHistoryList()
            val updated = (listOf(query) + current.filter { it != query }).take(MAX_ENTRIES)
            preferences[historyKey] = updated.joinToString(SEPARATOR)
            Timber.tag(TAG).i("Search history added: query=%s, total=%d", query, updated.size)
        }
    }

    override suspend fun remove(query: String) {
        context.searchHistoryDataStore.edit { preferences ->
            val updated = preferences.toHistoryList().filter { it != query }
            preferences[historyKey] = updated.joinToString(SEPARATOR)
            Timber.tag(TAG).i("Search history removed: query=%s", query)
        }
    }

    override suspend fun clear() {
        context.searchHistoryDataStore.edit { preferences ->
            preferences[historyKey] = ""
            Timber.tag(TAG).i("Search history cleared")
        }
    }

    private fun Preferences.toHistoryList(): List<String> {
        val raw = this[historyKey].orEmpty()
        if (raw.isEmpty()) return emptyList()
        return raw.split(SEPARATOR).filter { it.isNotEmpty() }
    }

    private companion object {
        const val TAG = "Search"
        const val KEY_HISTORY = "history"
        const val MAX_ENTRIES = 10
        const val SEPARATOR = "\n"
    }
}
