/*
 * Favorite Feature モジュール。
 * androidFeature Convention Plugin により以下が自動適用される:
 * - androidLibrary + androidLibraryCompose + androidHilt
 * - core:model + core:designsystem の依存
 * - lifecycle / hilt-lifecycle-viewmodel-compose / coil / timber
 * - 共通テスト依存（junit / turbine / coroutines-test）
 *
 * このファイルは「Feature 固有の依存」のみ記述する。
 */

plugins {
    alias(libs.plugins.githubfinder.android.feature)
}

android {
    namespace = "com.example.githubfinder.feature.favorite"
}

dependencies {
    implementation(project(":core:data"))

    testImplementation(project(":core:testing"))
}
