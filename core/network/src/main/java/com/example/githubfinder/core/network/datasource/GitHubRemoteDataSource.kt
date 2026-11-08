/*
 * Abstract:
 * GitHub APIとの通信を担当するリモートデータソース。
 */

package com.example.githubfinder.core.network.datasource

import com.example.githubfinder.core.model.AppError
import com.example.githubfinder.core.network.GitHubApiService
import com.example.githubfinder.core.network.dto.SearchResponseDTO
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class GitHubRemoteDataSource @Inject constructor(
    private val apiService: GitHubApiService,
) {
    suspend fun searchRepositories(query: String, page: Int): SearchResponseDTO {
        try {
            return apiService.searchRepositories(query = query, page = page)
        } catch (e: HttpException) {
            when (e.code()) {
                403 -> throw AppError.RateLimit
                else -> throw AppError.Server(e.code())
            }
        } catch (e: IOException) {
            throw AppError.Network
        } catch (e: CancellationException) {
            // キャンセルを AppError.Unknown に化かさない（構造化並行性を壊さないため伝播させる）
            throw e
        } catch (e: Exception) {
            throw AppError.Unknown(e.message ?: "Unknown error")
        }
    }
}
