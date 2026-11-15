plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace  = "com.aethercam.app"
    compileSdk = 34

    defaultConfig {
        applicationId   = "com.aethercam.app"
        minSdk          = 29       // Android 10
        targetSdk       = 34
        versionCode     = 100
        versionName     = "2.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        ndk { abiFilters.addAll(setOf("armeabi-v7a", "arm64-v8a", "x86_64")) }
    }

    // ── Signing ───────────────────────────────────────────────────────────────
    // Release builds in CI use the auto-generated debug keystore (safe for
    // sideloading).  For Play Store distribution, replace with a real keystore
    // supplied via GitHub Secrets and signingConfigs.getByName("release").
    signingConfigs {
        // "debug" is created automatically by the Android Gradle plugin
        // We just reference it for the release build type below.
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign release with the debug keystore so the APK is installable
            // without a Play Store signature.  Replace with real keystore for prod.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled         = false
            applicationIdSuffix     = ".debug"
            versionNameSuffix       = "-debug"
        }
    }

    // ── Compilation ───────────────────────────────────────────────────────────
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    // ── Build Features ────────────────────────────────────────────────────────
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

// ── Dependencies ──────────────────────────────────────────────────────────────
dependencies {
    // AndroidX core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Camera2 + CameraX
    val cameraXVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Security / Crypto
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // QR Code scanning
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")

    // JSON
    implementation("org.json:json:20231013")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
