/*
 * Android + Kotlin の共通ビルド設定（compileSdk / minSdk / Java バージョン。Kotlin は built-in Kotlin で Java 設定から継承）。
 * Application / Library 両方の Convention から呼び出される。
 */

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

/**
 * Android プロジェクトの SDK / Java / Kotlin バージョンを設定する。
 * compileSdk / minSdk は全モジュール共通。
 */
internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension,
) {
    commonExtension.apply {
        compileSdk = 37
        defaultConfig.apply {
            minSdk = 26
        }
        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
    // Kotlin の jvmTarget は AGP 9 の built-in Kotlin が compileOptions.targetCompatibility から自動で決めるため、明示設定しない。
}
