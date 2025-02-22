package com.example.perros

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // ✅ Si el usuario ya está autenticado, ir directo a MapsActivity
        if (auth.currentUser != null) {
            startActivity(Intent(this, MapsActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoToRegister = findViewById<Button>(R.id.btnGoToRegister)

        solicitarPermisosNotificacion() // ✅ Solicitar permisos en Android 13+

        btnGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnLogin.setOnClickListener {
            val email = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()
                        suscribirNotificaciones() // ✅ Suscribir a notificaciones solo después de loguearse
                        startActivity(Intent(this, MapsActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Usuario o contraseña incorrectos", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    // ✅ Mantener sesión iniciada al minimizar
    override fun onResume() {
        super.onResume()
        if (auth.currentUser != null) {
            startActivity(Intent(this, MapsActivity::class.java))
            finish()
        }
    }

    // ✅ Solicitar permisos de notificación en Android 13+
    private fun solicitarPermisosNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ✅ Registrar el lanzador de permisos
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Permiso de notificación concedido", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "El usuario denegó el permiso de notificación", Toast.LENGTH_SHORT).show()
            }
        }

    // ✅ Suscribir al usuario al tema de notificaciones
    private fun suscribirNotificaciones() {
        FirebaseMessaging.getInstance().subscribeToTopic("geofence_alert")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Suscripción a notificaciones activada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Error al suscribirse a notificaciones", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
