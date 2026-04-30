/*
 * Design System モジュール。
 * Compose の主要ライブラリは Convention Plugin で implementation 注入されるが、
 * 本モジュールは「他モジュールから transitive で使われるシンボル」を提供するため
 * api スコープで公開する必要があり、ここで明示的に上書きする。
 */

plugins {
    alias(libs.plugins.githubfinder.android.library)
    alias(libs.plugins.githubfinder.android.library.compose)
    alias(libs.plugins.githubfinder.android.hilt)
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
    api(libs.compose.material.icons)
    api(libs.browser)
    debugApi(libs.compose.ui.tooling)
}
