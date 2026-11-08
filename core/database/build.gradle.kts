plugins {
    alias(libs.plugins.githubfinder.android.library)
    alias(libs.plugins.githubfinder.android.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.example.githubfinder.core.database"
}

// Room Gradle Plugin（公式）でスキーマ JSON を出力してバージョン管理する（マイグレーション検証・差分レビューのため）
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.coroutines.test)
}
