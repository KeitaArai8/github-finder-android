/*
 * App ターゲット。
 * Convention Plugin により compileSdk / minSdk / Compose / Hilt の標準設定を継承。
 * このファイルでは「app 固有の設定」と「依存」のみ記述する。
 */

plugins {
    alias(libs.plugins.githubfinder.android.application)
    alias(libs.plugins.githubfinder.android.application.compose)
    alias(libs.plugins.githubfinder.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.githubfinder"
    defaultConfig {
        applicationId = "com.example.githubfinder"
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            // AGP 9.4 の `optimization { }` DSL は @Incubating のため、安定版の isMinifyEnabled / isShrinkResources を使う
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:search"))
    implementation(project(":feature:favorite"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:widget"))

    implementation(libs.activity.compose)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
