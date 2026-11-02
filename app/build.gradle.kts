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
        // Deliberately 30 (Android 11), for two reasons:
        //  - apps targeting 33+ can't turn Bluetooth off and on; 32 or lower still can;
        //  - the hidden "connect this device" calls used for reconnecting are only
        //    reachable by apps targeting 30 or lower.
        // This app is sideloaded only, so the Play Store's target-SDK rules don't apply.
        targetSdk = 30
        versionCode = 4
        versionName = "1.3"
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
