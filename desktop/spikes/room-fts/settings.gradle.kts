// Throwaway spike K1, CONTAINER-BUILD-ONLY (linux-x64 JVM). Standalone build: not part of
// the root build, not part of ../../settings.gradle.kts, and not run by desktop-linux.yml.
// Run: ../../../gradlew test --stacktrace
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

rootProject.name = "room-fts-spike"
