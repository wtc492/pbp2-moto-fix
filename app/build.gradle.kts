plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.budfix"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.budfix"
        minSdk = 31
        // Deliberately 32 (Android 12L). Apps targeting 33+ are no longer allowed to
        // turn Bluetooth off and on; apps targeting 32 or lower still are. This app is
        // for sideloading only, so the Play Store's target-SDK rules don't apply.
        targetSdk = 32
        versionCode = 2
        versionName = "1.1"
    }

    // A fixed debug key, so each GitHub build can install over the previous one.
    // (GitHub's build machines otherwise make a new random key every run.)
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // The old targetSdk is intentional (see above).
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
