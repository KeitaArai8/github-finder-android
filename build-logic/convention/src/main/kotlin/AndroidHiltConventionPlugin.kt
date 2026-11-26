/*
 * Hilt + KSP の共通設定を適用する Convention Plugin。
 * - dagger.hilt.android.plugin + ksp プラグイン適用
 * - hilt-android + hilt-compiler 依存追加
 *
 * 使用例: Hilt を使う任意のモジュール（`feature` 配下 / `core:data` 等）で
 *   plugins {
 *     alias(libs.plugins.githubfinder.android.library)
 *     alias(libs.plugins.githubfinder.android.hilt)
 *   }
 */

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.google.devtools.ksp")
                apply("dagger.hilt.android.plugin")
            }

            dependencies {
                add("implementation", libs.findLibrary("hilt-android").get())
                add("ksp", libs.findLibrary("hilt-compiler").get())
            }
        }
    }
}
