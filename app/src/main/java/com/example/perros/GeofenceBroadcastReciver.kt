package com.example.perros

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase

/**
 * Receptor de transmisión para eventos de geovallas (geofencing).
 *
 * Este BroadcastReceiver se encarga de:
 * - Detectar cuando un perro entra o sale de su zona segura
 * - Enviar notificaciones push cuando el perro sale de la zona
 * - Actualizar el estado en Firebase Realtime Database
 * - Registrar eventos en Firebase Analytics
 *
 * Estructura de datos en Firebase:
 * ```
 * geofencing_status/
 *   └── mascota1/
 *         └── status: String ("IN"/"OUT")
 * ```
 *
 * Tipos de transición:
 * - GEOFENCE_TRANSITION_ENTER (1): El perro entra en la zona segura
 * - GEOFENCE_TRANSITION_EXIT (2): El perro sale de la zona segura
 *
 * @see MapsActivity donde se configuran las geovallas
 */
class GeofenceBroadcastReciver : BroadcastReceiver() {
    /**
     * Maneja los eventos de geovalla recibidos.
     *
     * Este método:
     * 1. Procesa el evento de geovalla
     * 2. Determina el tipo de transición (entrada/salida)
     * 3. Actualiza el estado en Firebase
     * 4. Envía notificaciones si es necesario
     *
     * @param context Contexto de la aplicación
     * @param intent Intent que contiene los datos del evento de geovalla
     */
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("Geofence", "onReceive activado - Intent recibido")

        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.e("Geofence", "Error: geofencingEvent es nulo")
            return
        }

        if (geofencingEvent.hasError()) {
            Log.e("Geofence", "Error en el evento de geofencing: ${geofencingEvent.errorCode}")
            return
        }

        val transitionType = geofencingEvent.geofenceTransition
        Log.d("Geofence", "Transición detectada: $transitionType")

        when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.d("Geofence", "Mascota ha ENTRADO en la zona segura")
                updateDatabase("IN")
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d("Geofence", "Mascota ha SALIDO de la zona segura")
                updateDatabase("OUT")
                enviarNotificacion(context)
            }
            else -> {
                Log.e("Geofence", "Tipo de transición desconocido: $transitionType")
            }
        }
    }

    /**
     * Actualiza el estado de la geovalla en Firebase.
     *
     * @param status Estado de la geovalla ("IN" cuando el perro está dentro,
     *               "OUT" cuando está fuera)
     */
    private fun updateDatabase(status: String) {
        val database = FirebaseDatabase.getInstance().getReference("geofencing_status")
        database.child("mascota1").setValue(status)

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
        val channelId = "geofence_channel"
        val notificationId = 1

        if (ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("Notificación", "Permiso de notificación no concedido")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Geofence Alerts", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificación cuando la mascota sale de la zona segura"
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("¡Alerta de geocerca!")
            .setContentText("Tu mascota ha salido de la zona segura.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            Log.d("Notificación", "Se ha enviado una notificación correctamente.")
            notify(notificationId, builder.build())
        }

        Log.d("Notificación", "Notificación enviada correctamente")
    }
} 