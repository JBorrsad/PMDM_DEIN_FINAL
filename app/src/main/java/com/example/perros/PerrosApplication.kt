package com.example.perros

import android.app.Application
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

/**
 * Clase Application personalizada para la aplicación Perros.
 * 
 * Esta clase se inicializa cuando la aplicación se lanza y se utiliza
 * para realizar configuraciones globales como inicialización de caché,
 * Firebase, y otros componentes que deben estar disponibles en toda la app.
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