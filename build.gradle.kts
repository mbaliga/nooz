// Top-level build file. Plugins are declared here with `apply false` so each
// module opts in via the version catalog. House style: manual DI, no Hilt/Koin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// The i18n ratchet. See gradle/i18n/verify-i18n.gradle.kts for what it checks
// and why Android Lint's HardcodedText cannot do this job.
apply(from = "gradle/i18n/verify-i18n.gradle.kts")
