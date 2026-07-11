plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "${property("riverwip.packageBase")}.feature.reader"
    compileSdk = 35

    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }

    // The reader embeds :feature:lens (which depends on the flavored
    // :core:inference), so it needs the same flavor dimension declared —
    // otherwise Gradle's variant-aware resolution has no matching variant to
    // pick. Reader itself has no flavor-specific source of its own.
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") { dimension = "distribution" }
        create("full") { dimension = "distribution" }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    implementation(project(":feature:lens"))

    // FileProvider + ResourcesCompat for the newspaper-share image.
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
