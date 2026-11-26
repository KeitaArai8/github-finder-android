/*
 * Abstract:
 * GitHubリポジトリページURLをfullNameから安全に組み立てるヘルパー。
 * Uri.encodeでpercent-encodeし、`/`のみ許可することでowner/repo形式を維持する。
 */

package com.example.githubfinder.core.designsystem

import android.net.Uri

fun githubUrlFor(fullName: String): String {
    val encoded = Uri.encode(fullName, "/")
    return "https://github.com/$encoded"
}
