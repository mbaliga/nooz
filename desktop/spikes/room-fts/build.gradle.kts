import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Spike K1: does the existing @Fts4(unicode61) article_text table, the real Entities.kt and
// the real DAOs, work on Room KMP + BundledSQLiteDriver on a desktop JVM? Pins follow the
// OQ-17 staged pin (Kotlin 2.1.20). Room 2.7.1 is the first line with KMP support.
plugins {
    kotlin("multiplatform") version "2.1.20"
    id("com.google.devtools.ksp") version "2.1.20-2.0.1"
    id("androidx.room") version "2.7.1"
}

val roomVersion = "2.7.1"
val dataDir = layout.projectDirectory.dir("../../../core/data")

kotlin {
    jvmToolchain(17)
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    sourceSets {
        commonMain {
            // The app's real entities and DAOs, mapped in by directory. Only RiverDatabase.kt
            // (Android Context, SupportSQLiteDatabase) is replaced by the spike's own copy.
            kotlin.srcDir(dataDir.dir("src/main/kotlin"))
            kotlin.include("xyz/mdhv/riverwip/data/db/**")
            kotlin.exclude("**/data/db/RiverDatabase.kt")
            dependencies {
                implementation("androidx.room:room-runtime:$roomVersion")
                implementation("androidx.sqlite:sqlite-bundled:2.5.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
        jvmTest.dependencies {
            implementation(kotlin("test-junit"))
            implementation("androidx.room:room-testing:$roomVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
        }
    }
}

dependencies {
    add("kspJvm", "androidx.room:room-compiler:$roomVersion")
}

// Room writes the schema it generates here, never into core/data/schemas, so the checked-in
// v3/v4 JSON is untouched and can be compared with what Room KMP emits.
room { schemaDirectory(layout.buildDirectory.dir("schemas-out").get().asFile.path) }

// The checked-in schemas, copied under the database's own class name for the migration helper.
val copyAppSchemas by tasks.registering(Copy::class) {
    from(dataDir.dir("schemas"))
    into(layout.buildDirectory.dir("schemas-in"))
}
tasks.withType<Test>().configureEach {
    dependsOn(copyAppSchemas)
    systemProperty("spike.schemas.in", layout.buildDirectory.dir("schemas-in").get().asFile.path)
    systemProperty("spike.schemas.out", layout.buildDirectory.dir("schemas-out").get().asFile.path)
    systemProperty("spike.schemas.app", dataDir.dir("schemas").asFile.path)
    testLogging { showStandardStreams = true; events("passed", "failed", "skipped") }
}

// Kotlin Multiplatform has no plain `test` task; alias it so `gradlew test` works.
tasks.register("test") { dependsOn("jvmTest") }
