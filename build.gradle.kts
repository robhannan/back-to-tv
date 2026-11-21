plugins {
    alias(libs.plugins.android.application) apply false
    // Puts the pinned Kotlin Gradle Plugin on the classpath for AGP's built-in Kotlin support; it is never applied.
    alias(libs.plugins.kotlin.android) apply false
}
