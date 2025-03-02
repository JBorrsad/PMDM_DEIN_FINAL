package com.example.perros

import android.util.Log
import com.google.firebase.database.DataSnapshot
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton que gestiona los datos precargados de la aplicación.
 *
 * Esta clase se encarga de almacenar en caché los datos frecuentemente 
 * accedidos para reducir consultas a Firebase y mejorar el rendimiento.
 */
object DatosPrecargados {
    private const val TAG = "DatosPrecargados"
    
    // Indicador de inicialización completa
    private var inicializacionCompleta = false
    
    // ID del usuario actual
    private var userId: String? = null
    
    // Cachés para diferentes tipos de datos
    private val cacheUsuarios = ConcurrentHashMap<String, DataSnapshot>()
    private val cachePerros = ConcurrentHashMap<String, DataSnapshot>()
    private val cacheUbicacionesPerros = ConcurrentHashMap<String, DataSnapshot>()
    private val cacheZonasSeguras = ConcurrentHashMap<String, DataSnapshot>()
    
    // Caché para perros por dueño
    private val cachePerrosPorDueno = ConcurrentHashMap<String, List<String>>()
    
    /**
     * Indica si la inicialización de datos está completa
     */
    fun isInicializado(): Boolean = inicializacionCompleta
    
    /**
     * Establece el estado de inicialización
     */
    fun setInicializado(estado: Boolean) {
        inicializacionCompleta = estado
        Log.d(TAG, "Estado de inicialización establecido a: $estado")
    }
    
    /**
     * Marca la inicialización como completa
     */
    fun marcarInicializacionCompleta() {
        inicializacionCompleta = true
        Log.d(TAG, "✅ Inicialización de datos completada")
    }
    
    /**
     * Limpia todos los datos almacenados en caché
     */
    fun limpiarDatos() {
        userId = null
        inicializacionCompleta = false
        cacheUsuarios.clear()
        cachePerros.clear()
        cacheUbicacionesPerros.clear()
        cacheZonasSeguras.clear()
        cachePerrosPorDueno.clear()
        Log.d(TAG, "🔄 Caché de datos limpiada")
    }
    
    /**
     * Guarda el ID del usuario actual
     */
    fun guardarIdUsuario(id: String) {
        userId = id
        Log.d(TAG, "ID de usuario guardado: $id")
    }
    
    /**
     * Obtiene el ID del usuario actual
     */
    fun obtenerIdUsuario(): String? = userId
    
    /**
     * Guarda los datos del usuario actual
     */
    fun guardarDatosUsuario(datosUsuario: DataSnapshot) {
        val id = userId ?: return
        guardarUsuario(id, datosUsuario)
    }
    
    /**
     * Guarda los datos de un usuario en caché
     */
    fun guardarUsuario(usuarioId: String, datosUsuario: DataSnapshot) {
        cacheUsuarios[usuarioId] = datosUsuario
        Log.d(TAG, "👤 Usuario guardado en caché: $usuarioId")
    }
    
    /**
     * Obtiene los datos de un usuario desde la caché
     */
    fun obtenerUsuario(usuarioId: String): DataSnapshot? {
        return cacheUsuarios[usuarioId]
    }
    
    /**
     * Guarda los perros asociados a un usuario 
     */
    fun guardarPerrosUsuario(duenioId: String, perrosSnapshot: DataSnapshot) {
        val listaPerrosIds = mutableListOf<String>()
        
        perrosSnapshot.children.forEach { perroSnapshot ->
            val perroId = perroSnapshot.key ?: return@forEach
            val isPerro = perroSnapshot.child("isPerro").getValue(Boolean::class.java) == true
            
            if (isPerro) {
                listaPerrosIds.add(perroId)
                guardarPerro(perroId, perroSnapshot)
            }
        }
        
        cachePerrosPorDueno[duenioId] = listaPerrosIds
        Log.d(TAG, "🐕 Guardados ${listaPerrosIds.size} perros para usuario $duenioId")
    }
    
    /**
     * Obtiene la lista de IDs de perros asociados a un usuario
     */
    fun obtenerIdsPerrosUsuario(duenioId: String): List<String> {
        return cachePerrosPorDueno[duenioId] ?: emptyList()
    }
    
    /**
     * Guarda los datos de un perro en caché
     */
    fun guardarPerro(perroId: String, datosPerro: DataSnapshot) {
        cachePerros[perroId] = datosPerro
        Log.d(TAG, "🐕 Perro guardado en caché: $perroId")
    }
    
    /**
     * Obtiene los datos de un perro desde la caché
     */
    fun obtenerPerro(perroId: String): DataSnapshot? {
        return cachePerros[perroId]
    }
    
    /**
     * Guarda la ubicación de un perro en caché
     */
    fun guardarUbicacionPerro(perroId: String, ubicacionSnapshot: DataSnapshot) {
        cacheUbicacionesPerros[perroId] = ubicacionSnapshot
        Log.d(TAG, "📍 Ubicación guardada para perro: $perroId")
    }
    
    /**
     * Obtiene la ubicación de un perro desde la caché
     */
    fun obtenerUbicacionPerro(perroId: String): DataSnapshot? {
        return cacheUbicacionesPerros[perroId]
    }
    
    /**
     * Guarda la zona segura de un perro en caché
     */
    fun guardarZonaSeguraPerro(perroId: String, zonaSeguraSnapshot: DataSnapshot) {
        cacheZonasSeguras[perroId] = zonaSeguraSnapshot
        Log.d(TAG, "🔒 Zona segura guardada para perro: $perroId")
    }
    
    /**
     * Obtiene la zona segura de un perro desde la caché
     */
    fun obtenerZonaSeguraPerro(perroId: String): DataSnapshot? {
        return cacheZonasSeguras[perroId]
    }
    
    /**
     * Muestra el estado actual de la caché de datos en el log
     */
    fun mostrarEstadoDatos() {
        Log.d(TAG, "📊 ESTADO DE DATOS PRECARGADOS 📊")
        Log.d(TAG, "- Usuarios en caché: ${cacheUsuarios.size}")
        Log.d(TAG, "- Perros en caché: ${cachePerros.size}")
        Log.d(TAG, "- Ubicaciones en caché: ${cacheUbicacionesPerros.size}")
        Log.d(TAG, "- Zonas seguras en caché: ${cacheZonasSeguras.size}")
        Log.d(TAG, "- Dueños con perros: ${cachePerrosPorDueno.size}")
        
        if (userId != null) {
            val perrosUsuario = cachePerrosPorDueno[userId] ?: emptyList()
            Log.d(TAG, "- Perros del usuario actual: ${perrosUsuario.size}")
            perrosUsuario.forEach { perroId ->
                val nombrePerro = cachePerros[perroId]?.child("nombre")?.getValue(String::class.java) ?: "Sin nombre"
                Log.d(TAG, "  - Perro: $nombrePerro (ID: $perroId)")
            }
        }
    }
} 