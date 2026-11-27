/*
 * Abstract:
 * 検索履歴を DataStore Preferences に永続化するデータソース。
 * - 直近 MAX_ENTRIES 件（10）を新しい順に保持
 * - 同一クエリ追加時は重複排除して先頭に再挿入
 * - 空白のみ・空文字は無視
 * 改行区切りで Set ではなく List として順序を保持する（履歴の並び順を表現するため）。
 * クエリ自体に改行は含まれない前提（UI 層で trim 済み）。
 */

package com.example.githubfinder.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.githubfinder.core.datastore.di.SearchHistoryDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

@Singleton
class SearchHistoryLocalDataSource @Inject constructor(
    @SearchHistoryDataStore private val dataStore: DataStore<Preferences>,
) {

    // キー名は保存済みデータのキーそのもの。変えると既存ユーザーの履歴が読めなくなる。
    private val historyKey = stringPreferencesKey(KEY_HISTORY)

    /** 履歴の最新スナップショット Stream（新しい順）。 */
    val history: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences.toHistoryList()
    }

    /** 履歴に追加（重複排除 + 先頭挿入 + 上限切り詰め）。空白のみのクエリは無視する。 */
    suspend fun add(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) return

        dataStore.edit { preferences ->
            val current = preferences.toHistoryList()
            val updated = (listOf(query) + current.filter { it != query }).take(MAX_ENTRIES)
            preferences[historyKey] = updated.joinToString(SEPARATOR)
            Timber.tag(TAG).i("Search history added: query=%s, total=%d", query, updated.size)
        }
    }

    /** 単一エントリを削除する。 */
    suspend fun remove(query: String) {
        dataStore.edit { preferences ->
            val updated = preferences.toHistoryList().filter { it != query }
            preferences[historyKey] = updated.joinToString(SEPARATOR)
            Timber.tag(TAG).i("Search history removed: query=%s", query)
        }
    }

    /** 履歴を全消去する。 */
    suspend fun clear() {
        dataStore.edit { preferences ->
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
