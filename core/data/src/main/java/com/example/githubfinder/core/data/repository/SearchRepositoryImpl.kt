/*
 * Abstract:
 * リモートAPIとメモリキャッシュを統合する検索リポジトリの実装。
 */

package com.example.githubfinder.core.data.repository

import com.example.githubfinder.core.data.datasource.SearchCacheDataSource
import com.example.githubfinder.core.model.AppError
import com.example.githubfinder.core.model.SearchResultEntity
import com.example.githubfinder.core.network.datasource.GitHubRemoteDataSource
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val remoteDataSource: GitHubRemoteDataSource,
    private val cacheDataSource: SearchCacheDataSource,
) : SearchRepository {

    private val perPage = 30

    override suspend fun search(query: String, page: Int): SearchResultEntity {
        cacheDataSource.get(query, page)?.let { return it }

        val dto = try {
            remoteDataSource.searchRepositories(query, page)
        } catch (e: AppError) {
            throw e
        } catch (e: IOException) {
            throw AppError.Decode
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppError.Unknown(e.message ?: "Unknown error")
        }

        val items = dto.items.map { it.toEntity() }
        val hasNextPage = page * perPage < dto.totalCount
        val result = SearchResultEntity(
            totalCount = dto.totalCount,
            items = items,
            hasNextPage = hasNextPage,
        )
        cacheDataSource.set(query, page, result)
        return result
    }
}
