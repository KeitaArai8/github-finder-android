/*
 * UI 共通モジュール。
 * モデル型（:core:model）を描画するコンポーネントと、複数 feature で使う UI 補助（文言型・Custom Tabs・シェア）を置く。
 * :core:designsystem とは違い、データ層のモデルに依存する。
 * feature が両モジュールのシンボルを使うため、designsystem / model は api で公開する。
 */

plugins {
    alias(libs.plugins.githubfinder.android.library)
    alias(libs.plugins.githubfinder.android.library.compose)
}

android {
    namespace = "com.example.githubfinder.core.ui"
}

dependencies {
    api(project(":core:designsystem"))
    api(project(":core:model"))
    implementation(libs.browser)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
