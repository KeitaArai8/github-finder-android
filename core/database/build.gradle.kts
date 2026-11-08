plugins {
    alias(libs.plugins.githubfinder.android.library)
    alias(libs.plugins.githubfinder.android.hilt)
}

android {
    namespace = "com.example.githubfinder.core.database"
}

// Room のスキーマ JSON を出力してバージョン管理する（マイグレーション検証・差分レビューのため）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.coroutines.test)
}
