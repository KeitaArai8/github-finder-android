/*
 * Abstract:
 * ViewModel / State が「画面に出す文言」を Context なしで表現するための型。
 * ViewModel に日本語リテラルや Context を持たせず、文字列リソース ID と引数だけを State に載せ、
 * 実際の文字列化は UI 層（Composable / Context）で行う。リソース ID と引数の比較でテストできる。
 */

package com.example.githubfinder.core.designsystem

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.githubfinder.core.model.AppError

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

    /** Context を使って文字列化する（Composable 外。Glance ウィジェット等）。 */
    fun asString(context: Context): String = when (this) {
        is Resource -> context.getString(resId, *args.toTypedArray())
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

/** 任意の例外 → 表示文言。[AppError] ならその文言、それ以外は例外メッセージ（無ければ汎用の不明エラー）。 */
fun Throwable.toUiText(): UiText =
    (this as? AppError)?.toUiText()
        ?: message?.let { UiText.Raw(it) }
        ?: UiText.Resource(R.string.error_unknown)
