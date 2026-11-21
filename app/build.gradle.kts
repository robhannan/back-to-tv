plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.robhannan.backtotv"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.robhannan.backtotv"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.okhttp)
}
