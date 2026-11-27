/*
 * Abstract:
 * DataStore ベースのローカルデータソースを使用する検索履歴リポジトリの実装。
 */

package com.example.githubfinder.core.data.repository

import com.example.githubfinder.core.datastore.SearchHistoryLocalDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
internal class SearchHistoryRepositoryImpl @Inject constructor(
    private val localDataSource: SearchHistoryLocalDataSource,
) : SearchHistoryRepository {

    override val history: Flow<List<String>> = localDataSource.history
    override suspend fun add(rawQuery: String) = localDataSource.add(rawQuery)
    override suspend fun remove(query: String) = localDataSource.remove(query)
    override suspend fun clear() = localDataSource.clear()
}
