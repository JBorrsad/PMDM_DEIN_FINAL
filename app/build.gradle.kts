plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    id("com.google.gms.google-services") // Firebase
}

android {
    namespace = "com.example.perros"
    compileSdk = 35 // ✅ Mantener actualizado con la última versión de Android

    viewBinding {
        enable = true
    }

    defaultConfig {
        applicationId = "com.example.perros"
        minSdk = 26
        targetSdk = 34 // ✅ Mejor mantenerlo igual que compileSdk
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
}

dependencies {


    implementation("com.google.android.material:material:1.11.0") // Última versión de Material Design


    // 🔥 UCrop (para recortar imágenes)
    implementation("com.github.yalantis:ucrop:2.2.6")

    // 🔥 Firebase (usando BOM para manejar versiones)
    implementation(platform("com.google.firebase:firebase-bom:33.9.0")) // ❌ Eliminé la versión anterior
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-database-ktx:21.0.0")
    implementation("com.google.firebase:firebase-messaging-ktx:23.2.1")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // 🔥 Google Maps y Localización
    implementation("com.google.android.gms:play-services-maps:18.1.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // 🔥 AndroidX y Material Design
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // 🔥 Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17) // ✅ Mejor usar Java 17 en lugar de 11
    }
}
