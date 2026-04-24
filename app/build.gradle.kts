plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.example.neodocscanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.neodocscanner"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }

    // Prevent aapt from compressing .tflite model file so TFLite can mmap it
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM — pins all Compose library versions consistently
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)

    // Hilt — DI framework (mandatory per architecture spec)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Room — local database (replaces SwiftData)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore — replaces @AppStorage / UserDefaults
    implementation(libs.datastore.preferences)

    // Coroutines — replaces Swift async/await + Task
    implementation(libs.kotlinx.coroutines.android)

    // Lifecycle ViewModel + Runtime Compose (StateFlow, collectAsState)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Gson — JSON serialization for extractedFields / extractedRegions blobs
    implementation(libs.gson)

    // ML Kit — Document Scanner (camera-based scanning, replaces VNDocumentCameraViewController)
    implementation(libs.mlkit.document.scanner)
    // ML Kit — Text Recognition (OCR, replaces Vision framework)
    implementation(libs.mlkit.text.recognition)
    // ML Kit — Barcode/QR scanner (for QR onboarding link flow)
    implementation(libs.mlkit.code.scanner)
    // TensorFlow Lite — document image classification
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)
    // Coil — async image loading from file paths (thumbnails)
    implementation(libs.coil.compose)

    // Reorderable — drag-to-reorder for the page reorder sheet
    // (handles LazyColumn recycling, edge auto-scroll, smooth animations)
    implementation(libs.reorderable)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
