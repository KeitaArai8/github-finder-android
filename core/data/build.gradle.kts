plugins {
    alias(libs.plugins.githubfinder.android.library)
    alias(libs.plugins.githubfinder.android.hilt)
}

android {
    namespace = "com.example.githubfinder.core.data"
}

dependencies {
    // @ApplicationScope / @Dispatcher を使う core:data の依存先（feature:favorite / feature:widget 等）にも公開する
    api(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(libs.coroutines.android)
    implementation(libs.timber)
    implementation(libs.datastore.preferences)
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.retrofit)
}
