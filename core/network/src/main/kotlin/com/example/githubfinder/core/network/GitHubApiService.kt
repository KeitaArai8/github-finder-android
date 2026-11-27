/*
 * Abstract:
 * GitHub APIのRetrofitインターフェース定義。
 */

package com.example.githubfinder.core.network

import com.example.githubfinder.core.network.dto.SearchResponseDTO
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/** 検索 API の 1 ページあたりの件数。次ページの有無の計算でも同じ値を使う。 */
const val SEARCH_PER_PAGE = 30

interface GitHubApiService {
    @Headers("Accept: application/vnd.github.v3+json")
    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = SEARCH_PER_PAGE,
        @Query("sort") sort: String = "stars",
        @Query("order") order: String = "desc",
    ): SearchResponseDTO
}
