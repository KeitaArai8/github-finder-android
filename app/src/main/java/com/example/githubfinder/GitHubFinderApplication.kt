/*
 * Abstract:
 * Hiltを使用するアプリケーションクラス。
 * デバッグビルドでのみ Timber を有効化し、構造化ログを Logcat に出力する。
 */

package com.example.githubfinder

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class GitHubFinderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Release ビルドでは Timber を plant しないため、Timber.x() 呼び出しは no-op になる
        // 本番環境で集約ログ出力が必要な場合は CrashlyticsTree 等を別途 plant する設計
    }
}
