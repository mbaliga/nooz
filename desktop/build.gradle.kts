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
    }
}

compose.desktop {
    application {
        mainClass = "xyz.mdhv.riverwip.desktop.MainKt"
    }
}
