/*
 * Abstract:
 * GitHubApiService のテスト用 Fake。
 * 既定は [response] を返す。失敗は [errorToThrow]、通信中のまま止める・呼び出しごとに結果を変えるなどは [behavior] で差し替える。
 */

package com.example.githubfinder.core.testing

import com.example.githubfinder.core.network.GitHubApiService
import com.example.githubfinder.core.network.dto.SearchResponseDTO

class FakeGitHubApiService : GitHubApiService {
    var response: SearchResponseDTO = SearchResponseDTO(totalCount = 0, items = emptyList())
    var errorToThrow: Throwable? = null

    /** 非 null の間は [response] / [errorToThrow] より優先して呼ばれる（中断・例外・任意の結果を返せる）。 */
    var behavior: (suspend () -> SearchResponseDTO)? = null
    var callCount: Int = 0

    override suspend fun searchRepositories(
        query: String,
        page: Int,
        perPage: Int,
        sort: String,
        order: String,
    ): SearchResponseDTO {
        callCount += 1
        behavior?.let { return it() }
        errorToThrow?.let { throw it }
        return response
    }
}
