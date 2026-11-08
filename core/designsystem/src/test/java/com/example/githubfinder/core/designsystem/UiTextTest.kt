package com.example.githubfinder.core.designsystem

import com.example.githubfinder.core.model.AppError
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class UiTextTest {

    @Test
    fun `IOExceptionはネットワークエラーの文言になり生メッセージは出ない`() {
        // Arrange: 技術的なメッセージを持つ通信例外
        val error = IOException("Unable to resolve host \"api.github.com\"")

        // Act: 表示文言へ変換
        val text = error.toUiText()

        // Assert: AppError.Network と同じ文言リソースで、ホスト名などの生メッセージを含まない
        assertEquals(UiText.Resource(R.string.error_network), text)
    }

    @Test
    fun `AppErrorの例外はそのエラー専用の文言になる`() {
        // Arrange: レート制限の AppError
        val error: Throwable = AppError.RateLimit

        // Act: Throwable としての変換
        val text = error.toUiText()

        // Assert: レート制限の文言リソース
        assertEquals(UiText.Resource(R.string.error_rate_limit), text)
    }

    @Test
    fun `AppError以外の例外は汎用の不明エラー文言になり生メッセージは出ない`() {
        // Arrange: 実装詳細を含むメッセージの例外
        val error = IllegalStateException("NullPointer in SearchViewModel.performSearch")

        // Act: 表示文言へ変換
        val text = error.toUiText()

        // Assert: 汎用の不明エラーのリソースで、メッセージは UI に載らない
        assertEquals(UiText.Resource(R.string.error_unknown), text)
    }
}
