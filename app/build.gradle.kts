import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.DokkaConfiguration.Visibility
import java.net.URL
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("org.jetbrains.dokka")
    id("kotlin-parcelize")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }

    // Configuración global para todas las tareas de Kotlin
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-Xjvm-default=all",
                "-Xsuppress-version-warnings"
            )
        }
    }
}

dependencies {
    // Agregar soporte para desugaring de características de Java 8+
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    // Dependencias principales de Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Usar annotationProcessor en lugar de kapt para Room
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    
    // Usar annotationProcessor en lugar de kapt para Lifecycle
    annotationProcessor("androidx.lifecycle:lifecycle-compiler:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    
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
    implementation("com.google.maps.android:android-maps-utils:2.4.0")

    // uCrop para recorte de imágenes
    implementation("com.github.yalantis:ucrop:2.2.8")

    // AndroidX
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Glide para carga de imágenes y transformaciones
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

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

// Configuración de Dokka para el módulo "app"
tasks.withType<DokkaTask>().configureEach {
    moduleName.set("app")
    failOnWarning.set(false)  // Para evitar fallos por advertencias
    suppressObviousFunctions.set(true)  // Para evitar documentar getters/setters obvios
    suppressInheritedMembers.set(false)  // Para mantener miembros heredados
    outputDirectory.set(File(buildDir, "dokka/html"))

    dokkaSourceSets {
        named("main") {
            includes.from("src/main/kotlin/module.md")
            includes.from("src/main/kotlin/com.example.perros.md")
            
            documentedVisibilities.set(setOf(
                org.jetbrains.dokka.DokkaConfiguration.Visibility.PUBLIC,
                org.jetbrains.dokka.DokkaConfiguration.Visibility.PROTECTED
            ))
            
            // Configura Dokka para manejar correctamente nuestros comentarios
            reportUndocumented.set(false)
            skipEmptyPackages.set(true)
            skipDeprecated.set(false)
            
            sourceLink {
                localDirectory.set(file("src/main/java"))
                remoteUrl.set(URI("https://github.com/tu-usuario/perros/blob/main/app/src/main/java").toURL())
                remoteLineSuffix.set("#L")
            }
        }
    }

    // Agregar configuración para mejorar la generación de la navegación lateral
    pluginsMapConfiguration.set(
        mapOf(
            "org.jetbrains.dokka.base.DokkaBase" to """
                {
                    "customStyleSheets": ["${file("src/main/kotlin/navigation-fix.css").absolutePath.replace("\\", "/")}"],
                    "customAssets": ["${file("src/main/kotlin/fix-navigation.js").absolutePath.replace("\\", "/")}"],
                    "separateInheritedMembers": true,
                    "renderSourceSetFileSizes": false
                }
            """.trimIndent()
        )
    )
}

// Tarea para servir la documentación con un servidor HTTP simple sin dependencias externas
tasks.register("serveDocs") {
    dependsOn("dokkaHtml")
    group = "documentation"
    description = "Genera y sirve la documentación usando un servidor integrado en Gradle (no requiere Python)"

    doLast {
        val dokkaOutputDir = layout.buildDirectory.dir("dokka/html").get().asFile
        val port = 8090
        
        println("\n")
        println("=".repeat(80))
        println("Iniciando servidor de documentación en: http://localhost:$port")
        println("La documentación está disponible en: ${dokkaOutputDir.absolutePath}")
        println("=".repeat(80))
        println("\n")
        
        // Abre el navegador automáticamente
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            exec {
                commandLine("cmd", "/c", "start", "http://localhost:$port")
            }
        } else if (System.getProperty("os.name").lowercase().contains("mac")) {
            exec {
                commandLine("open", "http://localhost:$port")
            }
        } else {
            exec {
                commandLine("xdg-open", "http://localhost:$port")
            }
        }
        
        // Inicia un proceso de servidor usando un script Python integrado
        val pythonScript = createTempServerScript(dokkaOutputDir.absolutePath, port)
        exec {
            commandLine("python", pythonScript.absolutePath)
        }
    }
}

// Crea un script temporal de Python para iniciar un servidor web simple
fun createTempServerScript(docRoot: String, port: Int): File {
    val script = File.createTempFile("dokka_server", ".py")
    script.deleteOnExit()
    
    script.writeText("""
import os
import sys
import webbrowser
from http.server import HTTPServer, SimpleHTTPRequestHandler
import socketserver
import threading

# Configuración del servidor
PORT = $port
DIRECTORY = r"$docRoot"

class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)
    
    def log_message(self, format, *args):
        # Sobreescribe el método para mostrar mensajes más amigables
        sys.stderr.write("[%s] %s\\n" % (self.log_date_time_string(), format % args))

# Configura e inicia el servidor
os.chdir(DIRECTORY)
httpd = socketserver.TCPServer(("", PORT), Handler)

print(f"\\nServidor iniciado en http://localhost:{PORT}")
print(f"Presiona Ctrl+C para detener el servidor\\n")

try:
    httpd.serve_forever()
except KeyboardInterrupt:
    print("\\nServidor detenido por usuario")
    httpd.server_close()
""".trimIndent())
    
    return script
}

// Actualiza la tarea serveDocumentation para corregir problemas de PowerShell
tasks.register<Exec>("serveDocumentation") {
    dependsOn("dokkaHtml")
    group = "documentation"
    description = "Genera la documentación con Dokka y la sirve en http://localhost:8080 usando Python"
    
    val dokkaDir = layout.buildDirectory.dir("dokka/html").get().asFile.absolutePath
    
    doFirst {
        println("Servidor de documentación iniciado en: http://localhost:8080")
        println("Presiona Ctrl+C para detener el servidor")
    }
    
    // Configuración para Windows (PowerShell)
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        workingDir = File(dokkaDir)
        commandLine("cmd", "/c", "start http://localhost:8080 && python -m http.server 8080")
    } else {
        // Configuración para Linux/Mac
        workingDir = File(dokkaDir)
        commandLine("sh", "-c", "(xdg-open http://localhost:8080 || open http://localhost:8080 || true) && python -m http.server 8080")
    }
}

// Actualiza la tarea viewDocumentation para usar la API moderna de Gradle
tasks.register("viewDocumentation") {
    dependsOn("dokkaHtml")
    group = "documentation"
    description = "Genera la documentación con Dokka y abre el archivo index.html en el navegador predeterminado"

    doLast {
        println("Abriendo documentación en el navegador...")
        val dokkaOutputDir = layout.buildDirectory.dir("dokka/html").get().asFile
        val indexHtml = File(dokkaOutputDir, "index.html")
        
        if (indexHtml.exists()) {
            if (System.getProperty("os.name").lowercase().contains("windows")) {
                exec {
                    commandLine("cmd", "/c", "start", indexHtml.absolutePath)
                }
            } else if (System.getProperty("os.name").lowercase().contains("mac")) {
                exec {
                    commandLine("open", indexHtml.absolutePath)
                }
            } else {
                exec {
                    commandLine("xdg-open", indexHtml.absolutePath)
                }
            }
            println("Documentación abierta en: ${indexHtml.absolutePath}")
        } else {
            println("ERROR: El archivo index.html no existe en ${dokkaOutputDir.absolutePath}")
        }
    }
} 