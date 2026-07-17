plugins {
    // AGP 9 has built-in Kotlin support — applying org.jetbrains.kotlin.android is now
    // fatal (https://kotl.in/gradle/agp-built-in-kotlin). Only AGP + the Compose compiler.
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.dialed.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dialed.app"
        minSdk = 30
        targetSdk = 36
        versionCode = 23
        versionName = "0.23.0"
        vectorDrawables { useSupportLibrary = true }
    }

    // STABLE signing key (committed, throwaway) — NOT the ephemeral per-CI-run debug keystore.
    // A regenerated debug key each build gave every release a different signature, so an
    // in-place upgrade was rejected ("app not compatible / can't install"). This pins one key so
    // upgrades install over each other forever. Distinct from dialed-faces.keystore (WFP rule:
    // face APKs must be signed with a different key than the marketplace app). :app and :wear
    // share this key so the Data Layer still pairs the two APKs.
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
                "proguard-rules.pro"
            )
            // Play/upload signing is provided via CI secrets at store time (Phase 6).
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
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":wear-common"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.splashscreen)
    implementation(libs.datastore.preferences)

    // Phase 2 (billing) + Phase 4 (phone->watch Data Layer transport)
    implementation(libs.billing.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.coroutines.play.services)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
