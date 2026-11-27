/*
 * Abstract:
 * リモートAPIとメモリキャッシュを統合する検索リポジトリの実装。
 */

package com.example.githubfinder.core.data.repository

import com.example.githubfinder.core.data.datasource.SearchCacheDataSource
import com.example.githubfinder.core.data.model.asExternalModel
import com.example.githubfinder.core.model.SearchResult
import com.example.githubfinder.core.network.SEARCH_PER_PAGE
import com.example.githubfinder.core.network.datasource.GitHubRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SearchRepositoryImpl @Inject constructor(
    private val remoteDataSource: GitHubRemoteDataSource,
    private val cacheDataSource: SearchCacheDataSource,
) : SearchRepository {

    override suspend fun search(query: String, page: Int, refresh: Boolean): SearchResult {
        if (!refresh) cacheDataSource.get(query, page)?.let { return it }

        // 失敗は GitHubRemoteDataSource が AppError に変換済みなので、ここでは包み直さない
        val dto = remoteDataSource.searchRepositories(query, page)

        val items = dto.items.map { it.asExternalModel() }
        val hasNextPage = page * SEARCH_PER_PAGE < dto.totalCount
        val result = SearchResult(
            totalCount = dto.totalCount,
            items = items,
            hasNextPage = hasNextPage,
        )
        if (refresh) {
            cacheDataSource.replace(query, page, result)
        } else {
            cacheDataSource.set(query, page, result)
        }
        return result
    }
}
