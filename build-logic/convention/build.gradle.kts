/*
 * convention plugin module。
 * GitHubFinderAndroid 全モジュール共通の Gradle 設定をここに集約する。
 *
 * 各 Convention Plugin は libs.versions.toml の plugins セクションで ID 登録され、
 * 各モジュールの build.gradle.kts で `alias(libs.plugins.githubfinder.android.xxx)` として呼び出される。
 */

plugins {
    `kotlin-dsl`
}

group = "com.example.githubfinder.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Gradle Plugin の API を compileOnly で取得（runtime には公開しない）
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "githubfinder.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "githubfinder.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "githubfinder.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "githubfinder.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "githubfinder.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidFeature") {
            id = "githubfinder.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
    }
}
