pluginManagement {
    // build-logic を Composite Build として含める（Convention Plugin を提供）
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GitHubFinderAndroid"

include(":app")
include(":core:model")
include(":core:data")
include(":core:network")
include(":core:database")
include(":core:designsystem")
include(":core:testing")
include(":core:tutorial")
include(":feature:search")
include(":feature:favorite")
include(":feature:settings")
include(":feature:widget")
