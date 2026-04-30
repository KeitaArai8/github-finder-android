package com.example.githubfinder.feature.search

import com.example.githubfinder.core.data.repository.SearchRepository
import com.example.githubfinder.core.model.SearchResultEntity

class FakeSearchRepository : SearchRepository {
    var searchResult: Result<SearchResultEntity> = Result.success(
        SearchResultEntity(totalCount = 0, items = emptyList(), hasNextPage = false)
    )

    override suspend fun search(query: String, page: Int): SearchResultEntity =
        searchResult.getOrThrow()
}
