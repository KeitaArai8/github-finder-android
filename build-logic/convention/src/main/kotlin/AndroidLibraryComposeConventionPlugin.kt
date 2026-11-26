/*
 * Compose を使う Library モジュール用の Convention Plugin。
 * - androidLibrary を前提に kotlin-compose を追加
 * - buildFeatures.compose = true
 * - Compose BOM + 主要 Compose ライブラリを依存注入
 *
 * 使用例: `feature` 配下 / `core:designsystem` の build.gradle.kts で
 *   plugins {
 *     alias(libs.plugins.githubfinder.android.library)
 *     alias(libs.plugins.githubfinder.android.library.compose)
 *   }
 */

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val extension = extensions.getByType<LibraryExtension>()
            extension.buildFeatures {
                compose = true
            }

            configureAndroidCompose(extension)
        }
    }
}
