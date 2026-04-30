/*
 * Search Feature モジュール。androidFeature Convention で共通設定を継承。
 */

plugins {
    alias(libs.plugins.githubfinder.android.feature)
}

android {
    namespace = "com.example.githubfinder.feature.search"
}

dependencies {
    implementation(project(":core:data"))

    // Compose UI Test（Search 固有のため Convention に含めず個別記述）
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.coroutines.test)
    debugImplementation(libs.compose.ui.test.manifest)
}
