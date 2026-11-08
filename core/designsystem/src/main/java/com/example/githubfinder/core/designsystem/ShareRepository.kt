/*
 * Abstract:
 * リポジトリの GitHub URL を Android 標準のシェアシートで共有するヘルパー。
 */

package com.example.githubfinder.core.designsystem

import android.content.Context
import android.content.Intent

/** [fullName]（owner/repo）の GitHub URL をシェアシートで共有する。 */
fun Context.shareRepository(fullName: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, githubUrlFor(fullName))
        putExtra(Intent.EXTRA_SUBJECT, fullName)
    }
    startActivity(Intent.createChooser(sendIntent, getString(R.string.share_repository_chooser_title)))
}
