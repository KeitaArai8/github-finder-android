/*
 * 複数モジュールが共有する Dispatcher の Qualifier と、アプリ寿命の CoroutineScope（@ApplicationScope）の Provider。
 * Now in Android の :core:common は JVM ライブラリだが、ここは @ApplicationScope の Provider が Timber（Android ライブラリ）で
 * 未捕捉例外をログに残すため Android ライブラリにしている。
 */

plugins {
    alias(libs.plugins.githubfinder.android.library)
    alias(libs.plugins.githubfinder.android.hilt)
}

android {
    namespace = "com.example.githubfinder.core.common"
}

dependencies {
    implementation(libs.coroutines.android)
    implementation(libs.timber)
}
