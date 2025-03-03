package com.example.perros

import android.app.Application
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

/**
 * # PerrosApplication
 * 
 * Clase Application personalizada que inicializa y configura los componentes globales
 * del sistema de monitorización de mascotas.
 * 
 * ## Funcionalidad principal
 * Esta clase representa el punto de entrada de la aplicación a nivel de sistema, proporcionando:
 * - Inicialización centralizada de componentes críticos del sistema
 * - Configuración optimizada del sistema de caché de imágenes
 * - Gestión de recursos globales compartidos entre componentes
 * - Establecimiento de parámetros de rendimiento para toda la aplicación
 * - Manejo de ciclo de vida global de la aplicación
 * 
 * ## Características técnicas implementadas:
 * - **Coil Image Loading**: Sistema avanzado de carga y caché de imágenes
 * - **Caché en dos niveles**: Implementación de memoria RAM y almacenamiento en disco 
 * - **Gestión optimizada de recursos**: Configuración precisa de límites de memoria
 * - **Transiciones visuales**: Efecto crossfade para carga suave de imágenes
 * - **Persistencia entre sesiones**: Conservación de caché para mejorar experiencia recurrente
 * - **Manejo de errores robusto**: Captura y registro de excepciones durante la inicialización
 * 
 * ## Configuración de caché:
 * ```
 * ┌─ Memoria RAM ───────────┐   ┌─ Almacenamiento Disco ──┐
 * │ - 40% memoria disponible│   │ - Tamaño máximo: 150MB  │
 * │ - Prioridad alta        │   │ - Ubicación: directorio │
 * │ - Primera capa de caché │   │   personalizado /cache  │
 * └─────────────────────────┘   └───────────────────────────┘
 * ```
 * 
 * La inicialización adecuada de estos sistemas a nivel de aplicación garantiza un 
 * rendimiento óptimo y una experiencia de usuario fluida en todos los componentes.
 * 
 * @property TAG Etiqueta para registro de log de eventos relacionados con la aplicación
 * @property DISK_CACHE_SIZE Tamaño máximo de la caché en disco para imágenes (150MB)
 * 
 * @see CoilImageCache Clase que proporciona acceso al ImageLoader desde cualquier componente
 */
class PerrosApplication : Application() {
    
    companion object {
        private const val TAG = "PerrosApplication"
        private const val DISK_CACHE_SIZE = 150 * 1024 * 1024L // 150MB
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Inicializar el sistema de caché de imágenes
        initImageCache()
        
        Log.d(TAG, "Aplicación iniciada - Sistema de caché de imágenes inicializado")
    }
    
    /**
     * Inicializa el sistema de caché de imágenes.
     * Configura el ImageLoader de Coil para toda la aplicación
     * con opciones optimizadas para la caché.
     */
    private fun initImageCache() {
        try {
            // Crear y configurar el ImageLoader con opciones optimizadas
            val diskCacheFolder = File(cacheDir, "perros_images_cache")
            if (!diskCacheFolder.exists()) {
                diskCacheFolder.mkdirs()
            }
            
            val imageLoader = ImageLoader.Builder(applicationContext)
                .memoryCache {
                    MemoryCache.Builder(applicationContext)
                        .maxSizePercent(0.40) // Usar 40% de la memoria disponible
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(diskCacheFolder)
                        .maxSizeBytes(DISK_CACHE_SIZE)
                        .build()
                }
                // Configuración avanzada para mejor rendimiento
                .respectCacheHeaders(false) // Ignorar cabeceras HTTP para la caché
                .crossfade(true)
                .crossfade(50) // Transición aún más rápida (50ms)
                .build()
            
            // Establecer el ImageLoader a nivel de aplicación
            Coil.setImageLoader(imageLoader)
            
            // También inicializar el singleton de CoilImageCache
            CoilImageCache.getImageLoader(applicationContext)
            
            Log.d(TAG, "Sistema de caché de imágenes inicializado correctamente con mayor tamaño de caché")
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar el sistema de caché de imágenes: ${e.message}")
        }
    }
    
    /**
     * Libera recursos cuando la aplicación está por terminar.
     */
    override fun onTerminate() {
        super.onTerminate()
        // No limpiamos la caché ya que queremos que persista entre sesiones
    }
} 