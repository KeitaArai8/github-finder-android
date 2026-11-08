/*
 * Abstract:
 * ViewModel / State が「画面に出す文言」を Context なしで表現するための型。
 * ViewModel に日本語リテラルや Context を持たせず、文字列リソース ID と引数だけを State に載せ、
 * 実際の文字列化は UI 層（Composable / Context）で行う。リソース ID と引数の比較でテストできる。
 */

package com.example.githubfinder.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.githubfinder.core.model.AppError
import java.io.IOException

sealed interface UiText {

    /** 文字列リソース + フォーマット引数。 */
    data class Resource(@StringRes val resId: Int, val args: List<Any> = emptyList()) : UiText

    /** ローカライズ不能な素の文字列（サーバー等が返した任意のメッセージ）。 */
    data class Raw(val value: String) : UiText

    /** Composable 内で文字列化する（ロケール変更に追従する）。 */
    @Composable
    fun asString(): String = when (this) {
        is Resource -> stringResource(resId, *args.toTypedArray())
        is Raw -> value
    }
}

/** [AppError] → 表示文言。ドメイン層（core:model）は文言を持たず、UI 層がこの変換で決める。 */
fun AppError.toUiText(): UiText = when (this) {
    AppError.Network -> UiText.Resource(R.string.error_network)
    is AppError.Server -> UiText.Resource(R.string.error_server, listOf(statusCode))
    AppError.RateLimit -> UiText.Resource(R.string.error_rate_limit)
    is AppError.Unknown -> UiText.Raw(message)
}

/**
 * 任意の例外 → 表示文言。[AppError] ならその文言、通信失敗（[IOException]）はネットワークエラー、
 * それ以外は汎用の不明エラー。例外の生メッセージ（技術的な文言）は画面に出さない。
 */
fun Throwable.toUiText(): UiText = when (this) {
    is AppError -> toUiText()
    is IOException -> AppError.Network.toUiText()
    else -> UiText.Resource(R.string.error_unknown)
}
