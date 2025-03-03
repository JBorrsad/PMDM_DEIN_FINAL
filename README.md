# DogTracker: Sistema de Monitorización de Mascotas

Para generar la documentación del proyecto, ejecutar el siguiente comando:

.\gradlew :app:serveDocs

## Descripción General

DogTracker es una aplicación Android diseñada para el monitoreo en tiempo real de la ubicación de perros domésticos, proporcionando tranquilidad a sus dueños y seguridad para las mascotas. El sistema permite establecer "zonas seguras" personalizadas para cada mascota y envía notificaciones instantáneas cuando un perro sale de su zona designada.

La aplicación utiliza tecnologías GPS para rastrear con precisión la ubicación de las mascotas y Firebase como backend para garantizar la sincronización en tiempo real entre dispositivos. Con una interfaz moderna y adaptable basada en Material Design 3, DogTracker ofrece una experiencia de usuario intuitiva tanto en modo claro como oscuro.

## Características Principales

### 🗺️ Monitoreo de Ubicación y Zonas Seguras
- **Seguimiento GPS en tiempo real** de la posición de tus mascotas
- **Creación de zonas seguras personalizadas** con perímetros ajustables
- **Notificaciones push instantáneas** cuando un perro sale de su zona segura
- **Historial de ubicaciones** para revisar los movimientos anteriores

### 👤 Gestión de Perfiles
- **Perfiles detallados para mascotas** con información como raza, edad, peso y fechas importantes
- **Perfiles de usuario** para propietarios con capacidad de gestionar múltiples mascotas
- **Galería de imágenes** con procesamiento y optimización incorporados
- **Modo de simulación** para probar funcionalidades sin necesidad de múltiples dispositivos

### 🎨 Diseño e Interfaz
- **Implementación completa de Material Design 3** con componentes modernos
- **Temas adaptables** para modos claro y oscuro
- **Layouts responsivos** que se adaptan a diferentes tamaños de pantalla
- **Animaciones y transiciones fluidas** entre componentes de la interfaz
- **SplashScreen personalizado** con imagen de marca

### 🔧 Tecnologías Implementadas
- **Firebase Realtime Database** para sincronización de datos en tiempo real
- **Firebase Authentication** para gestión segura de usuarios
- **Google Maps API** para visualización y configuración de zonas
- **Procesamiento de imágenes** con UCrop para recorte y optimización
- **Sistema de notificaciones** para alertas de zonas seguras
- **Room Database** para almacenamiento local y funcionamiento offline

## Arquitectura Técnica

La aplicación está estructurada siguiendo las mejores prácticas de desarrollo Android:

### Material Design 3
- Implementación completa de la guía de diseño de Material 3
- Componentes modernos como MaterialCardView, ShapeableImageView y FloatingActionButton
- Temas personalizados para modos claro y oscuro
- Paleta de colores coherente y accesible

### Layouts Responsivos
- Utilización de ConstraintLayout para adaptabilidad
- Medidas relativas para compatibilidad con diferentes pantallas
- RecyclerView para listas eficientes
- Dimensionamiento dinámico de elementos

### Fragments y Reutilización
- Uso de Fragments para evitar duplicación de código
- Componentes UI reutilizables
- Adaptadores personalizados para mostrar datos
- ViewBinding para acceso tipo-seguro a vistas

### Vistas y Recursos
- Imágenes vectoriales para escalabilidad
- Múltiples tipos de botones (estándar, outline, personalizados)
- Menús de opciones y navegación inferior
- Animaciones personalizadas para transiciones

### Multimedia y Animaciones
- SplashScreen con animaciones atractivas
- Procesamiento de imágenes con UCrop
- Transiciones entre actividades
- Animaciones en elementos de UI para feedback visual

### Geolocalización y Mapas
- Integración con Google Maps para visualización
- Sistema de geofencing personalizado
- Cálculo eficiente de distancias
- Marcadores personalizados para mascotas

### Notificaciones
- Sistema de notificaciones push
- Canales de notificación personalizados
- Notificaciones periódicas para alertas continuas
- Acciones directas desde notificaciones

## Requisitos del Sistema

- Android 7.0 (API level 24) o superior
- Servicios de Google Play actualizados
- Permisos de ubicación y notificaciones
- Acceso a internet para sincronización en tiempo real

## Configuración e Instalación

1. Clona el repositorio en tu máquina local
2. Abre el proyecto en Android Studio
3. Sincroniza con Gradle para instalar dependencias
4. Configura tu proyecto en Firebase y descarga el archivo google-services.json
5. Coloca el archivo google-services.json en la carpeta app/
6. Obtén una clave de API de Google Maps y configúrala en el archivo de configuración
7. Compila e instala en tu dispositivo

## Capturas de Pantalla

[Aquí se incluirían capturas de pantalla de la aplicación mostrando la pantalla de inicio, el mapa con zonas seguras, perfiles de mascotas y pantallas de configuración]

## Contribuciones

Las contribuciones son bienvenidas. Por favor, sigue estos pasos:

1. Haz fork del repositorio
2. Crea una rama para tu funcionalidad (`git checkout -b feature/nueva-funcionalidad`)
3. Realiza tus cambios y haz commit (`git commit -m 'Añadir nueva funcionalidad'`)
4. Sube tus cambios (`git push origin feature/nueva-funcionalidad`)
5. Abre un Pull Request

## Licencia

Este proyecto está licenciado bajo la Licencia MIT - ver el archivo [LICENSE](LICENSE) para más detalles.

## Créditos

Desarrollado por [Tu Nombre] como proyecto para el ciclo formativo de Desarrollo de Aplicaciones Multiplataforma.

Las librerías utilizadas incluyen:
- Firebase (Apache 2.0)
- Google Maps (Términos de servicio de Google)
- UCrop (Apache 2.0)
- Material Components (Apache 2.0)

---

© 2023 DogTracker - Todos los derechos reservados 