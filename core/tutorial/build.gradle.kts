/*
 * チュートリアル表示済みフラグの永続化（DataStore + 旧 SharedPreferences からの移行）と、その Hilt 配線。
 * Compose には依存しない。コーチマークの描画・ステップ管理は :core:designsystem が持つ。
 */

plugins {
    alias(libs.plugins.githubfinder.android.library)
    alias(libs.plugins.githubfinder.android.hilt)
}

android {
    namespace = "com.example.githubfinder.core.tutorial"
}

dependencies {
    implementation(libs.coroutines.android)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
