package com.example.githubfinder.core.data.datasource

import com.example.githubfinder.core.model.RepositoryEntity
import com.example.githubfinder.core.model.SearchResultEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SearchCacheDataSourceTest {

    @Test
    fun `未保存のクエリ_ページはnullを返す`() = runTest {
        // Arrange: 空のキャッシュ
        val cache = SearchCacheDataSource()

        // Act: 未保存キーを get
        val result = cache.get("swift", 1)

        // Assert: nil が返る
        assertNull(result)
    }

    @Test
    fun `set後のgetは保存した値を返す`() = runTest {
        // Arrange: 5件のキャッシュ値を用意
        val cache = SearchCacheDataSource()
        val expected = makeResult(totalCount = 10, itemCount = 5)

        // Act: set してから同じキーで get
        cache.set("swift", 1, expected)
        val actual = cache.get("swift", 1)

        // Assert: 保存した値（totalCount=10, itemCount=5）が返る
        checkNotNull(actual)
        assertEquals(10, actual.totalCount)
        assertEquals(5, actual.items.size)
    }

    @Test
    fun `異なるページは別キーで管理される`() = runTest {
        // Arrange: page=1 と page=2 に異なる結果を保存
        val cache = SearchCacheDataSource()
        val page1 = makeResult(totalCount = 60, itemCount = 30)
        val page2 = makeResult(totalCount = 60, itemCount = 30)

        // Act: 両方を保存
        cache.set("swift", 1, page1)
        cache.set("swift", 2, page2)

        // Assert: ページ別に取り出せる
        assertEquals(30, cache.get("swift", 1)?.items?.size)
        assertEquals(30, cache.get("swift", 2)?.items?.size)
    }

    @Test
    fun `クエリは大文字小文字を区別しない`() = runTest {
        // Arrange: "Swift"（先頭大文字）で保存
        val cache = SearchCacheDataSource()
        val result = makeResult(totalCount = 1, itemCount = 1)

        // Act: 大文字違いで保存
        cache.set("Swift", 1, result)

        // Assert: "swift"・"SWIFT" でも取り出せる
        assertNotNull(cache.get("swift", 1))
        assertNotNull(cache.get("SWIFT", 1))
    }

    @Test
    fun `異なるクエリは別キーで管理される`() = runTest {
        // Arrange: 2つのクエリで保存
        val cache = SearchCacheDataSource()
        cache.set("swift", 1, makeResult(totalCount = 1, itemCount = 1))
        cache.set("kotlin", 1, makeResult(totalCount = 2, itemCount = 2))

        // Act & Assert: クエリ別に正しい totalCount が取り出せる
        assertEquals(1, cache.get("swift", 1)?.totalCount)
        assertEquals(2, cache.get("kotlin", 1)?.totalCount)
    }

    @Test
    fun `replaceは同じクエリの他ページを破棄し指定ページの値で置き換える`() = runTest {
        // Arrange: "swift" の page=1,2 と別クエリ "kotlin" の page=1 を保存済み
        val cache = SearchCacheDataSource()
        cache.set("swift", 1, makeResult(totalCount = 60, itemCount = 30))
        cache.set("swift", 2, makeResult(totalCount = 60, itemCount = 30))
        cache.set("kotlin", 1, makeResult(totalCount = 1, itemCount = 1))

        // Act: "SWIFT"（大文字違い）の page=1 を新しい値（totalCount=5）で replace
        cache.replace("SWIFT", 1, makeResult(totalCount = 5, itemCount = 5))

        // Assert: page=1 は新しい値、同じクエリの page=2 は破棄され、別クエリ "kotlin" は残る
        assertEquals(5, cache.get("swift", 1)?.totalCount)
        assertNull(cache.get("swift", 2))
        assertEquals(1, cache.get("kotlin", 1)?.totalCount)
    }

    @Test
    fun `保持件数を超えると古いエントリから破棄され新しいエントリは残る`() = runTest {
        // Arrange: 上限を十分に超える 100 件を、別クエリとして古い順に保存する
        val cache = SearchCacheDataSource()
        repeat(100) { index -> cache.set("query$index", 1, makeResult(totalCount = index, itemCount = 1)) }

        // Act & Assert: 最も古い "query0" は破棄され、最後に保存した "query99" は残る
        assertNull(cache.get("query0", 1))
        assertEquals(99, cache.get("query99", 1)?.totalCount)
    }

    private fun makeResult(totalCount: Int, itemCount: Int): SearchResultEntity {
        val items = (0 until itemCount).map { index ->
            RepositoryEntity(
                id = index,
                fullName = "owner/repo$index",
                description = null,
                starCount = 0,
                language = null,
                ownerAvatarURL = null,
            )
        }
        return SearchResultEntity(totalCount = totalCount, items = items, hasNextPage = false)
    }
}
