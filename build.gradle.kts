plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // Declared here only to put the Kotlin Gradle plugin version from the catalog on the build
    // classpath; AGP 9 compiles Kotlin itself (built-in Kotlin) and uses that KGP.
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.ktfmt.gradle) apply false
}

tasks.register("clean", Delete::class) { delete(rootProject.layout.buildDirectory) }
