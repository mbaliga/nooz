// Standalone build for the desktop head. It is deliberately not part of the root
// build: the root settings include :app, which needs the Android SDK, and the desktop
// toolchain pins (Kotlin 2.1.20, Compose Multiplatform 1.8.2) differ from the
// Android ones. Run it with the root wrapper: ../gradlew -p desktop <task>.
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "nooz-desktop"

// :core:model is the pure-JVM domain core, mapped in by directory so it is built from
// the same sources the Android app uses. Its build file reads the `libs` catalog,
// which here is desktop/gradle/libs.versions.toml (the subset it needs, on the
// desktop pins).
include(":core:model")
project(":core:model").projectDir = file("../core/model")
