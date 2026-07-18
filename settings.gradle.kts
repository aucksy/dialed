pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Transitive dep of the runtime WFP validator (debug sideload, Phase 4A).
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.xgouchet") }
        }
    }
}

rootProject.name = "Dialed"

// :wear-common = shared phone<->watch protocol. :wear = the WFP bridge (Phase 3-4).
// :watchface = the hand-authored Dialed DEFAULT face (bundled into :wear assets by CI so
// onboarding can install+activate it and own the active slot; also the uninstall revert target).
include(":app", ":wear-common", ":wear", ":watchface")

// facepacks/ holds one generated Gradle module per bundled face (tools/gen-facepacks.mjs).
// Each reuses WFF resources from the `faces/` submodule and only overrides applicationId
// to com.dialed.app.watchfacepush.<series>.<face>. Include whichever are present.
val facepacksDir = file("facepacks")
if (facepacksDir.isDirectory) {
    facepacksDir.listFiles()
        ?.filter { it.isDirectory && file("${it.path}/build.gradle.kts").exists() }
        ?.sortedBy { it.name }
        ?.forEach { include(":facepacks:${it.name}") }
}
