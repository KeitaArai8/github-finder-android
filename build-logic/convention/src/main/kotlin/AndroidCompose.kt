/*
 * Compose 関連の共通設定。
 * Library / Application の両 Compose Convention から呼び出される。
 * Compose BOM と主要 Compose 依存ライブラリを統一注入する。
 */

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

internal fun Project.configureAndroidCompose(
    @Suppress("UNUSED_PARAMETER") commonExtension: CommonExtension,
) {
    val bom = libs.findLibrary("compose-bom").get()
    val ui = libs.findLibrary("compose-ui").get()
    val uiGraphics = libs.findLibrary("compose-ui-graphics").get()
    val uiToolingPreview = libs.findLibrary("compose-ui-tooling-preview").get()
    val uiTooling = libs.findLibrary("compose-ui-tooling").get()
    val material3 = libs.findLibrary("compose-material3").get()

    dependencies {
        add("implementation", platform(bom))
        add("androidTestImplementation", platform(bom))
        add("implementation", ui)
        add("implementation", uiGraphics)
        add("implementation", uiToolingPreview)
        add("implementation", material3)
        add("debugImplementation", uiTooling)
    }
}
