# PawTracker

PawTracker es un sistema de monitorización en tiempo real para mascotas que desarrollamos como parte de nuestro proyecto final del ciclo formativo de Desarrollo de Aplicaciones Multiplataforma. La aplicación permite a los dueños de perros rastrear la ubicación de sus mascotas mediante GPS y recibir alertas cuando salen de zonas seguras predefinidas.

## Estructura del Proyecto

El repositorio está organizado para facilitar tanto el desarrollo como la revisión del proyecto:

### /app

Contiene el código fuente completo de la aplicación Android. Aquí encontrarás todas las clases Kotlin, recursos, manifiestos y configuraciones de Gradle necesarias para compilar y ejecutar el proyecto.

### /memoria

Documentación funcional completa del proyecto en formato HTML, incluyendo todas las capturas de pantalla y diagramas. Esta memoria detalla la arquitectura, características implementadas y decisiones de diseño tomadas durante el desarrollo.

### /dokka

Script automatizado para generar y visualizar la documentación técnica del código. Utiliza Dokka, la herramienta estándar para documentación de proyectos Kotlin.

### /apk

Versión debug precompilada de la aplicación, lista para instalar en dispositivos Android sin necesidad de compilar el proyecto.

### /docs

Sitio web estático con la documentación funcional, configurado para ser servido mediante GitHub Pages.

## Características Implementadas

### Monitoreo de Ubicación y Geofencing

El sistema utiliza GPS para rastrear en tiempo real la posición de las mascotas. Los usuarios pueden definir zonas seguras personalizadas mediante círculos en el mapa, y la aplicación envía notificaciones push instantáneas cuando un perro abandona su zona designada. También mantiene un historial de ubicaciones para revisar movimientos anteriores.

### Gestión de Perfiles

Cada mascota tiene un perfil detallado que incluye información como raza, edad, peso, y fechas importantes. Los usuarios pueden gestionar múltiples perros desde un único perfil de propietario. Las imágenes de perfil se procesan y optimizan automáticamente mediante UCrop. Además, implementamos un modo de simulación que facilita las pruebas sin necesidad de múltiples dispositivos.

### Diseño y Experiencia de Usuario

La interfaz está construida completamente sobre Material Design 3, con soporte nativo para temas claro y oscuro. Los layouts son responsivos y se adaptan a diferentes tamaños de pantalla. Incluimos animaciones fluidas entre transiciones y un SplashScreen personalizado que mejora la percepción de carga inicial.

### Stack Tecnológico

- **Firebase Realtime Database**: Sincronización de datos en tiempo real entre dispositivos
- **Firebase Authentication**: Sistema de autenticación seguro con soporte para Google Sign-In
- **Google Maps API**: Visualización de mapas y configuración de zonas seguras
- **Room Database**: Almacenamiento local para funcionamiento offline
- **UCrop**: Procesamiento y recorte de imágenes de perfil
- **WorkManager**: Gestión de tareas en segundo plano y notificaciones periódicas

## Arquitectura Técnica

### Material Design 3

Implementamos la última versión de las guías de diseño de Google, utilizando componentes modernos como MaterialCardView, ShapeableImageView y FloatingActionButton. Los temas están completamente personalizados para mantener consistencia visual en modos claro y oscuro, con una paleta de colores diseñada para cumplir estándares de accesibilidad.

### Layouts y Responsividad

Todos los layouts utilizan ConstraintLayout para garantizar adaptabilidad. Las medidas son relativas y se calculan dinámicamente según el dispositivo. Para las listas empleamos RecyclerView con adaptadores personalizados que optimizan el rendimiento mediante ViewHolder pattern.

### Fragments y Reutilización de Código

Estructuramos la aplicación usando Fragments para evitar duplicación de código y facilitar la navegación. Los componentes UI son reutilizables y empleamos ViewBinding para acceso tipo-seguro a las vistas, eliminando la necesidad de findViewById.

### Recursos y Assets

Utilizamos vectores XML siempre que es posible para mantener calidad en cualquier densidad de pantalla. Los estilos están centralizados en archivos de recursos, facilitando el mantenimiento y la consistencia visual. Implementamos múltiples variantes de botones y controles según las necesidades de cada pantalla.

### Geolocalización

El sistema de geofencing está implementado de forma personalizada, calculando distancias entre la ubicación del dispositivo y los centros de las zonas seguras mediante la fórmula de Haversine. Los marcadores en el mapa son personalizados y se actualizan en tiempo real conforme cambia la ubicación.

### Sistema de Notificaciones

Las notificaciones están organizadas en canales específicos según su prioridad. Implementamos un sistema de verificación periódica mediante WorkManager que comprueba si las mascotas están dentro de sus zonas seguras, enviando alertas cuando es necesario. Las notificaciones incluyen acciones directas para abrir el mapa y ver la ubicación actual.

## Requisitos del Sistema

- Android 7.0 (API level 24) o superior
- Google Play Services actualizados
- Permisos de ubicación (foreground y background)
- Permisos de notificaciones
- Conexión a internet para sincronización en tiempo real

## Configuración e Instalación

### Configuración del Entorno

1. Clona el repositorio:

```bash
git clone https://github.com/[usuario]/PMDM_DEIN_FINAL.git
cd PMDM_DEIN_FINAL
```

2. Abre el proyecto en Android Studio Arctic Fox o superior

3. Sincroniza las dependencias de Gradle

### Configuración de Firebase

1. Crea un proyecto en [Firebase Console](https://console.firebase.google.com)
2. Añade una aplicación Android con el package name del proyecto
3. Descarga el archivo `google-services.json`
4. Coloca el archivo en la carpeta `app/`
5. Habilita Authentication (Email/Password y Google Sign-In)
6. Crea una Realtime Database en modo test

### Configuración de Google Maps

1. Obtén una API Key desde [Google Cloud Console](https://console.cloud.google.com)
2. Habilita las APIs de Maps SDK for Android y Places API
3. Añade la clave en el archivo de configuración correspondiente

### Compilación

```bash
./gradlew assembleDebug
```

La APK compilada estará disponible en `app/build/outputs/apk/debug/`

## Documentación

### Documentación Funcional

Disponible en la carpeta `/memoria` o visitando nuestra [página de GitHub Pages](https://[usuario].github.io/PMDM_DEIN_FINAL/)

### Documentación Técnica

Genera la documentación del código ejecutando:

```bash
./gradlew :app:serveDocs
```

El navegador se abrirá automáticamente mostrando la documentación generada por Dokka.

## Equipo de Desarrollo

Este proyecto ha sido desarrollado por:

- Aritz Mendive
- Juan Borrás
- Martín Peñalva

## Licencia

Este proyecto está licenciado bajo la Licencia MIT. Consulta el archivo [LICENSE](LICENSE) para más detalles.

## Dependencias y Créditos

Las principales librerías utilizadas en el proyecto:

- Firebase SDK (Apache 2.0)
- Google Maps SDK (Términos de servicio de Google)
- UCrop (Apache 2.0)
- Material Components for Android (Apache 2.0)
- AndroidX Libraries (Apache 2.0)

---

Desarrollado como proyecto final del ciclo formativo de Desarrollo de Aplicaciones Multiplataforma.
