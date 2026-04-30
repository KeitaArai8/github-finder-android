package com.example.githubfinder.core.data.datasource

import com.example.githubfinder.core.model.RepositoryEntity
import com.example.githubfinder.core.model.SearchResultEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SearchCacheDataSourceTest {

    @Test
    fun `未保存のクエリ_ページはnullを返す`() {
        // Arrange: 空のキャッシュ
        val cache = SearchCacheDataSource()

        // Act: 未保存キーを get
        val result = cache.get("swift", 1)

        // Assert: nil が返る
        assertNull(result)
    }

    @Test
    fun `set後のgetは保存した値を返す`() {
        // Arrange: 5件のキャッシュ値を用意
        val cache = SearchCacheDataSource()
        val expected = makeResult(totalCount = 10, itemCount = 5)

        // Act: set してから同じキーで get
        cache.set("swift", 1, expected)
        val actual = cache.get("swift", 1)

        // Assert: 保存した値（totalCount=10, itemCount=5）が返る
        assertNotNull(actual)
        assertEquals(10, actual!!.totalCount)
        assertEquals(5, actual.items.size)
    }

    @Test
    fun `異なるページは別キーで管理される`() {
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
    fun `クエリは大文字小文字を区別しない`() {
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
    fun `異なるクエリは別キーで管理される`() {
        // Arrange: 2つのクエリで保存
        val cache = SearchCacheDataSource()
        cache.set("swift", 1, makeResult(totalCount = 1, itemCount = 1))
        cache.set("kotlin", 1, makeResult(totalCount = 2, itemCount = 2))

        // Act & Assert: クエリ別に正しい totalCount が取り出せる
        assertEquals(1, cache.get("swift", 1)?.totalCount)
        assertEquals(2, cache.get("kotlin", 1)?.totalCount)
    }

    @Test
    fun `clearで全キャッシュが削除される`() {
        // Arrange: 2つのクエリで保存済みキャッシュ
        val cache = SearchCacheDataSource()
        cache.set("swift", 1, makeResult(totalCount = 1, itemCount = 1))
        cache.set("kotlin", 1, makeResult(totalCount = 1, itemCount = 1))

        // Act: clear で全削除
        cache.clear()

        // Assert: 両方とも null が返る
        assertNull(cache.get("swift", 1))
        assertNull(cache.get("kotlin", 1))
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
