import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.DokkaConfiguration.Visibility
import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("org.jetbrains.dokka")
}

android {
    namespace = "com.example.perros"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.perros"
        minSdk = 26
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    
    // Maps y Location
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // uCrop para recorte de imágenes
    implementation("com.github.yalantis:ucrop:2.2.8")

    // AndroidX
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Glide para cargar imágenes
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Coil para cargar imágenes
    implementation("io.coil-kt:coil:2.4.0")
    
    // Forzar versiones específicas de androidx.core para evitar conflictos
    configurations.all {
        resolutionStrategy {
            force("androidx.core:core-ktx:1.12.0")
            force("androidx.core:core:1.12.0")
        }
    }

    // La dependencia de CircleCropTransformation está incluida en el paquete principal de Coil

    // Google Sign In
    implementation("com.google.android.gms:play-services-auth:20.5.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

// Configuración de Dokka para el módulo "app"
tasks.withType<DokkaTask>().configureEach {
    outputDirectory.set(layout.buildDirectory.dir("dokka/html").map { it.asFile })

    dokkaSourceSets {
        named("main") {
            includes.from(rootProject.file("README.md"))
            documentedVisibilities.set(setOf(
                org.jetbrains.dokka.DokkaConfiguration.Visibility.PUBLIC,
                org.jetbrains.dokka.DokkaConfiguration.Visibility.PROTECTED
            ))
            sourceLink {
                localDirectory.set(file("src/main/java"))
                remoteUrl.set(uri("https://github.com/tu-usuario/perros/blob/main/app/src/main/java").toURL())
                remoteLineSuffix.set("#L")
            }
        }
    }
}
