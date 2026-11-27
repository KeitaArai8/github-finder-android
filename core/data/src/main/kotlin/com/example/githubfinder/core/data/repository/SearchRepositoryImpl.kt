/*
 * Abstract:
 * リモートAPIとメモリキャッシュを統合する検索リポジトリの実装。
 */

package com.example.githubfinder.core.data.repository

import com.example.githubfinder.core.data.datasource.SearchCacheDataSource
import com.example.githubfinder.core.data.model.asExternalModel
import com.example.githubfinder.core.model.AppError
import com.example.githubfinder.core.model.SearchResult
import com.example.githubfinder.core.network.datasource.GitHubRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
internal class SearchRepositoryImpl @Inject constructor(
    private val remoteDataSource: GitHubRemoteDataSource,
    private val cacheDataSource: SearchCacheDataSource,
) : SearchRepository {

    private val perPage = 30

    override suspend fun search(query: String, page: Int, refresh: Boolean): SearchResult {
        if (!refresh) cacheDataSource.get(query, page)?.let { return it }

        val dto = try {
            remoteDataSource.searchRepositories(query, page)
        } catch (e: AppError) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppError.Unknown(e.message ?: "Unknown error")
        }

        val items = dto.items.map { it.asExternalModel() }
        val hasNextPage = page * perPage < dto.totalCount
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
