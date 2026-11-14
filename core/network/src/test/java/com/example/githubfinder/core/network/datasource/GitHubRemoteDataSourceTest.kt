package com.example.githubfinder.core.network.datasource

import com.example.githubfinder.core.model.AppError
import com.example.githubfinder.core.network.GitHubApiService
import com.example.githubfinder.core.network.dto.SearchResponseDTO
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class GitHubRemoteDataSourceTest {

    /** [searchRepositories] の結果を [behavior] で差し替えられる API の Fake。 */
    private class FakeApi(
        var behavior: suspend () -> SearchResponseDTO,
    ) : GitHubApiService {
        override suspend fun searchRepositories(
            query: String,
            page: Int,
            perPage: Int,
            sort: String,
            order: String,
        ): SearchResponseDTO = behavior()
    }

    private fun httpException(code: Int) = HttpException(
        Response.error<SearchResponseDTO>(code, "".toResponseBody("application/json".toMediaType())),
    )

    private suspend fun GitHubRemoteDataSource.searchError(): Throwable {
        try {
            searchRepositories("kotlin", 1)
        } catch (e: Throwable) {
            return e
        }
        fail("Expected an exception")
        throw AssertionError("unreachable")
    }

    @Test
    fun `成功時はAPIのレスポンスをそのまま返す`() = runTest {
        // Arrange: 総件数 7 のレスポンスを返す API
        val dto = SearchResponseDTO(totalCount = 7, items = emptyList())
        val dataSource = GitHubRemoteDataSource(FakeApi { dto })

        // Act: 検索する
        val result = dataSource.searchRepositories("kotlin", 1)

        // Assert: 加工せず同じレスポンスが返る
        assertSame(dto, result)
    }

    @Test
    fun `HTTP 403はRateLimitになる`() = runTest {
        // Arrange: 403 の HttpException を投げる API
        val dataSource = GitHubRemoteDataSource(FakeApi { throw httpException(403) })

        // Act: 検索して例外を受け取る
        val error = dataSource.searchError()

        // Assert: GitHub のレート制限として AppError.RateLimit に変換される
        assertEquals(AppError.RateLimit, error)
    }

    @Test
    fun `403以外のHTTPエラーはステータスコード付きのServerになる`() = runTest {
        // Arrange: 500 の HttpException を投げる API
        val dataSource = GitHubRemoteDataSource(FakeApi { throw httpException(500) })

        // Act: 検索して例外を受け取る
        val error = dataSource.searchError()

        // Assert: ステータスコードを保持した AppError.Server(500) になる
        assertEquals(AppError.Server(500), error)
    }

    @Test
    fun `IOExceptionはNetworkになる`() = runTest {
        // Arrange: 通信失敗（IOException）を投げる API
        val dataSource = GitHubRemoteDataSource(FakeApi { throw IOException("offline") })

        // Act: 検索して例外を受け取る
        val error = dataSource.searchError()

        // Assert: AppError.Network に変換される
        assertEquals(AppError.Network, error)
    }

    @Test
    fun `想定外の例外はメッセージ付きのUnknownになりメッセージが無ければ既定文言になる`() = runTest {
        // Arrange: メッセージ付き / メッセージ無しの想定外例外を順に投げる API
        val api = FakeApi { throw IllegalStateException("broken") }
        val dataSource = GitHubRemoteDataSource(api)

        // Act: メッセージ付きの例外で検索する
        val withMessage = dataSource.searchError()

        // Assert: 元のメッセージを持つ AppError.Unknown になる
        assertEquals(AppError.Unknown("broken"), withMessage)

        // Act: メッセージの無い例外で検索する
        api.behavior = { throw IllegalStateException() }
        val withoutMessage = dataSource.searchError()

        // Assert: 既定文言の AppError.Unknown になる
        assertEquals(AppError.Unknown("Unknown error"), withoutMessage)
    }

    @Test
    fun `CancellationExceptionはAppErrorに変換されずそのまま伝播する`() = runTest {
        // Arrange: CancellationException を投げる API
        val cancellation = CancellationException("cancelled")
        val dataSource = GitHubRemoteDataSource(FakeApi { throw cancellation })

        // Act: 検索して例外を受け取る
        val error = dataSource.searchError()

        // Assert: AppError.Unknown に化けず、同じ例外が投げ直される
        assertSame(cancellation, error)
    }

    @Test
    fun `通信中にコルーチンをキャンセルするとAppErrorではなくキャンセルとして終わる`() = runTest {
        // Arrange: 完了しない（通信中のままの）API を呼ぶ検索を開始する
        val gate = CompletableDeferred<SearchResponseDTO>()
        val dataSource = GitHubRemoteDataSource(FakeApi { gate.await() })
        val deferred = async { dataSource.searchRepositories("kotlin", 1) }
        runCurrent()

        // Act: 通信中に呼び出し元をキャンセルする
        deferred.cancel()
        runCurrent()

        // Assert: キャンセル扱いで終わる（AppError.Unknown として失敗しない）
        assertTrue(deferred.isCancelled)
        val error = runCatching { deferred.await() }.exceptionOrNull()
        assertTrue("Expected CancellationException, got $error", error is CancellationException)
    }
}
