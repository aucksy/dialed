// Top-level build file. Plugin versions come from gradle/libs.versions.toml.
plugins {
    // AGP 9 provides built-in Kotlin; the standalone kotlin.android plugin must not be
    // applied anywhere (https://kotl.in/gradle/agp-built-in-kotlin).
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
}
