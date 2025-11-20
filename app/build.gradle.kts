// ✅ Import para el DSL nuevo de compilador
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
    

}

android {
    namespace = "com.mobile.mymeds"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mobile.mymeds"
        minSdk = 24
        targetSdk = 34
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
        // ✅ Java 11
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // ✅ Activa Compose
    buildFeatures { compose = true }
}

// ✅ Reemplaza el bloque deprecado kotlinOptions por compilerOptions
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        // Si necesitas flags extra:
        // freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// ✅ (Opcional pero útil) Parámetros de KSP para Room
ksp {
    arg("room.generateKotlin", "true")
    arg("room.incremental", "true")
    // arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * 🔧 Fix global: elimina com.intellij:annotations (12.0) de TODAS las configs
 * para evitar "duplicate class" con org.jetbrains:annotations.
 */
configurations.configureEach {
    exclude(group = "com.intellij", module = "annotations")
}


dependencies {
    // ✅ Fuerza 1 sola lib de anotaciones (JetBrains)
    implementation("org.jetbrains:annotations:24.1.0")

    // --- Core & Lifecycle / Compose ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // LiveData en Compose (desde catálogo)
    implementation(libs.androidx.compose.runtime.livedata)

    // Material Icons Extended (desde catálogo)
    implementation(libs.androidx.compose.material.icons.extended)

    // ML Kit Text Recognition
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    // Common (si lo usas)
    implementation(libs.vision.common)

    // Compose BOM + módulos
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // --- Firebase (BOM + KTX) ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.analytics.ktx)
    implementation(libs.firebase.storage.ktx)

    // --- Google Maps ---
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)
    implementation(libs.play.services.location)

    // --- Networking ---
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    // --- AppCompat / Navigation / Coil ---
    implementation(libs.appcompat)
    implementation(libs.navigation.compose)
    implementation(libs.coil.compose)

    // --- Room (Relacional) con KSP ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- WorkManager for background jobs ---
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // --- DataStore (KV) ---
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")


    implementation("androidx.work:work-runtime-ktx:2.9.0")
    // --- Tests ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
