plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.azhulabs.sesnot.mobile"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.azhulabs.sesnot"   // saat uygulamasıyla aynı olmalı (Data Layer eşleşmesi)
        minSdk = 31                              // createOnDeviceSpeechRecognizer; dosya kaynağı için 33 gerekir
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")   // ML Kit ListenableFuture.await()

    // Konuşmacı ayrımı (pyannote segmentation + 3D-Speaker embedding, tamamen cihaz üstü).
    // Modeller: bkz. mobile/README.md → assets/diarization/
    implementation("com.k2fsa.sherpa.onnx:sherpa-onnx:1.10.30")

    // Gemini Nano (AICore) ile cihaz üstü özet; destekli cihazlarda otomatik kullanılır, yoksa yedek özetleyici.
    implementation("com.google.mlkit:genai-prompt:1.0.0-alpha1")
}
