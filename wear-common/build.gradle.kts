plugins {
    // AGP 9 built-in Kotlin — no kotlin.android plugin (see root build.gradle.kts).
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.dialed.app.wear.common"
    compileSdk = 36

    defaultConfig {
        // Shared by :app (phone, minSdk 30) and :wear (watch, minSdk 33) — use the lower.
        minSdk = 30
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}
