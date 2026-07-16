plugins {
    // AGP 9 has built-in Kotlin — applying org.jetbrains.kotlin.android is fatal
    // (https://kotl.in/gradle/agp-built-in-kotlin). Only AGP + the Compose compiler.
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    // Distinct namespace (R/BuildConfig package) from :app, but the SAME applicationId —
    // the Wear companion ships under com.dialed.app so Play pairs the two APKs, the Data
    // Layer connects them, and (critically) the WFP validation tokens minted in CI with
    // --package_name=com.dialed.app match this app as the "marketplace" caller.
    namespace = "com.dialed.app.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dialed.app"
        minSdk = 33          // WFP itself needs API 36; gated at runtime via isSupported().
        targetSdk = 36
        versionCode = 21
        versionName = "0.21.0"
        vectorDrawables { useSupportLibrary = true }
    }

    // Same stable committed key as :app (see :app build.gradle.kts) — pins the signature across
    // builds so upgrades install in place, and keeps phone+wear on one cert so they pair.
    signingConfigs {
        getByName("debug") {
            storeFile = file("$rootDir/dialed-app-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":wear-common"))

    // Compose core (layout/Canvas/graphics) via the shared BOM; Wear Compose M3 is versioned
    // independently and pulls its own compatible compose-ui/foundation.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)

    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.datastore.preferences)

    // Watch Face Push + the phone<->watch Data Layer transport.
    implementation(libs.watchface.push)
    implementation(libs.play.services.wearable)
    implementation(libs.coroutines.play.services)
    implementation(libs.wear.remote.interactions) // RemoteActivityHelper: actually open the phone app

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.wear.compose.ui.tooling)
}
