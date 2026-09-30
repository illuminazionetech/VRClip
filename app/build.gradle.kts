@file:Suppress("UnstableApiUsage")

import com.android.build.api.variant.FilterConfiguration
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.room)
    alias(libs.plugins.ktfmt.gradle)
}

val splitApks = !project.hasProperty("noSplits")

val abiFilterList =
    providers.gradleProperty("ABI_FILTERS").orNull?.split(';') ?: emptyList()

val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)

val appVersion =
    resolveAppVersion(
        explicitVersionName = providers.gradleProperty(VERSION_NAME_PROPERTY).orNull,
        latestTag = {
            providers
                .exec {
                    commandLine("git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")
                    isIgnoreExitValue = true
                }
                .standardOutput
                .asText
                .orNull
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        },
    )
val baseVersionCode = appVersion.code.toInt()

/**
 * Release signing. CI writes `keystore.properties` from the KEYSTORE_B64 / KEYSTORE_PASSWORD
 * repository secrets; `keyAlias` defaults to "vrclip" and `keyPassword` to the store password
 * (PKCS12 keystores use one password for both). Without it, release builds fall back to the
 * debug key, which is fine for local testing but can never update an installed release, so the
 * release workflow refuses to publish anything that is not signed with the real key.
 */
val releaseSigning: Properties? =
    rootProject
        .file("keystore.properties")
        .takeIf { it.exists() }
        ?.let { file -> Properties().apply { FileInputStream(file).use(::load) } }

android {
    namespace = "com.illuminazionetech.vrclip"
    compileSdk = 37

    buildFeatures {
        buildConfig = true
        resValues = true
        compose = true
    }

    if (releaseSigning != null) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias") ?: "vrclip"
                keyPassword =
                    releaseSigning.getProperty("keyPassword")
                        ?: releaseSigning.getProperty("storePassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.illuminazionetech.vrclip"
        minSdk = 28
        // Stays at 36 until downloads (Python, FFmpeg and aria2c run from nativeLibraryDir) are
        // verified against the Android 17 behavior changes that come with targeting 37.
        targetSdk = 36
        versionCode = baseVersionCode
        versionName = appVersion.name
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        if (splitApks) {
            splits {
                abi {
                    isEnable = true
                    reset()
                    include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                    isUniversalApk = true
                }
            }
        } else {
            ndk { abiFilters.addAll(abiFilterList) }
        }
    }

    room { schemaDirectory("$projectDir/schemas") }
    ksp { arg("room.incremental", "true") }

    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                val abi =
                    if (splitApks) {
                        output.filters
                            .find { it.filterType == FilterConfiguration.FilterType.ABI }
                            ?.identifier
                    } else {
                        abiFilterList.firstOrNull()
                    }

                // Per-ABI APKs get +1..+4; the universal APK gets +10 so it can be installed
                // over any of the architecture-specific splits.
                output.versionCode.set(baseVersionCode + (abiCodes[abi] ?: 10))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                if (releaseSigning != null) signingConfigs.getByName("release")
                else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "VRClip Debug")
        }
    }

    flavorDimensions += "publishChannel"

    productFlavors {
        create("generic") {
            dimension = "publishChannel"
            isDefault = true
        }

        create("githubPreview") {
            dimension = "publishChannel"
            applicationIdSuffix = ".preview"
            resValue("string", "app_name", "VRClip Preview")
        }
    }

    lint {
        disable.addAll(listOf("MissingTranslation", "ExtraTranslation", "MissingQuantity"))
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        // youtubedl-android executes python/ffmpeg/aria2c straight from nativeLibraryDir, so the
        // native libraries must be extracted on install.
        jniLibs { useLegacyPackaging = true }
    }

    androidResources { generateLocaleConfig = true }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

ktfmt { kotlinLangStyle() }

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.addAll(
            "kotlin.RequiresOptIn",
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":color"))

    implementation(libs.bundles.core)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.androidxCompose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.bundles.coil)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.bundles.youtubedlAndroid)
    implementation(libs.bundles.media3)
    implementation(libs.litert)
    implementation(libs.mmkv)

    implementation(libs.meta.spatial.sdk)
    implementation(libs.meta.spatial.sdk.compose)
    implementation(libs.meta.spatial.sdk.uiset)
    implementation(libs.meta.spatial.sdk.toolkit)
    implementation(libs.meta.spatial.sdk.vr)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
