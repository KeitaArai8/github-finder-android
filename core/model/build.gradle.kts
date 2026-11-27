/*
 * Android 依存を持たない純粋な Kotlin/JVM モジュール（ドメインモデル / 共有型のみ）。
 * Android Gradle Plugin の Convention は適用しないため、Java 17 と警告のエラー扱い（-PwarningsAsErrors=true）をここで設定する。
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(
            providers.gradleProperty("warningsAsErrors").map(String::toBoolean).orElse(false),
        )
    }
}
