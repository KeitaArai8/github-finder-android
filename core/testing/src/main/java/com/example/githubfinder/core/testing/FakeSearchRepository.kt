package com.example.githubfinder.core.testing

import com.example.githubfinder.core.data.repository.SearchRepository
import com.example.githubfinder.core.model.SearchResultEntity
import kotlinx.coroutines.CompletableDeferred
import kotlin.coroutines.cancellation.CancellationException

class FakeSearchRepository : SearchRepository {
    var searchResult: Result<SearchResultEntity> = Result.success(
        SearchResultEntity(totalCount = 0, items = emptyList(), hasNextPage = false)
    )
    var searchCallCount = 0
    var lastSearchQuery: String? = null
    var lastSearchPage: Int? = null

    /** 非 null の間、search は complete されるまで中断する（通信中のまま止めるためのゲート）。 */
    var searchGate: CompletableDeferred<Unit>? = null

    /** ゲート待ち中にキャンセルされたリクエストのクエリ（キャンセル伝播の検証用）。 */
    val cancelledQueries = mutableListOf<String>()

    override suspend fun search(query: String, page: Int): SearchResultEntity {
        searchCallCount++
        lastSearchQuery = query
        lastSearchPage = page
        try {
            searchGate?.await()
        } catch (e: CancellationException) {
            cancelledQueries += query
            throw e
        }
        return searchResult.getOrThrow()
    }
}
