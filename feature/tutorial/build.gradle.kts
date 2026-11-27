/*
 * Tutorial Feature モジュール。初回起動のチュートリアル画面・コーチマーク・ステップ管理と、その文言を置く。
 * androidFeature Convention で共通設定を継承。
 */

plugins {
    alias(libs.plugins.githubfinder.android.feature)
}

android {
    namespace = "com.example.githubfinder.feature.tutorial"
}

dependencies {
    // CoachMarkManager が TutorialRepository を使う
    implementation(project(":core:data"))
}
