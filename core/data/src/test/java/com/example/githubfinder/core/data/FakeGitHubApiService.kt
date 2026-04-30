package com.example.githubfinder.core.data

import com.example.githubfinder.core.network.GitHubApiService
import com.example.githubfinder.core.network.dto.SearchResponseDTO

class FakeGitHubApiService : GitHubApiService {
    var response: SearchResponseDTO = SearchResponseDTO(totalCount = 0, items = emptyList())
    var errorToThrow: Throwable? = null
    var callCount: Int = 0

    override suspend fun searchRepositories(
        query: String,
        page: Int,
        perPage: Int,
        sort: String,
        order: String,
    ): SearchResponseDTO {
        callCount += 1
        errorToThrow?.let { throw it }
        return response
    }
}
