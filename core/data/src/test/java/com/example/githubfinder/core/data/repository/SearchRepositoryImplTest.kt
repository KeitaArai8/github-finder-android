package com.example.githubfinder.core.data.repository

import com.example.githubfinder.core.data.FakeGitHubApiService
import com.example.githubfinder.core.data.datasource.SearchCacheDataSource
import com.example.githubfinder.core.model.AppError
import com.example.githubfinder.core.network.datasource.GitHubRemoteDataSource
import com.example.githubfinder.core.network.dto.OwnerDTO
import com.example.githubfinder.core.network.dto.RepositoryDTO
import com.example.githubfinder.core.network.dto.SearchResponseDTO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class SearchRepositoryImplTest {

    @Test
    fun `初回検索はリモートAPIを呼ぶ`() = runTest {
        // Arrange: totalCount=1 の正常レスポンスを返す Fake API
        val fakeApi = FakeGitHubApiService().apply { response = makeResponse(totalCount = 1) }
        val repository = makeRepository(fakeApi)

        // Act: 初回検索
        repository.search("swift", 1)

        // Assert: Remote が1回呼ばれる
        assertEquals(1, fakeApi.callCount)
    }

    @Test
    fun `同一クエリ_ページの再検索はキャッシュヒットしリモートを呼ばない`() = runTest {
        // Arrange: totalCount=1 の正常レスポンス
        val fakeApi = FakeGitHubApiService().apply { response = makeResponse(totalCount = 1) }
        val repository = makeRepository(fakeApi)

        // Act: 同じクエリ・ページで2回検索
        repository.search("swift", 1)
        repository.search("swift", 1)

        // Assert: 2回目はキャッシュヒットで Remote 1回のみ
        assertEquals(1, fakeApi.callCount)
    }

    @Test
    fun `異なるページの検索はキャッシュミスしリモートを呼ぶ`() = runTest {
        // Arrange: totalCount=60（複数ページあり）
        val fakeApi = FakeGitHubApiService().apply { response = makeResponse(totalCount = 60) }
        val repository = makeRepository(fakeApi)

        // Act: page=1 と page=2 を検索
        repository.search("swift", 1)
        repository.search("swift", 2)

        // Assert: ページ別キャッシュキーで Remote 2回呼ばれる
        assertEquals(2, fakeApi.callCount)
    }

    @Test
    fun `totalCount gt page times 30 のとき hasNextPage は true`() = runTest {
        // Arrange: totalCount=100, perPage=30 → page1 では 70件残り
        val fakeApi = FakeGitHubApiService().apply { response = makeResponse(totalCount = 100) }
        val repository = makeRepository(fakeApi)

        // Act: page=1 で検索
        val result = repository.search("swift", 1)

        // Assert: 次ページあり
        assertTrue(result.hasNextPage)
    }

    @Test
    fun `totalCount le page times 30 のとき hasNextPage は false`() = runTest {
        // Arrange: totalCount=20, perPage=30 → page1 で完結
        val fakeApi = FakeGitHubApiService().apply { response = makeResponse(totalCount = 20) }
        val repository = makeRepository(fakeApi)

        // Act: page=1 で検索
        val result = repository.search("swift", 1)

        // Assert: 次ページなし
        assertFalse(result.hasNextPage)
    }

    @Test
    fun `403応答でAppError_RateLimitが透過する`() = runTest {
        // Arrange: 403 HttpException を投げる Fake API
        val fakeApi = FakeGitHubApiService().apply { errorToThrow = makeHttpException(403) }
        val repository = makeRepository(fakeApi)

        // Act & Assert: AppError.RateLimit が透過する
        try {
            repository.search("swift", 1)
            fail("Expected AppError.RateLimit")
        } catch (e: AppError.RateLimit) {
            // expected
        }
    }

    @Test
    fun `500応答でAppError_Serverが透過する`() = runTest {
        // Arrange: 500 HttpException を投げる Fake API
        val fakeApi = FakeGitHubApiService().apply { errorToThrow = makeHttpException(500) }
        val repository = makeRepository(fakeApi)

        // Act & Assert: AppError.Server(code=500) が透過する
        try {
            repository.search("swift", 1)
            fail("Expected AppError.Server")
        } catch (e: AppError.Server) {
            assertEquals(500, e.statusCode)
        }
    }

    @Test
    fun `IOExceptionはAppError_Networkに変換されて透過する`() = runTest {
        // Arrange: IOException（オフライン相当）を投げる Fake API
        val fakeApi = FakeGitHubApiService().apply { errorToThrow = IOException("offline") }
        val repository = makeRepository(fakeApi)

        // Act & Assert: AppError.Network に変換されて透過する
        try {
            repository.search("swift", 1)
            fail("Expected AppError.Network")
        } catch (e: AppError.Network) {
            // expected
        }
    }

    @Test
    fun `404応答でAppError_Serverが透過する`() = runTest {
        // Arrange: 404 HttpException を投げる Fake API
        val fakeApi = FakeGitHubApiService().apply { errorToThrow = makeHttpException(404) }
        val repository = makeRepository(fakeApi)

        // Act & Assert: 4xx も 5xx と同じく AppError.Server(code=404) になる
        try {
            repository.search("swift", 1)
            fail("Expected AppError.Server")
        } catch (e: AppError.Server) {
            assertEquals(404, e.statusCode)
        }
    }

    @Test
    fun `通信中のキャンセルはAppError_Unknownに変換されずCancellationExceptionのまま伝播する`() = runTest {
        // Arrange: API 呼び出しが CancellationException を投げる Fake API
        val fakeApi = FakeGitHubApiService().apply { errorToThrow = CancellationException("cancelled") }
        val repository = makeRepository(fakeApi)

        // Act & Assert: AppError ではなく CancellationException がそのまま届く
        try {
            repository.search("swift", 1)
            fail("Expected CancellationException")
        } catch (e: CancellationException) {
            // expected
        }
    }

    private fun makeRepository(fakeApi: FakeGitHubApiService): SearchRepositoryImpl =
        SearchRepositoryImpl(
            remoteDataSource = GitHubRemoteDataSource(fakeApi),
            cacheDataSource = SearchCacheDataSource(),
        )

    private fun makeResponse(totalCount: Int): SearchResponseDTO = SearchResponseDTO(
        totalCount = totalCount,
        items = listOf(
            RepositoryDTO(
                id = 1,
                fullName = "owner/repo",
                description = null,
                stargazersCount = 0,
                language = null,
                owner = OwnerDTO(avatarUrl = "https://example.com/a.png"),
            ),
        ),
    )

    private fun makeHttpException(code: Int): HttpException {
        val body = "".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(code, body))
    }

}
