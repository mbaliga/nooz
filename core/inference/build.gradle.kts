plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "${property("riverwip.packageBase")}.inference"
    compileSdk = 35

    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // `full` may add ML Kit GenAI; `foss` must stay free of proprietary deps.
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") { dimension = "distribution" }
        create("full") { dimension = "distribution" }
    }
}

dependencies {
    api(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
