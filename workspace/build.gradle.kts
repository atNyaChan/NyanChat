plugins {
    id("rikkahub.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "me.rerere.workspace"

    defaultConfig {
        externalNativeBuild {
            cmake {
                cppFlags += ""
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.xz)
    implementation("com.github.luben:zstd-jni:1.5.7-4@aar")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
