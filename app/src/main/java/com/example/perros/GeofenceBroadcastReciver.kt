package com.example.perros

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase

/**
 * # GeofenceBroadcastReciver
 * 
 * Receptor especializado en capturar y procesar eventos del sistema de geofencing 
 * para el sistema de monitorización de mascotas.
 * 
 * ## Funcionalidad principal
 * Esta clase actúa como componente crítico del sistema de zonas seguras, responsable de:
 * - Interceptar eventos de entrada/salida de zonas delimitadas para perros
 * - Procesar las transiciones entre estados (dentro/fuera de zona segura)
 * - Generar notificaciones inmediatas cuando un perro sale de su área segura
 * - Establecer un sistema de recordatorios periódicos mientras el perro permanece fuera
 * - Actualizar estados en tiempo real para su visualización en la aplicación
 * - Registrar eventos de seguridad para análisis posteriores
 * 
 * ## Características técnicas implementadas:
 * - **Gestión de transiciones geográficas**: Procesamiento de eventos de entrar/salir de zonas
 * - **Notificaciones push**: Alertas inmediatas y recordatorios periódicos con información contextual
 * - **AlarmManager**: Programación de notificaciones periódicas independientes del ciclo de vida
 * - **Firebase Realtime Database**: Actualización del estado para sincronización entre dispositivos
 * - **Firebase Analytics**: Registro de eventos para análisis de comportamiento y mejora del servicio
 * - **Compatibilidad multiversión**: Código adaptado a diferentes versiones de Android
 * 
 * ## Estructura de datos en Firebase:
 * ```
 * geofencing_status/
 *   └── {perroId}/
 *         ├── status: String ("IN"/"OUT")
 *         ├── lastExit: Long (timestamp)
 *         ├── zoneName: String?
 *         └── notifications: Boolean
 * ```
 * 
 * ## Tipos de transiciones procesadas:
 * - **GEOFENCE_TRANSITION_ENTER (1)**: El perro entra a su zona segura - se cancelan alertas
 * - **GEOFENCE_TRANSITION_EXIT (2)**: El perro sale de su zona segura - se inician alertas
 * - **GEOFENCE_TRANSITION_DWELL (4)**: El perro permanece en zona durante un tiempo específico
 * 
 * @property CHANNEL_ID Identificador del canal de notificaciones para alertas de geofencing
 * @property NOTIFICATION_ID Identificador único para las notificaciones generadas
 * @property ALARM_REQUEST_CODE Código de solicitud para AlarmManager y PendingIntent
 * @property ACTION_PERIODIC_NOTIFICATION Acción que identifica las notificaciones periódicas
 * @property NOTIFICATION_INTERVAL Intervalo entre notificaciones periódicas (30 segundos)
 * 
 * @see GeofencingService Servicio en segundo plano que gestiona el monitoreo continuo
 * @see MapsActivity Actividad principal donde se visualizan las ubicaciones y zonas seguras
 */
