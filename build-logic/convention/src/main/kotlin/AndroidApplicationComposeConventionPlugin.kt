/*
 * Compose を使う App ターゲット用の Convention Plugin。
 * androidApplication との組み合わせで使用する。
 */

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val extension = extensions.getByType<ApplicationExtension>()
            extension.buildFeatures {
                compose = true
            }

            configureAndroidCompose(extension)
        }
    }
}
