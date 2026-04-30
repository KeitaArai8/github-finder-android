/*
 * build-logic の独立 Settings。
 * convention plugin を Composite Build として組み込むためのエントリポイント。
 * NiA の build-logic と同じ構成。
 */

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
