plugins {
    alias(libs.plugins.githubfinder.android.library)
    alias(libs.plugins.githubfinder.android.hilt)
}

android {
    namespace = "com.example.githubfinder.feature.widget"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.coroutines.android)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.coroutines.test)
}
