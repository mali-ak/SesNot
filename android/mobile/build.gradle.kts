plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Konuşmacı ayrımı: sherpa-onnx Maven'da yok, GitHub sürümlerinden AAR olarak gelir.
// `mobile/libs/sherpa-onnx.aar` varsa gerçek uygulama (src/sherpa) derlenir, yoksa stub (src/stub).
val sherpaAar = file("libs/sherpa-onnx.aar")
val hasSherpa = sherpaAar.exists()

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
    sourceSets {
        getByName("main").java.srcDir(if (hasSherpa) "src/sherpa/java" else "src/stub/java")
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    if (hasSherpa) implementation(files(sherpaAar))

    // Gemini Nano (AICore) ile cihaz üstü özet; destekli cihazlarda otomatik kullanılır, yoksa yedek özetleyici.
    implementation("com.google.mlkit:genai-prompt:1.0.0-alpha1")
}
