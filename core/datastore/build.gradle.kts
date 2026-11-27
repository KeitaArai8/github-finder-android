/*
 * DataStore Preferences を使うデータソース（検索履歴・チュートリアル表示済みフラグ）と、
 * その DataStore を提供する Hilt モジュールを置く。利用側は :core:data のリポジトリのみ。
 * データソースのコンストラクタが DataStore 型を公開するため、DataStore は api で公開する。
 */

plugins {
    alias(libs.plugins.githubfinder.android.library)
    alias(libs.plugins.githubfinder.android.hilt)
}

android {
    namespace = "com.example.githubfinder.core.datastore"
}

dependencies {
    api(libs.datastore.preferences)
    implementation(libs.coroutines.android)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
