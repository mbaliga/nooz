plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "${property("riverwip.packageBase")}"
    compileSdk = 35

    defaultConfig {
        // Working applicationId. Final name/package is RESERVED — the late
        // rename sweep changes `riverwip.packageBase` (gradle.properties) plus
        // this file's flavor suffixes and AppInfo.kt only.
        applicationId = "${property("riverwip.packageBase")}"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Two flavors: `foss` (F-Droid-eligible; zero proprietary/Play deps) and
    // `full` (may add ML Kit GenAI). All features work in foss.
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
            applicationIdSuffix = ".foss"
            versionNameSuffix = "-foss"
        }
        create("full") {
            dimension = "distribution"
            applicationIdSuffix = ".full"
            versionNameSuffix = "-full"
        }
    }

    // A fixed debug keystore, committed to the repo (owner: "every time I get
    // a new apk, I get 'app not installed'"). CI runs on ephemeral runners
    // with no persisted `~/.android/debug.keystore`, so without this AGP's
    // implicit debug signingConfig generates a brand-new random key on every
    // run — a real Android install refuses to update an app whose new APK
    // isn't signed with the same key as the one already installed, so every
    // fresh CI build conflicted with whichever build came before it. This
    // keystore is debug-only (never used for the release signingConfig below,
    // never uploaded anywhere) — standard "androiddebugkey"/"android"
    // credentials, safe to commit.
    signingConfigs {
        getByName("debug") {
            storeFile = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Release signing, deliberately NOT committed to the repo (unlike the
        // debug keystore above) — this key controls the app's identity on the
        // Play Store, so it lives only as a local `keystore.properties` (see
        // `keystore.properties.example`, gitignored) or, in CI, as decoded
        // GitHub Actions secrets passed through these env vars (see
        // `.github/workflows/release.yml`). Left unset — not a build failure —
        // when neither source is present, since only the release-bundling task
        // actually needs it; every other CI job (debug assemble, unit tests)
        // never touches this config.
        create("release") {
            val props = java.util.Properties()
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) propsFile.inputStream().use { props.load(it) }

            val path = props.getProperty("storeFile") ?: System.getenv("RELEASE_KEYSTORE_PATH")
            if (path != null) storeFile = file(path)
            storePassword = props.getProperty("storePassword") ?: System.getenv("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = props.getProperty("keyAlias") ?: System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = props.getProperty("keyPassword") ?: System.getenv("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // App optimization (owner's #15): R8 code shrinking + obfuscation and
            // resource shrinking, so the release download is as light as we can
            // make it. R8 runs in full mode (AGP 8 default). Debug stays fast to
            // build and is the only variant the routine CI workflow assembles —
            // release only builds on-demand, via release.yml.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Per-device downloads from the App Bundle stay small — one language, one
    // density, one ABI per install instead of the universal APK.
    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    implementation(project(":core:inference"))
    implementation(project(":feature:sources"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:river"))
    implementation(project(":feature:lens"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
