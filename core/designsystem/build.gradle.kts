/*
 * Design System モジュール。テーマ・アイコン・汎用の状態表示コンポーネントを置く。
 * 他のプロジェクトモジュールにも DI フレームワークにも依存しない（モデルを描画するコンポーネントは :core:ui）。
 * Compose の主要ライブラリは Convention Plugin で implementation 注入されるが、
 * 本モジュールは「他モジュールから transitive で使われるシンボル」を提供するため
 * api スコープで公開する必要があり、ここで明示的に上書きする。
 */

plugins {
    alias(libs.plugins.githubfinder.android.library)
    alias(libs.plugins.githubfinder.android.library.compose)
}

android {
    namespace = "com.example.githubfinder.core.designsystem"
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.ui.tooling.preview)
    api(libs.compose.material3)
    debugApi(libs.compose.ui.tooling)
}
