/*
 * テスト専用の共有モジュール（MainDispatcherRule と、複数モジュールで共有する Fake）。
 * テスト以外のモジュールからは依存しない。Hilt は使わない。
 */

plugins {
    alias(libs.plugins.githubfinder.android.library)
}

android {
    namespace = "com.example.githubfinder.core.testing"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))

    // MainDispatcherRule のシグネチャ（TestDispatcher / TestWatcher）が公開 API に現れるため api で公開する
    api(libs.coroutines.test)
    api("junit:junit:4.13.2")
}
