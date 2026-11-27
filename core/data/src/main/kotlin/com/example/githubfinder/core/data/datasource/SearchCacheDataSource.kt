/*
 * Abstract:
 * 検索結果のメモリキャッシュを管理するデータソース。
 * 複数のコルーチンから呼ばれるため Mutex で読み書きを直列化し、保持件数は MAX_ENTRIES に制限する（超えたら古い順に破棄）。
 */

package com.example.githubfinder.core.data.datasource

import com.example.githubfinder.core.model.SearchResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
internal class SearchCacheDataSource @Inject constructor() {
    private val mutex = Mutex()

    /** 挿入順を保つ LinkedHashMap。先頭が最も古いエントリ。 */
    private val cache = LinkedHashMap<CacheKey, SearchResult>()

    suspend fun get(query: String, page: Int): SearchResult? = mutex.withLock { cache[cacheKey(query, page)] }

    suspend fun set(query: String, page: Int, result: SearchResult) {
        mutex.withLock { put(cacheKey(query, page), result) }
    }

    /** [query] の他ページのキャッシュを破棄したうえで、[page] ページ目の [result] を保存する。 */
    suspend fun replace(query: String, page: Int, result: SearchResult) {
        mutex.withLock {
            val key = cacheKey(query, page)
            cache.keys.removeAll { it.query == key.query }
            put(key, result)
        }
    }

    private fun put(key: CacheKey, result: SearchResult) {
        cache[key] = result
        while (cache.size > MAX_ENTRIES) {
            cache.remove(cache.keys.first())
        }
    }

    private fun cacheKey(query: String, page: Int) = CacheKey(query.lowercase(), page)

    private data class CacheKey(val query: String, val page: Int)

    private companion object {
        const val MAX_ENTRIES = 50
    }
}
