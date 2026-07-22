plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Pure Kotlin/JVM. No Android, no Compose. This module is the app's testable
// domain core: data types, taxonomy, and the analytical logic that must be
// unit-verified (river decomposition, dedup simhash, classifier rules, the lens
// fidelity guard, the CVD palette math). Keeping it Android-free means CI and
// local dev can run these tests without an Android SDK.
dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
