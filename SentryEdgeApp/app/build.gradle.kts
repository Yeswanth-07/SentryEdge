plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.enerix.sentryscan"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.enerix.sentryscan"
        minSdk = 26          // VpnService + ConnectivityManager.getConnectionOwnerUid need 29,
        targetSdk = 34       // but we gate that call behind a Build.VERSION check so the app still
        versionCode = 1      // installs (with reduced attribution) on 26-28.
        versionName = "0.2.0-arm"

        // Build only for 64-bit Arm. Every modern Android phone (and Arm dev boards that run
        // Android) uses arm64-v8a, and ExecuTorch's prebuilt LLM libraries ship for it. This
        // also keeps the APK smaller. Side effect: the x86 emulator can no longer run this
        // build, so test on a real phone.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        // Real threat-intel API keys, read from gradle.properties (or -P flags) so they never
        // get hardcoded into source or committed to git. Empty string = that source is skipped
        // at runtime with an honest "no API key configured" result instead of crashing.
        buildConfigField("String", "VIRUSTOTAL_API_KEY", "\"${project.findProperty("VIRUSTOTAL_API_KEY") ?: ""}\"")
        buildConfigField("String", "ABUSEIPDB_API_KEY", "\"${project.findProperty("ABUSEIPDB_API_KEY") ?: ""}\"")
        buildConfigField("String", "PHISHTANK_API_KEY", "\"${project.findProperty("PHISHTANK_API_KEY") ?: ""}\"")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room for on-device-only storage (never synced anywhere)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // On-device LLM runtime (ExecuTorch, from Maven Central). It runs a Llama 3.2 .pte model on
    // the phone's Arm CPU through XNNPACK. No network is used for inference. If Gradle cannot
    // resolve this exact version, open Maven Central for org.pytorch:executorch-android and
    // use the newest version listed there.
    implementation("org.pytorch:executorch-android:1.5.1")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
