package com.example.perros

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        remoteMessage.notification?.let { notification ->
            Log.d("FCM", "Notificación recibida: ${notification.body}")
        }
    }

    override fun onNewToken(token: String) {
        Log.d("FCM", "Nuevo Token: $token")
        // Aquí podrías enviar el token a Firebase Database para asociarlo con un usuario
    }
}
