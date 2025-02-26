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
import android.content.SharedPreferences
import java.util.Date

/**
 * Actividad principal que maneja la autenticación y el inicio de sesión de usuarios.
 *
 * Esta actividad es el punto de entrada de la aplicación y proporciona las siguientes funcionalidades:
 * - Autenticación de usuarios mediante Firebase Auth
 * - Control de sesión con tiempo límite
 * - Gestión de permisos de notificaciones
 * - Suscripción a notificaciones de geovallas
 *
 * La actividad implementa un sistema de timeout de sesión que cierra automáticamente
 * la sesión después de un período de inactividad.
 *
 * Estructura de datos en Firebase:
 * ```
 * users/
 *   ├── {userId}/
 *   │     ├── nombre: String
 *   │     ├── email: String
 *   │     └── imagenBase64: String?
 * ```
 *
 * @property auth Instancia de FirebaseAuth para manejar la autenticación
 * @property sharedPreferences Almacenamiento local para datos de sesión y preferencias
 * @property SESSION_TIMEOUT Tiempo máximo de sesión en milisegundos (5 minutos)
 *
 * @see MapsActivity actividad que se inicia tras un login exitoso
 * @see RegisterActivity actividad para registrar nuevos usuarios
 */
class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences
    private val SESSION_TIMEOUT = 5 * 60 * 1000 // 5 minutos en milisegundos

    /**
     * Inicializa la actividad y configura la interfaz de usuario.
     *
     * Este método realiza las siguientes tareas:
     * - Inicializa Firebase Auth
     * - Configura las SharedPreferences
     * - Verifica si existe una sesión activa
     * - Configura los listeners de los botones
     * - Solicita permisos de notificación si es necesario
     *
     * @param savedInstanceState Estado guardado de la actividad
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        sharedPreferences = getSharedPreferences("login_prefs", MODE_PRIVATE)

        // Verificar si la sesión ha expirado
        if (auth.currentUser != null) {
            val lastLoginTime = sharedPreferences.getLong("last_login_time", 0)
            val currentTime = Date().time

            if (currentTime - lastLoginTime > SESSION_TIMEOUT) {
                // La sesión ha expirado, cerrar sesión
                auth.signOut()
                sharedPreferences.edit().clear().apply()
            } else {
                startActivity(Intent(this, MapsActivity::class.java))
                finish()
                return
            }
        }

        setContentView(R.layout.activity_main)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoToRegister = findViewById<Button>(R.id.btnGoToRegister)

        solicitarPermisosNotificacion()

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
                        // Guardar el tiempo de inicio de sesión
                        sharedPreferences.edit()
                            .putLong("last_login_time", Date().time)
                            .apply()

                        Toast.makeText(this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()
                        suscribirNotificaciones()
                        startActivity(Intent(this, MapsActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Usuario o contraseña incorrectos", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    /**
     * Verifica el estado de la sesión cuando la actividad vuelve a primer plano.
     *
     * Comprueba si:
     * - Hay un usuario autenticado
     * - La sesión no ha expirado
     * Si la sesión ha expirado, cierra la sesión y limpia las preferencias.
     */
    override fun onResume() {
        super.onResume()
        if (auth.currentUser != null) {
            val lastLoginTime = sharedPreferences.getLong("last_login_time", 0)
            val currentTime = Date().time

            if (currentTime - lastLoginTime > SESSION_TIMEOUT) {
                // La sesión ha expirado, cerrar sesión
                auth.signOut()
                sharedPreferences.edit().clear().apply()
            } else {
                startActivity(Intent(this, MapsActivity::class.java))
                finish()
            }
        }
    }

    /**
     * Solicita permisos de notificación al usuario.
     *
     * Este método solo se ejecuta en Android 13 (API 33) o superior, ya que
     * es cuando se introdujo el permiso POST_NOTIFICATIONS.
     * En versiones anteriores, las notificaciones no requieren permiso explícito.
     */
    private fun solicitarPermisosNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Permiso de notificación concedido", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "El usuario denegó el permiso de notificación", Toast.LENGTH_SHORT).show()
            }
        }

    /**
     * Suscribe al dispositivo al tema de notificaciones de geovallas.
     *
     * Utiliza Firebase Cloud Messaging para suscribirse al tema 'geofence_alert',
     * que permite recibir notificaciones cuando un perro sale de su zona segura.
     *
     * @see MapsActivity.comprobarYNotificarZonaInsegura donde se generan las notificaciones
     */
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