plugins {
    id("rikkahub.android.library.compose")
}

android {
    namespace = "me.rerere.material3"
    sourceSets {
        named("main") {
            kotlin.directories.add("material-color-utilities/kotlin")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
}
