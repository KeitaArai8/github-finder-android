/*
 * `feature` 配下 モジュール用の集約 Convention Plugin。
 * - androidLibrary + androidLibraryCompose + androidHilt を一括適用
 * - lifecycle / hilt-lifecycle-viewmodel-compose / coil 等の Feature 共通依存を追加
 * - core:model / core:designsystem を自動依存
 *
 * 使用例: `feature` 配下 の build.gradle.kts で
 *   plugins { alias(libs.plugins.githubfinder.android.feature) }
 *   android { namespace = "com.example.githubfinder.feature.xxx" }
 *   dependencies { implementation(project(":core:data")) ... }  // 各 feature 固有のみ
 */

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("githubfinder.android.library")
                apply("githubfinder.android.library.compose")
                apply("githubfinder.android.hilt")
            }

            dependencies {
                // すべての Feature が依存する基本セット
                add("implementation", project(":core:model"))
                add("implementation", project(":core:designsystem"))

                // Feature 共通: ViewModel + Lifecycle + Hilt ViewModel + Coil
                add("implementation", libs.findLibrary("hilt-lifecycle-viewmodel-compose").get())
                add("implementation", libs.findLibrary("lifecycle-runtime-compose").get())
                add("implementation", libs.findLibrary("lifecycle-viewmodel-compose").get())
                add("implementation", libs.findLibrary("coil-compose").get())
                add("implementation", libs.findLibrary("timber").get())

                // テスト共通
                add("testImplementation", libs.findLibrary("junit").get())
                add("testImplementation", libs.findLibrary("turbine").get())
                add("testImplementation", libs.findLibrary("coroutines-test").get())
            }
        }
    }
}
