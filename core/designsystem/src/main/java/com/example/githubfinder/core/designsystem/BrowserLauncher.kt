/*
 * Abstract:
 * Custom Tabs でURLを開く汎用ユーティリティ。設定画面の外部リンク、
 * リポジトリ行タップからのGitHubページ表示など、複数機能から再利用する。
 *
 * セキュリティ: HTTPS スキームのみを許可する防御的プログラミングを行い、
 * 万一 file://・javascript:・intent:// など悪意あるURLが渡されても開かない。
 */

package com.example.githubfinder.core.designsystem

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

fun launchCustomTab(context: Context, url: String) {
    val uri = Uri.parse(url)
    // 許可スキームは HTTPS のみ（外部ブラウザが任意スキームを解釈して意図しない挙動になるのを防ぐ）
    if (uri.scheme != "https") return
    CustomTabsIntent.Builder().build().launchUrl(context, uri)
}
