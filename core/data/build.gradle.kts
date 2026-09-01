plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "${property("riverwip.packageBase")}.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric reads the exported Room schemas as assets; without this
        // the merged test assets are never built and MigrationTestHelper can
        // only report the schema file as missing.
        unitTests.isIncludeAndroidResources = true
    }

    // The exported schemas are test fixtures as much as build output, so the
    // JVM test source set can read them straight from the tree.
    sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
}

// Schema JSONs are checked in from here on (D37). The app has shipped, so "just
// wipe it" stopped being an acceptable answer to a schema change — exported
// schemas are what let a migration be *tested* rather than asserted, and
// MigrationTestHelper reads them from this directory.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // Full-text extraction (Readability-style) when feeds truncate.
    implementation(libs.jsoup)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    // MigrationTestHelper needs an Instrumentation; under Robolectric that
    // comes from androidx.test, and the schema JSONs are reached through the
    // test source set's assets (wired above).
    testImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.work.testing)
}
