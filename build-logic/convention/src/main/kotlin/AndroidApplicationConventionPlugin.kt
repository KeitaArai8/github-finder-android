/*
 * App ターゲット用の Convention Plugin。
 * - com.android.application （Kotlin は AGP 9 の built-in Kotlin を使うため kotlin-android は適用しない）
 * - compileSdk / minSdk / targetSdk / Java 17 / Kotlin JVM target 17（compileOptions から継承）
 * - buildConfig 有効化（BuildConfig.DEBUG を Kotlin から参照するため）
 *
 * 使用例: app/build.gradle.kts で
 *   plugins { alias(libs.plugins.githubfinder.android.application) }
 */

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = 36
                buildFeatures {
                    buildConfig = true
                }
            }
        }
    }
}
