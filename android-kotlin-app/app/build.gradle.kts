plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pulseforge.studio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pulseforge.studio"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "2.0.0-PRO"
    }

    signingConfigs {
        create("release") {
            val ksFile = file("release-keystore.jks")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASS") ?: "PulseForge2026Secure"
                keyAlias = "pulseforge_key"
                keyPassword = System.getenv("KEYSTORE_PASS") ?: "PulseForge2026Secure"
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Pure Android SDK + Kotlin Standard Library
}
