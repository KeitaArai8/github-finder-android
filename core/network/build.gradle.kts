plugins {
    alias(libs.plugins.githubfinder.android.library)
    alias(libs.plugins.githubfinder.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.githubfinder.core.network"

    // デバッグ時だけ HTTP ログを出すため BuildConfig.DEBUG を参照する
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
