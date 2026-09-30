plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.illuminazionetech.vrclip.color"
    compileSdk = 37

    defaultConfig { minSdk = 28 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes { release { isMinifyEnabled = false } }
}

kotlin { jvmToolchain(21) }

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.core.ktx)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
}
