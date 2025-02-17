package com.example.perros

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoToRegister = findViewById<Button>(R.id.btnGoToRegister)

        // 🔹 Ir a la pantalla de registro
        btnGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // 🔹 Botón de inicio de sesión
        btnLogin.setOnClickListener {
            val email = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // 🔹 Login exitoso
                        Toast.makeText(this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()

                        // 🔹 SUSCRIBIR AL USUARIO AL TEMA "geofence_alert"
                        FirebaseMessaging.getInstance().subscribeToTopic("geofence_alert")
                            .addOnCompleteListener { subscriptionTask ->
                                if (subscriptionTask.isSuccessful) {
                                    Toast.makeText(this, "Suscripción a notificaciones activada", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this, "Error al suscribirse a notificaciones", Toast.LENGTH_SHORT).show()
                                }
                            }

                        // 🔹 Redirigir a MapsActivity después del login
                        val intent = Intent(this, MapsActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        // 🔹 Error en las credenciales
                        Toast.makeText(this, "Usuario o contraseña incorrectos", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
}
