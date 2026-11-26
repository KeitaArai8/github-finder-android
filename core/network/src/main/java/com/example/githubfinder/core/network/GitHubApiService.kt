/*
 * Abstract:
 * GitHub APIのRetrofitインターフェース定義。
 */

package com.example.githubfinder.core.network

import com.example.githubfinder.core.network.dto.SearchResponseDTO
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface GitHubApiService {
    @Headers("Accept: application/vnd.github.v3+json")
    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 30,
        @Query("sort") sort: String = "stars",
        @Query("order") order: String = "desc",
    ): SearchResponseDTO
}
