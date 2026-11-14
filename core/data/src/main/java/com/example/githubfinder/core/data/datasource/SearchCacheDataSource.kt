/*
 * Abstract:
 * 検索結果のメモリキャッシュを管理するデータソース。
 */

package com.example.githubfinder.core.data.datasource

import com.example.githubfinder.core.model.SearchResultEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchCacheDataSource @Inject constructor() {
    private val cache = mutableMapOf<String, SearchResultEntity>()

    fun get(query: String, page: Int): SearchResultEntity? = cache[cacheKey(query, page)]

    fun set(query: String, page: Int, result: SearchResultEntity) {
        cache[cacheKey(query, page)] = result
    }

    fun clear() = cache.clear()

    private fun cacheKey(query: String, page: Int): String = "${query.lowercase()}:$page"
}
