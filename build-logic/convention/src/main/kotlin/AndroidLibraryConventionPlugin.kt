/*
 * 全ての Android Library モジュール共通の設定を適用する Convention Plugin。
 * - com.android.library + kotlin-android
 * - compileSdk / minSdk / Java 17 / Kotlin JVM target 17
 *
 * 使用例: `feature` 配下 / `core` 配下 の build.gradle.kts で
 *   plugins { alias(libs.plugins.githubfinder.android.library) }
 */

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
            }
        }
    }
}
