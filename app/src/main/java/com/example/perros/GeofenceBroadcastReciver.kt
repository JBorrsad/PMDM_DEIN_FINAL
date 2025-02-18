package com.example.perros

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("Geofence", "onReceive activado") // Verificar si el receiver está funcionando

        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            Log.e("Geofence", "Error en el evento de geofencing")
            return
        }

        val transitionType = geofencingEvent.geofenceTransition
        when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.d("Geofence", "Mascota ha entrado en la zona segura")
                updateDatabase("IN")
            }

            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d("Geofence", "Mascota ha salido de la zona segura")
                updateDatabase("OUT")
                enviarNotificacion(context)
            }
        }
    }

    // ✅ Actualiza el estado en Firebase Realtime Database
    private fun updateDatabase(status: String) {
        val database = FirebaseDatabase.getInstance().getReference("geofencing_status")
        database.child("mascota1").setValue(status)

        // Registrar evento en Firebase Analytics
        enviarEventoGeofencing(status)
    }

    // ✅ Envía evento a Firebase Analytics
    private fun enviarEventoGeofencing(status: String) {
        val analytics = Firebase.analytics
        val bundle = Bundle()
        bundle.putString("geofencing_status", status)
        analytics.logEvent("geofence_alert", bundle) // Nombre del evento en Firebase
    }

    // ✅ Enviar notificación local al salir de la zona segura
    private fun enviarNotificacion(context: Context) {
        val channelId = "geofence_channel"
        val notificationId = 1

        // Verificar si el permiso de notificación está concedido
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("Notificación", "Permiso de notificación no concedido")
            return
        }

        // Crear canal de notificaciones para Android 8+ (Oreo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Geofence Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificación cuando la mascota sale de la zona segura"
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        // Crear y mostrar la notificación
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Reemplaza con tu icono
            .setContentTitle("¡Alerta de geocerca!")
            .setContentText("Tu mascota ha salido de la zona segura.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
        }
    }
}