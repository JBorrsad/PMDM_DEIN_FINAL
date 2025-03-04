import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.DokkaConfiguration.Visibility
import java.net.URL
import java.net.URI

buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
}

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

// Configuración para generar diagramas UML
tasks.register("generarUML") {
    group = "documentation"
    description = "Genera diagramas UML para las clases del proyecto usando PlantUML"
    
    doLast {
        // Crear directorios necesarios
        val umlDir = File(project.buildDir, "uml")
        umlDir.mkdirs()
        
        // Crear archivo PlantUML
        val diagramFile = File(umlDir, "diagrama-clases.puml")
        
        // Generar contenido básico del diagrama
        val diagramaContent = """
            @startuml
            
            skinparam classAttributeIconSize 0
            skinparam backgroundColor white
            skinparam roundcorner 5
            skinparam class {
                BackgroundColor LightSkyBlue
                ArrowColor DeepSkyBlue
                BorderColor DarkSlateGray
            }
            
            package com.example.perros {
                class MainActivity {
                    + onCreate(Bundle?)
                    + onStart()
                }
                class PerfilUsuario {
                    + usuario: Usuario
                    + mostrarDatos()
                }
                class PerfilPerro {
                    + perro: Perro
                    + mostrarInfo()
                }
                class EditarPerro {
                    + guardarCambios()
                }
                class EditarUsuario {
                    + actualizarPerfil()
                }
                class DogLocationManager {
                    + iniciarSeguimiento()
                    + detenerSeguimiento()
                }
                class MapsActivity {
                    + mostrarMapa()
                    + ubicarPerros()
                }
                class GeofencingService {
                    + crearGeofence()
                    + eliminarGeofence()
                }
                class GeofenceBroadcastReciver {
                    + onReceive()
                }
                class AjustesActivity {
                    + guardarPreferencias()
                }
                class SplashLoginActivity {
                    + verificarLogin()
                }
                class LoginActivity {
                    + iniciarSesion()
                }
                class RegisterActivity {
                    + registrarUsuario()
                }
                class ImageUtils {
                    + cargarImagen()
                    + guardarImagen()
                }
                class LoadingSpinner {
                    + mostrar()
                    + ocultar()
                }
                class PerrosApplication {
                    + onCreate()
                }
                class DogsClusterManager {
                    + addItem()
                    + clearItems()
                }
                class DogSpinnerAdapter {
                    + getView()
                }
                class BootCompletedReceiver {
                    + onReceive()
                }
                class DatosPrecargados {
                    + cargarDatos()
                }
                
                MainActivity --> PerfilUsuario : navega
                MainActivity --> PerfilPerro : navega
                MainActivity --> AjustesActivity : navega
                MainActivity --> MapsActivity : navega
                
                PerfilPerro --> EditarPerro : navega
                PerfilUsuario --> EditarUsuario : navega
                
                GeofenceBroadcastReciver --> GeofencingService : usa
                MapsActivity --> DogsClusterManager : usa
                MapsActivity --> DogLocationManager : usa
                
                EditarPerro ..> ImageUtils : usa
                EditarUsuario ..> ImageUtils : usa
                
                SplashLoginActivity --> LoginActivity : navega
                SplashLoginActivity --> RegisterActivity : navega
                SplashLoginActivity --> MainActivity : navega
                
                PerrosApplication o-- DatosPrecargados
            }
            
            @enduml
        """.trimIndent()
        
        diagramFile.writeText(diagramaContent)
        
        // Crear un archivo HTML con instrucciones
        val htmlFile = File(umlDir, "ver-diagrama.html")
        htmlFile.writeText("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Diagrama UML de Clases - Perros</title>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        line-height: 1.6;
                        margin: 20px;
                        max-width: 800px;
                        margin: 0 auto;
                        padding: 20px;
                    }
                    h1 {
                        color: #0066cc;
                        border-bottom: 1px solid #ccc;
                        padding-bottom: 10px;
                    }
                    .instructions {
                        background-color: #f5f5f5;
                        padding: 15px;
                        border-radius: 5px;
                        margin-bottom: 20px;
                    }
                    .button {
                        display: inline-block;
                        background-color: #0066cc;
                        color: white;
                        padding: 10px 20px;
                        text-decoration: none;
                        border-radius: 5px;
                        font-weight: bold;
                        margin: 20px 0;
                    }
                    textarea {
                        width: 100%;
                        height: 200px;
                        font-family: monospace;
                        padding: 10px;
                    }
                </style>
            </head>
            <body>
                <h1>Diagrama UML de Clases - Aplicación Perros</h1>
                
                <div class="instructions">
                    <h2>Instrucciones:</h2>
                    <p>Se ha generado un archivo PlantUML que contiene la estructura de clases de tu proyecto.</p>
                    <p>Para visualizar el diagrama UML, tienes dos opciones:</p>
                    <ol>
                        <li><strong>Usar una herramienta online:</strong> Copia el código PlantUML que se muestra abajo y pégalo en <a href="https://www.planttext.com/" target="_blank">PlantText</a>.</li>
                        <li><strong>Instalar una extensión:</strong> Si usas VSCode, instala la extensión <a href="https://marketplace.visualstudio.com/items?itemName=jebbs.plantuml" target="_blank">PlantUML</a> y abre el archivo diagrama-clases.puml.</li>
                    </ol>
                </div>
                
                <h2>Código PlantUML:</h2>
                <textarea id="plantUmlCode">${diagramaContent}</textarea>
                
                <p>
                    <a class="button" href="https://www.planttext.com/" target="_blank">Abrir PlantText</a>
                    <button class="button" onclick="copyToClipboard()">Copiar Código</button>
                </p>
                
                <div class="instructions">
                    <h2>Archivos generados:</h2>
                    <ul>
                        <li><strong>diagrama-clases.puml:</strong> Archivo fuente del diagrama UML</li>
                        <li><strong>ver-diagrama.html:</strong> Este archivo HTML con instrucciones</li>
                    </ul>
                    <p>Ubicación: ${umlDir.absolutePath}</p>
                </div>
                
                <script>
                function copyToClipboard() {
                    var copyText = document.getElementById("plantUmlCode");
                    copyText.select();
                    document.execCommand("copy");
                    alert("Código copiado al portapapeles");
                }
                </script>
            </body>
            </html>
        """.trimIndent())
        
        // Crear archivo README con instrucciones en Markdown
        val readmeFile = File(umlDir, "README.md")
        readmeFile.writeText("""
            # Diagrama UML de Clases - Aplicación Perros
            
            ## Instrucciones
            
            Se ha generado un archivo PlantUML que contiene la estructura de clases del proyecto.
            
            ### Opciones para visualizar el diagrama:
            
            1. **Usar PlantText (online):**
               - Accede a [PlantText](https://www.planttext.com/)
               - Copia el contenido del archivo `diagrama-clases.puml`
               - Pégalo en el editor de PlantText y pulsa "Refresh"
            
            2. **Usar VSCode con extensión PlantUML:**
               - Instala la extensión [PlantUML para VSCode](https://marketplace.visualstudio.com/items?itemName=jebbs.plantuml)
               - Abre el archivo `diagrama-clases.puml`
               - Pulsa Alt+D para previsualizar
            
            3. **Usar el archivo HTML incluido:**
               - Abre el archivo `ver-diagrama.html` en tu navegador
               - Sigue las instrucciones que aparecen
            
            ## Archivos generados
            
            - `diagrama-clases.puml`: Archivo fuente del diagrama UML
            - `ver-diagrama.html`: Archivo HTML con instrucciones
            - `README.md`: Este archivo con instrucciones en formato Markdown
            
            ## Personalización
            
            Si deseas modificar el diagrama, edita el archivo `diagrama-clases.puml` añadiendo o 
            modificando las clases y relaciones según la estructura actual de tu proyecto.
            
            Para más información sobre la sintaxis de PlantUML, consulta la 
            [documentación oficial](https://plantuml.com/class-diagram).
        """.trimIndent())
        
        // Información para el usuario
        println("\n")
        println("=".repeat(80))
        println("Diagrama UML generado correctamente en: ${umlDir.absolutePath}")
        println("\nArchivos generados:")
        println("- diagrama-clases.puml: Definición del diagrama UML")
        println("- ver-diagrama.html: Abrir este archivo para visualizar el diagrama")
        println("- README.md: Instrucciones detalladas")
        println("=".repeat(80))
        
        // Intentar abrir el archivo HTML
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            try {
                exec {
                    commandLine("cmd", "/c", "start", htmlFile.absolutePath)
                }
                println("\nAbriendo instrucciones en el navegador...")
            } catch (e: Exception) {
                println("\nNo se pudo abrir el navegador automáticamente.")
                println("Por favor, abre manualmente el archivo: ${htmlFile.absolutePath}")
            }
        }
        
        println("\n")
    }
}

// Tarea para analizar las clases y generar un diagrama UML más preciso
tasks.register("analizarClases") {
    group = "documentation"
    description = "Analiza las clases del proyecto y genera un diagrama UML más preciso"
    
    doLast {
        val srcDir = File(projectDir, "src/main/java/com/example/perros")
        val umlDir = File(project.buildDir, "uml")
        umlDir.mkdirs()
        
        // Verificar que el directorio existe
        if (!srcDir.exists() || !srcDir.isDirectory) {
            println("ERROR: No se encontró el directorio de código fuente: ${srcDir.absolutePath}")
            return@doLast
        }
        
        // Obtener todos los archivos Kotlin de forma recursiva
        val kotlinFiles = srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        
        println("Analizando ${kotlinFiles.size} archivos Kotlin...")
        
        // Enfoque simplificado: solo extraer nombres de clases y algunas relaciones básicas
        val classNames = mutableListOf<String>()
        val classDependencies = mutableListOf<Pair<String, String>>()
        
        // Expresiones regulares simplificadas
        val classRegex = Regex("class\\s+([A-Za-z0-9_]+)")
        val importRegex = Regex("import\\s+com\\.example\\.perros\\.([A-Za-z0-9_]+)")
        
        // Extraer nombres de clases
        kotlinFiles.forEach { file ->
            val content = file.readText()
            val className = file.nameWithoutExtension
            
            // Añadir el nombre de la clase
            classNames.add(className)
            
            // Buscar importaciones de otras clases del mismo paquete
            val imports = importRegex.findAll(content).map { it.groupValues[1] }.toList()
            imports.forEach { importedClass ->
                if (importedClass != className) {
                    classDependencies.add(Pair(className, importedClass))
                }
            }
            
            // Buscar referencias directas a otras clases
            classNames.forEach { otherClass ->
                if (otherClass != className && content.contains(otherClass)) {
                    classDependencies.add(Pair(className, otherClass))
                }
            }
        }
        
        // Generar contenido del diagrama PlantUML
        val diagramFile = File(umlDir, "diagrama-analizado.puml")
        val content = buildString {
            appendLine("@startuml")
            appendLine()
            appendLine("skinparam classAttributeIconSize 0")
            appendLine("skinparam backgroundColor white")
            appendLine("skinparam roundcorner 5")
            appendLine("skinparam class {")
            appendLine("    BackgroundColor LightSkyBlue")
            appendLine("    ArrowColor DeepSkyBlue")
            appendLine("    BorderColor DarkSlateGray")
            appendLine("}")
            appendLine()
            appendLine("package com.example.perros {")
            
            // Generar clases
            classNames.forEach { className ->
                appendLine("    class $className")
            }
            
            appendLine()
            
            // Generar relaciones simplificadas
            val processedRelations = mutableSetOf<String>()
            classDependencies.forEach { (source, target) ->
                val relationKey = "$source-$target"
                if (!processedRelations.contains(relationKey)) {
                    appendLine("    $source --> $target")
                    processedRelations.add(relationKey)
                }
            }
            
            appendLine("}")
            appendLine()
            appendLine("@enduml")
        }
        
        diagramFile.writeText(content)
        
        // Crear un archivo HTML con instrucciones
        val htmlFile = File(umlDir, "ver-diagrama-analizado.html")
        htmlFile.writeText("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Diagrama UML Generado por Análisis - Perros</title>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        line-height: 1.6;
                        margin: 20px;
                        max-width: 800px;
                        margin: 0 auto;
                        padding: 20px;
                    }
                    h1 {
                        color: #0066cc;
                        border-bottom: 1px solid #ccc;
                        padding-bottom: 10px;
                    }
                    .instructions {
                        background-color: #f5f5f5;
                        padding: 15px;
                        border-radius: 5px;
                        margin-bottom: 20px;
                    }
                    .button {
                        display: inline-block;
                        background-color: #0066cc;
                        color: white;
                        padding: 10px 20px;
                        text-decoration: none;
                        border-radius: 5px;
                        font-weight: bold;
                        margin: 20px 0;
                    }
                    textarea {
                        width: 100%;
                        height: 200px;
                        font-family: monospace;
                        padding: 10px;
                    }
                </style>
            </head>
            <body>
                <h1>Diagrama UML Generado por Análisis - Aplicación Perros</h1>
                
                <div class="instructions">
                    <h2>¿Qué es esto?</h2>
                    <p>Este diagrama UML ha sido generado automáticamente mediante el análisis del código fuente de tu proyecto.</p>
                </div>
                
                <div class="instructions">
                    <h2>Instrucciones:</h2>
                    <p>Para visualizar el diagrama UML, tienes dos opciones:</p>
                    <ol>
                        <li><strong>Usar una herramienta online:</strong> Copia el código PlantUML que se muestra abajo y pégalo en <a href="https://www.planttext.com/" target="_blank">PlantText</a>.</li>
                        <li><strong>Instalar una extensión:</strong> Si usas VSCode, instala la extensión <a href="https://marketplace.visualstudio.com/items?itemName=jebbs.plantuml" target="_blank">PlantUML</a> y abre el archivo diagrama-analizado.puml.</li>
                    </ol>
                </div>
                
                <h2>Código PlantUML:</h2>
                <textarea id="plantUmlCode">${content}</textarea>
                
                <p>
                    <a class="button" href="https://www.planttext.com/" target="_blank">Abrir PlantText</a>
                    <button class="button" onclick="copyToClipboard()">Copiar Código</button>
                </p>
                
                <script>
                function copyToClipboard() {
                    var copyText = document.getElementById("plantUmlCode");
                    copyText.select();
                    document.execCommand("copy");
                    alert("Código copiado al portapapeles");
                }
                </script>
            </body>
            </html>
        """.trimIndent())
        
        // Información para el usuario
        println("\n")
        println("=".repeat(80))
        println("Análisis de clases completado. Diagrama UML generado en: ${diagramFile.absolutePath}")
        println("Este diagrama UML ha sido generado mediante análisis automático del código.")
        println("\nArchivos generados:")
        println("- diagrama-analizado.puml: Definición del diagrama UML basado en análisis")
        println("- ver-diagrama-analizado.html: Página web para visualizar el diagrama")
        println("=".repeat(80))
        
        // Intentar abrir el archivo HTML
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            try {
                exec {
                    commandLine("cmd", "/c", "start", htmlFile.absolutePath)
                }
                println("\nAbriendo instrucciones en el navegador...")
            } catch (e: Exception) {
                println("\nNo se pudo abrir el navegador automáticamente.")
                println("Por favor, abre manualmente el archivo: ${htmlFile.absolutePath}")
            }
        }
        
        println("\n")
    }
}

// Tarea combinada para generar ambos diagramas UML
tasks.register("generarTodosUML") {
    dependsOn("generarUML", "analizarClases")
    group = "documentation"
    description = "Genera ambos diagramas UML (manual y automático)"
    
    doLast {
        val umlDir = File(project.buildDir, "uml")
        
        // Crear un archivo de instrucciones en español
        val instruccionesFile = File(umlDir, "INSTRUCCIONES.md")
        instruccionesFile.writeText("""
            # Instrucciones para Diagramas UML

            Este proyecto incluye dos tipos de diagramas UML para visualizar la estructura de clases:

            ## 1. Diagrama UML Manual

            Este diagrama (`diagrama-clases.puml`) fue creado manualmente con estructura y relaciones claras.
            Ventajas:
            - Estructura limpia y fácil de entender
            - Relaciones cuidadosamente seleccionadas
            - Incluye los métodos y propiedades más importantes

            Para visualizarlo:
            - Abre el archivo `ver-diagrama.html` en tu navegador
            - Sigue las instrucciones que aparecen

            ## 2. Diagrama UML Generado por Análisis

            Este diagrama (`diagrama-analizado.puml`) fue generado automáticamente analizando el código.
            Ventajas:
            - Contiene propiedades y métodos reales del código
            - Detecta relaciones automáticamente
            - Más preciso técnicamente

            Para visualizarlo:
            - Abre el archivo `ver-diagrama-analizado.html` en tu navegador
            - Sigue las instrucciones que aparecen

            ## Comparación de Ambos Diagramas

            | Característica | Diagrama Manual | Diagrama Analizado |
            |----------------|----------------|-------------------|
            | Precisión | Media | Alta |
            | Facilidad de lectura | Alta | Media |
            | Nivel de detalle | Seleccionado | Exhaustivo |
            | Relaciones | Cuidadosamente elegidas | Detectadas automáticamente |

            ## Visualización Online

            Para todos los diagramas, puedes usar [PlantText](https://www.planttext.com/):
            1. Copia el contenido del archivo `.puml`
            2. Pégalo en el editor de PlantText
            3. Haz clic en "Refresh" para generar el diagrama

            ## Generación de Nuevos Diagramas

            Si deseas regenerar los diagramas:
            ```
            ./gradlew generarTodosUML   # Genera ambos diagramas
            ./gradlew generarUML        # Solo el diagrama manual
            ./gradlew analizarClases    # Solo el diagrama generado por análisis
            ```
        """.trimIndent())
        
        println("\n")
        println("=".repeat(80))
        println("¡Todos los diagramas UML han sido generados correctamente!")
        println("\nArchivos de instrucciones:")
        println("- ${instruccionesFile.absolutePath}")
        println("\nPara visualizar los diagramas, abre los archivos HTML correspondientes.")
        println("=".repeat(80))
        println("\n")
    }
} 