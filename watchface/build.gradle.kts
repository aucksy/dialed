// The bundled Dialed DEFAULT face (Watch Face Format, resource-only, no code).
//
// This is Dialed's OWN face — hand-authored here, NOT generated from the fablecollection
// submodule (unlike facepacks/*). Onboarding installs + activates it so the marketplace owns the
// active slot; from then on every pushed face updates the slot in place and stays active. It is
// also the face uninstall reverts to, so ownership is never surrendered.
//
// Packaging mirrors the facepacks (WFP validator gotchas): minify strips the empty classes.dex the
// validator forbids (keeps arsc resource NAMES, which it resolves by), and the kotlin/** excludes
// drop AGP 9's built-in kotlin-stdlib metadata. Signed with the FACES key (must differ from :app's).
plugins { id("com.android.application") }

android {
    namespace = "com.dialed.app.watchfacepush.dialed.classic"
    compileSdk = 36

    defaultConfig {
        // WFP rule: <marketplaceAppId>.watchfacepush.<name>. Tokens are minted in CI with
        // --package_name=com.dialed.app so this installs as a Dialed-owned face.
        applicationId = "com.dialed.app.watchfacepush.dialed.classic"
        minSdk = 33
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    // Shared throwaway faces key — MUST differ from the :app signing key (WFP requirement).
    signingConfigs {
        create("faces") {
            storeFile = file("$rootDir/dialed-faces.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // AGP 9's built-in Kotlin bundles kotlin-stdlib metadata (kotlin/*.kotlin_builtins) into the
    // APK; WFP's strict entry allowlist rejects the kotlin/ dir. hasCode=false means it's unused.
    packaging {
        resources {
            excludes += listOf(
                "kotlin/**",
                "**/*.kotlin_builtins",
                "META-INF/*.kotlin_module",
                "META-INF/versions/**",
                "DebugProbesKt.bin",
            )
        }
    }

    buildTypes {
        // minify=true strips the (empty) classes.dex that WFP's file allowlist forbids. It shortens
        // res FILE PATHS but KEEPS resource NAMES in the arsc, and the validator resolves by name.
        release {
            signingConfig = signingConfigs.getByName("faces")
            isMinifyEnabled = true
            isShrinkResources = false
        }
        debug {
            signingConfig = signingConfigs.getByName("faces")
            isMinifyEnabled = true
        }
    }
}
