import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Compose Desktop skeleton: compiles and opens an empty window, nothing more.
// It does not read feeds, store anything or touch the network, and it has not been
// launched on a desktop from this container (no display here).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    // Applied by :core:model; declared here so the plugins share one classpath and version.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

kotlin {
    jvmToolchain(17)
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    sourceSets {
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
            implementation(project(":core:model"))
        }
        // Desktop-only test source set that compiles the pure-JVM Kokoro files straight
        // out of :core:inference (which is an Android module and cannot be built here)
        // and runs their characterization tests. KokoroLexicon imports android.content, so
        // it is left out and src/jvmTest holds a map-backed test double of the same name
        // for the two files that take one. Nothing here edits the root build; the tests
        // under core/inference also run inside the Android module in ci.yml.
        jvmTest {
            kotlin {
                srcDir("../core/inference/src/main/kotlin")
                srcDir("../core/inference/src/test/kotlin")
                include("**/inference/local/kokoro/**")
                exclude("**/KokoroLexicon.kt")
            }
            dependencies {
                implementation(libs.junit)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "xyz.mdhv.riverwip.desktop.MainKt"
    }
}
