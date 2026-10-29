plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.real3rr0r303yt.jarvis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.real3rr0r303yt.jarvis"
        minSdk = 26          // Android 8.0 — required for modern AccessibilityService gesture APIs
        targetSdk = 35
        versionCode = 9
        versionName = "1.1.5"

        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            // Sign release builds so the published APK is installable via
            // sideload. This uses the auto-generated debug keystore, which is
            // fine for a personal, open-source build. For Play Store uploads or
            // a stable update-signing identity, replace this with a dedicated
            // keystore (e.g. loaded from CI secrets).
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
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
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Kotlin coroutines for async command execution and service work
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Shizuku — optional elevated (ADB-level) permissions without root.
    // The app degrades gracefully to standard APIs when Shizuku is absent.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
