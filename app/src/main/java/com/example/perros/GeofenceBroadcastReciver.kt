package com.example.perros

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Bundle
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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
                enviarNotificacion()
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

    // ✅ Enviar notificación usando Firebase Cloud Messaging
    private fun enviarNotificacion() {
        Firebase.messaging.subscribeToTopic("geofence_alert")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FCM", "Dispositivo suscrito a geofence_alert")
                } else {
                    Log.e("FCM", "Error al suscribirse al tema")
                }
            }
    }
}
