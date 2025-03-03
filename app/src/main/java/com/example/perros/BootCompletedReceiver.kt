package com.example.perros

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.os.Build

/**
 * # BootCompletedReceiver
 * 
 * Receptor de broadcast especializado que restaura el sistema de monitorización 
 * después del reinicio del dispositivo.
 * 
 * ## Funcionalidad principal
 * Esta clase garantiza la continuidad del servicio de monitoreo de perros incluso 
 * después de que el dispositivo se reinicie, permitiendo:
 * - Detectar automáticamente cuando el dispositivo completa su arranque
 * - Restaurar el estado de monitorización para todos los perros registrados
 * - Reanudar notificaciones periódicas para perros fuera de su zona segura
 * - Reiniciar los servicios de geofencing y seguimiento GPS
 * - Mantener la integridad del sistema sin intervención del usuario
 * 
 * ## Características técnicas implementadas:
 * - **Receptor de broadcast del sistema**: Integración con eventos del sistema operativo Android
 * - **Persistencia de estados**: Recuperación de los estados previos tras el reinicio
 * - **Firebase Realtime Database**: Verificación del último estado conocido de cada perro
 * - **Servicios en primer plano**: Iniciación de servicios adaptados a las restricciones de Android
 * - **Gestión de preferencias**: Respeto de las configuraciones del usuario al restaurar servicios
 * - **Compatibilidad multiplataforma**: Manejo diferenciado según la versión de Android
 * 
 * ## Proceso tras el reinicio:
 * 1. Verifica si las notificaciones están habilitadas en las preferencias del usuario
 * 2. Recupera la lista de perros que estaban siendo monitoreados
 * 3. Comprueba el estado actual de cada perro (dentro/fuera de zona segura)
 * 4. Restaura los servicios de monitorización para cada perro que lo requiera
 * 5. Reinicia las notificaciones periódicas para perros fuera de su zona segura
 * 
 * Este componente es esencial para garantizar que las notificaciones de seguridad 
 * continúen funcionando incluso después de eventos del sistema como reinicios.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
    
    /**
     * Procesa el evento de broadcast cuando el dispositivo completa su arranque.
     * 
     * Este método se ejecuta automáticamente cuando el sistema envía el broadcast
     * ACTION_BOOT_COMPLETED, indicando que el dispositivo ha terminado su proceso
     * de inicio y está listo para restaurar los servicios de aplicaciones.
     * 
     * Realiza:
     * - Verificación de las preferencias de notificaciones del usuario
     * - Recuperación de la lista de perros monitoreados previamente
     * - Reinicio de los servicios de monitorización para cada perro
     * - Restauración de notificaciones periódicas si estaban activas
     * 
     * @param context Contexto de la aplicación para acceder a recursos y servicios
     * @param intent Intent que contiene la acción que provocó la activación del receptor
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Dispositivo reiniciado, verificando estado de geofencing")
            
            // Verificar preferencias de notificaciones
            val sharedPreferences = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val notificationsEnabled = sharedPreferences.getBoolean("notifications_enabled", true)
            
            if (!notificationsEnabled) {
                Log.d(TAG, "Las notificaciones están desactivadas, no se reiniciarán las alertas")
                return
            }
            
            // Obtener lista de perros monitoreados
            val monitoredDogsPrefs = context.getSharedPreferences("MonitoredDogs", Context.MODE_PRIVATE)
            val monitoredDogIds = monitoredDogsPrefs.getStringSet("dog_ids", emptySet()) ?: emptySet()
            
            if (monitoredDogIds.isNotEmpty()) {
                // Hay perros para monitorear, iniciar servicio para cada uno
                for (dogId in monitoredDogIds) {
                    Log.d(TAG, "Iniciando servicio de monitoreo para perro ID: $dogId")
                    startGeofencingService(context, dogId)
                }
            } else {
                // Verificar si había notificaciones periódicas activas antes de reiniciar
                val geofencePrefs = context.getSharedPreferences("GeofencePrefs", Context.MODE_PRIVATE)
                val wasActive = geofencePrefs.getBoolean("periodic_notifications_active", false)
                
                if (wasActive) {
                    Log.d(TAG, "Las notificaciones periódicas estaban activas, verificando estado actual")
                    checkDogStatusAndRestartNotifications(context)
                } else {
                    Log.d(TAG, "No había notificaciones periódicas activas")
                }
            }
        }
    }
    
    /**
     * Inicia el servicio de geofencing para un perro específico.
     * 
     * Crea y lanza el intent para el servicio de monitorización, teniendo en cuenta
     * las diferencias entre versiones de Android para servicios en primer plano.
     * 
     * Este método garantiza que el servicio de geofencing se inicie correctamente
     * según las restricciones del sistema operativo, usando startForegroundService
     * para Android 8.0+ y startService para versiones anteriores.
     * 
     * @param context Contexto de la aplicación para iniciar el servicio
     * @param dogId Identificador único del perro a monitorear
     */
    private fun startGeofencingService(context: Context, dogId: String) {
        val serviceIntent = Intent(context, GeofencingService::class.java).apply {
            putExtra("perroId", dogId)
        }
        
        // En Android 8.0+, debemos iniciar como un servicio en primer plano
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        
        Log.d(TAG, "Servicio de monitoreo iniciado para perro ID: $dogId")
    }
    
    /**
     * Verifica el estado actual del perro en Firebase y reinicia las notificaciones si sigue fuera.
     * 
     * Realiza una consulta a Firebase para determinar si un perro que estaba fuera de su
     * zona segura antes del reinicio sigue estando fuera, y en ese caso restaura las
     * notificaciones periódicas y el servicio de monitorización.
     * 
     * Este método implementa el patrón asíncrono con ValueEventListener para
     * procesar la respuesta de Firebase de manera eficiente.
     * 
     * @param context Contexto de la aplicación para acceder a los recursos necesarios
     */
    private fun checkDogStatusAndRestartNotifications(context: Context) {
        val database = FirebaseDatabase.getInstance().getReference("geofencing_status/mascota1")
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java)
                Log.d(TAG, "Estado actual del perro: $status")
                
                if (status == "OUT") {
                    // El perro sigue fuera, reiniciar notificaciones periódicas
                    Log.d(TAG, "El perro sigue fuera, reiniciando notificaciones periódicas")
                    val geofenceReceiver = GeofenceBroadcastReciver()
                    
                    // Enviar la notificación inicial
                    geofenceReceiver.enviarNotificacion(context)
                    
                    // Programar notificaciones periódicas
                    geofenceReceiver.schedulePeriodicNotifications(context)
                    
                    Log.d(TAG, "Notificaciones periódicas reiniciadas correctamente")
                    
                    // También iniciar el servicio de monitoreo continuo
                    startGeofencingService(context, "mascota1")
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error al verificar estado del perro: ${error.message}")
            }
        })
    }
} 