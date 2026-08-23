// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
}

subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.api.dsl.CommonExtension> {
            buildToolsVersion = "37.0.0"
        }
    }
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.CommonExtension> {
            buildToolsVersion = "37.0.0"
        }
    }
    plugins.withId("com.android.test") {
        extensions.configure<com.android.build.api.dsl.CommonExtension> {
            buildToolsVersion = "37.0.0"
        }
    }
}
