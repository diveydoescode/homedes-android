import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    // AGP 9+ embeds Kotlin; do not apply org.jetbrains.kotlin.android
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

/** Production proxy origin — always used for release; never overridden by local.properties. */
val productionSketchUrl = "https://homedes-webapp.vercel.app"

/**
 * Debug default: emulator loopback to host `scripts/run-sketch-proxy.ps1` (:8787).
 * Override with `sketch.base.url` in local.properties (physical device LAN IP, etc.).
 */
val debugSketchUrl: String =
    localProperties.getProperty("sketch.base.url")
        ?: "http://10.0.2.2:8787"

android {
    namespace = "com.homedesign.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.homedesign.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Fallback; buildTypes below set the real value per variant.
        buildConfigField("String", "SKETCH_BASE_URL", "\"$productionSketchUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Release always ships the production origin (ignore local.properties).
            buildConfigField("String", "SKETCH_BASE_URL", "\"$productionSketchUrl\"")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "SKETCH_BASE_URL", "\"$debugSketchUrl\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Filament ships libc++_shared; pick first if another AAR also includes it.
            pickFirsts += "**/libc++_shared.so"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.navigation:navigation-compose:2.9.4")

    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.exifinterface:exifinterface:1.4.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")

    // Kyant Liquid Glass (https://github.com/Kyant0/AndroidLiquidGlass)
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")

    // Tier 3 — Filament 3D renderer (walls + floors extrusion in Plan3DView)
    implementation("com.google.android.filament:filament-android:1.56.0")
    implementation("com.google.android.filament:filament-utils-android:1.56.0")
    implementation("com.google.android.filament:filamat-android:1.56.0")

    // Tier 3 AR — ARCore + GLES (not SceneView: it pins Filament ≠ 1.56)
    implementation("com.google.ar:core:1.48.0")
    // Fallback when ARCore unsupported: CameraX preview + overlay simulation
    val cameraX = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