class GeofenceBroadcastReciver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "GeofenceReceiver"
        private const val CHANNEL_ID = "geofence_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ALARM_REQUEST_CODE = 123
        private const val ACTION_PERIODIC_NOTIFICATION = "com.example.perros.ACTION_PERIODIC_NOTIFICATION"
        private const val NOTIFICATION_INTERVAL = 30000L
    }
    
    /**
     * Procesa los eventos de broadcast relacionados con geofencing y transiciones de zona.
     * 
     * Este método se activa cuando el sistema detecta un evento de geofencing (como entrada
     * o salida de una zona monitoreada) y ejecuta la lógica adecuada según el tipo de transición.
     * 
     * Realiza:
     * - Extracción del tipo de transición (entrada/salida) y datos del geofence
     * - Actualización del estado del perro en la base de datos
     * - Gestión de notificaciones según las preferencias del usuario
     * - Registro de eventos para análisis y diagnóstico
     * 
     * @param context Contexto de la aplicación para acceder a recursos y servicios
     * @param intent Intent que contiene los datos del evento de geofencing
     */
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive activado - Intent recibido: ${intent.action}")
        
        when (intent.action) {
            ACTION_PERIODIC_NOTIFICATION -> {
                // Verificar si el perro sigue fuera y enviar notificación periódica
                checkDogStatusAndNotify(context)
            }
            else -> {
                // Procesar evento de geofencing
                processGeofencingEvent(context, intent)
            }
        }
    }

    /**
     * Procesa el evento de geofencing recibido
     */
    private fun processGeofencingEvent(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.e(TAG, "Error: geofencingEvent es nulo")
            return
        }

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Error en el evento de geofencing: ${geofencingEvent.errorCode}")
            return
        }

        val transitionType = geofencingEvent.geofenceTransition
        Log.d(TAG, "Transición detectada: $transitionType")

        when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.d(TAG, "Mascota ha ENTRADO en la zona segura")
                updateDatabase("IN")
                // Cancelar notificaciones periódicas
                cancelPeriodicNotifications(context)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d(TAG, "Mascota ha SALIDO de la zona segura")
                updateDatabase("OUT")
                enviarNotificacion(context)
                // Iniciar notificaciones periódicas
                schedulePeriodicNotifications(context)
            }
            else -> {
                Log.e(TAG, "Tipo de transición desconocido: $transitionType")
            }
        }
    }

    /**
     * Programa notificaciones periódicas cada 5 segundos mientras el perro esté fuera
     */
    fun schedulePeriodicNotifications(context: Context) {
        Log.d(TAG, "Iniciando notificaciones periódicas")
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, GeofenceBroadcastReciver::class.java).apply {
            action = ACTION_PERIODIC_NOTIFICATION
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Programar para que se repita cada 5 segundos
        alarmManager.setRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + NOTIFICATION_INTERVAL,
            NOTIFICATION_INTERVAL,
            pendingIntent
        )
        
        // Guardar en SharedPreferences que las notificaciones periódicas están activas
        context.getSharedPreferences("GeofencePrefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("periodic_notifications_active", true)
            .apply()
            
        Log.d(TAG, "Notificaciones periódicas programadas")
    }
    
    /**
     * Cancela las notificaciones periódicas
     */
    fun cancelPeriodicNotifications(context: Context) {
        Log.d(TAG, "Cancelando notificaciones periódicas")
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, GeofenceBroadcastReciver::class.java).apply {
            action = ACTION_PERIODIC_NOTIFICATION
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Cancelar alarma
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        
        // Actualizar SharedPreferences
        context.getSharedPreferences("GeofencePrefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("periodic_notifications_active", false)
            .apply()
            
        Log.d(TAG, "Notificaciones periódicas canceladas")
    }
    
    /**
     * Verifica el estado actual del perro en Firebase y envía notificación si sigue fuera
     */
    private fun checkDogStatusAndNotify(context: Context) {
        Log.d(TAG, "Verificando estado actual del perro...")
        
        val database = FirebaseDatabase.getInstance().getReference("geofencing_status/mascota1")
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java)
                Log.d(TAG, "Estado actual del perro: $status")
                
                if (status == "OUT") {
                    // El perro sigue fuera, enviar notificación
                    Log.d(TAG, "El perro sigue fuera, enviando notificación periódica")
                    enviarNotificacion(context)
                } else {
                    // El perro ya no está fuera, cancelar notificaciones
                    Log.d(TAG, "El perro ya no está fuera, cancelando notificaciones periódicas")
                    cancelPeriodicNotifications(context)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error al verificar estado del perro: ${error.message}")
            }
        })
    }

    /**
     * Actualiza el estado de la geovalla en Firebase.
     *
     * @param status Estado de la geovalla ("IN" cuando el perro está dentro,
     *               "OUT" cuando está fuera)
     */
    private fun updateDatabase(status: String) {
        Log.d(TAG, "Actualizando estado en Firebase a: $status")
        val database = FirebaseDatabase.getInstance().getReference("geofencing_status")
        database.child("mascota1").setValue(status)
            .addOnSuccessListener {
                Log.d(TAG, "Estado actualizado correctamente a $status")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al actualizar estado: ${e.message}")
            }

        enviarEventoGeofencing(status)
    }

    /**
     * Registra eventos de geovalla en Firebase Analytics.
     *
     * @param status Estado a registrar en Analytics ("IN"/"OUT")
     */
    private fun enviarEventoGeofencing(status: String) {
        val analytics = Firebase.analytics
        val bundle = Bundle()
        bundle.putString("geofencing_status", status)
        analytics.logEvent("geofence_alert", bundle)
    }

    /**
     * Envía una notificación push cuando el perro sale de la zona segura.
     *
     * Esta función:
     * 1. Verifica los permisos de notificación
     * 2. Crea un canal de notificación (en Android 8.0+)
     * 3. Construye y muestra la notificación
     *
     * @param context Contexto necesario para enviar la notificación
     */
    fun enviarNotificacion(context: Context) {
        val notificationId = NOTIFICATION_ID
        
        val sharedPreferences = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPreferences.getBoolean("notifications_enabled", true)
        
        // Verificar si las notificaciones están habilitadas en la app
        if (!notificationsEnabled) {
            Log.d(TAG, "Las notificaciones están desactivadas en la configuración de la app")
            return
        }

        // Verificar permisos en Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Permiso de notificación no concedido en Android 13+")
                return
            }
        }

        try {
            // Crear canal de notificación (requerido para Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // Verificar si el canal ya existe
                if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.geofence_channel_name),
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = context.getString(R.string.geofence_channel_description)
                        enableLights(true)
                        lightColor = android.graphics.Color.RED
                        enableVibration(true)
                        vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 100)
                    }
                    notificationManager.createNotificationChannel(channel)
                    Log.d(TAG, "Canal de notificación creado: $CHANNEL_ID")
                }
            }

            // Crear intent para abrir la app al tocar la notificación
            val intent = Intent(context, MapsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )

            // Construir la notificación
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.app_icon_paw)
                .setContentTitle(context.getString(R.string.alerta_geocerca))
                .setContentText(context.getString(R.string.mascota_fuera_zona))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            // Enviar la notificación
            with(NotificationManagerCompat.from(context)) {
                try {
                    notify(notificationId, builder.build())
                    Log.d(TAG, "Notificación enviada correctamente con ID: $notificationId")
                } catch (e: SecurityException) {
                    Log.e(TAG, "Error de seguridad al enviar notificación: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error al enviar notificación: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción general al configurar/enviar notificación: ${e.message}", e)
        }
    }
} 