package com.example.perros

import android.app.Application
import android.util.Log

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
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Inicializar el sistema de caché de imágenes
        initImageCache()
        
        Log.d(TAG, "Aplicación iniciada - Sistema de caché de imágenes inicializado")
    }
    
    /**
     * Inicializa el sistema de caché de imágenes.
     * Precarga el ImageLoader de Coil para que esté listo para su uso.
     */
    private fun initImageCache() {
        try {
            // Inicializar el ImageLoader de Coil para que la caché esté lista
            CoilImageCache.getImageLoader(applicationContext)
            Log.d(TAG, "Sistema de caché de imágenes inicializado correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar el sistema de caché de imágenes: ${e.message}")
        }
    }
    
    /**
     * Libera recursos cuando la aplicación está por terminar.
     */
    override fun onTerminate() {
        super.onTerminate()
        // No es necesario limpiar la caché ya que queremos que persista
    }
} 