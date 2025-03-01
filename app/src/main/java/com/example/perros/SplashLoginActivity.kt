package com.example.perros

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import java.util.Date

/**
 * Actividad de splash screen para el proceso de login.
 *
 * Esta actividad utiliza un tema específico (SplashTheme) para mostrar inmediatamente
 * la pantalla de splash sin retrasos al iniciar la aplicación.
 * Gestiona la autenticación en segundo plano mientras se muestra el splash.
 *
 * @property loginStatus Estado de la operación de login (PENDING, SUCCESS, FAILED)
 * @property errorMessage Mensaje de error en caso de fallo
 */
class SplashLoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashLoginActivity"
        
        // Posibles estados de login
        const val STATUS_PENDING = 0
        const val STATUS_SUCCESS = 1
        const val STATUS_FAILED = 2
        
        // Claves para los extras del Intent
        const val EXTRA_LOGIN_STATUS = "login_status"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        const val EXTRA_EMAIL = "email"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_LOGIN_TYPE = "login_type"
        const val EXTRA_ID_TOKEN = "id_token"
        const val EXTRA_REMEMBER_ME = "remember_me"
        
        // Tipos de login
        const val LOGIN_TYPE_EMAIL = 1
        const val LOGIN_TYPE_GOOGLE = 2
        
        // Tiempos mínimos para mostrar el splash
        const val MIN_SPLASH_TIME = 1000L // Tiempo mínimo para mostrar el splash en ms
    }
    
    private var loginStatus = STATUS_PENDING
    private var errorMessage: String? = null
    private var startTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    
    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences
    private var rememberMe: Boolean = false
    private var email: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No llamamos a setContentView(), usamos el tema del splash en su lugar
        
        // Guardar el tiempo de inicio para calcular cuánto tiempo ha pasado
        startTime = System.currentTimeMillis()
        
        // Inicializar Firebase Auth y SharedPreferences
        auth = FirebaseAuth.getInstance()
        sharedPreferences = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        
        // Obtener el valor de "recordar usuario"
        rememberMe = intent.getBooleanExtra(EXTRA_REMEMBER_ME, false)
        
        // Comprobar el tipo de login que se está realizando
        val loginType = intent.getIntExtra(EXTRA_LOGIN_TYPE, LOGIN_TYPE_EMAIL)
        
        if (loginType == LOGIN_TYPE_EMAIL) {
            email = intent.getStringExtra(EXTRA_EMAIL)
            val password = intent.getStringExtra(EXTRA_PASSWORD)
            
            if (email != null && password != null) {
                iniciarSesionConEmail(email!!, password)
            } else {
                finalizarConError("Faltan credenciales")
            }
        } else if (loginType == LOGIN_TYPE_GOOGLE) {
            val idToken = intent.getStringExtra(EXTRA_ID_TOKEN)
            
            if (idToken != null) {
                iniciarSesionConGoogle(idToken)
            } else {
                finalizarConError("Falta token de Google")
            }
        } else {
            finalizarConError("Tipo de login no soportado")
        }
    }
    
    /**
     * Inicia sesión con email y contraseña.
     *
     * @param email Email del usuario
     * @param password Contraseña del usuario
     */
    private fun iniciarSesionConEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success")
                    
                    // Guardar preferencias del usuario
                    guardarPreferenciasUsuario(email)
                    
                    // Precargar imágenes del usuario
                    auth.currentUser?.let { firebaseUser ->
                        precargarImagenesUsuario(applicationContext, firebaseUser.uid)
                    }
                    
                    // Suscribirse a notificaciones
                    suscribirNotificaciones()
                    
                    finalizarConExito()
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    finalizarConError(task.exception?.message ?: "Error de autenticación")
                }
            }
    }
    
    /**
     * Inicia sesión con Google mediante el token de ID.
     *
     * @param idToken Token proporcionado por Google
     */
    private fun iniciarSesionConGoogle(idToken: String) {
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    
                    // Guardar tiempo de inicio de sesión
                    sharedPreferences.edit()
                        .putLong("last_login_time", Date().time)
                        .apply()
                    
                    // Precargar imágenes del usuario
                    auth.currentUser?.let { firebaseUser ->
                        precargarImagenesUsuario(applicationContext, firebaseUser.uid)
                    }
                    
                    // Suscribirse a notificaciones
                    suscribirNotificaciones()
                    
                    finalizarConExito()
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    finalizarConError(task.exception?.message ?: "Error de autenticación con Google")
                }
            }
    }
    
    /**
     * Guarda las preferencias del usuario como el tiempo de inicio de sesión 
     * y si se debe recordar el correo.
     *
     * @param email Correo electrónico del usuario si se debe recordar
     */
    private fun guardarPreferenciasUsuario(email: String?) {
        // Guardar tiempo de login y preferencia "recordar usuario"
        val editor = sharedPreferences.edit()
        editor.putLong("last_login_time", Date().time)
        editor.putBoolean("remember_me", rememberMe)
        
        // Si está marcado "recordar usuario", guardar el email
        if (rememberMe && email != null) {
            editor.putString("saved_email", email)
        } else {
            // Si no está marcado, eliminar email guardado
            editor.remove("saved_email")
        }
        
        editor.apply()
    }
    
    /**
     * Suscribe al dispositivo a las notificaciones de geovallas.
     */
    private fun suscribirNotificaciones() {
        FirebaseMessaging.getInstance().subscribeToTopic("geofence_alert")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Suscripción a notificaciones activada")
                } else {
                    Log.e(TAG, "Error al suscribirse a notificaciones", task.exception)
                }
            }
    }
    
    /**
     * Finaliza la actividad con éxito después del tiempo mínimo del splash.
     */
    private fun finalizarConExito() {
        loginStatus = STATUS_SUCCESS
        
        // Verificar si ha pasado el tiempo mínimo del splash
        val elapsedTime = System.currentTimeMillis() - startTime
        val remainingTime = (MIN_SPLASH_TIME - elapsedTime).coerceAtLeast(0)
        
        handler.postDelayed({
            val intent = Intent(this, MapsActivity::class.java)
            startActivity(intent)
            finish()
        }, remainingTime)
    }
    
    /**
     * Finaliza la actividad con error después del tiempo mínimo del splash.
     *
     * @param mensaje Mensaje de error para mostrar
     */
    private fun finalizarConError(mensaje: String) {
        loginStatus = STATUS_FAILED
        errorMessage = mensaje
        
        // Verificar si ha pasado el tiempo mínimo del splash
        val elapsedTime = System.currentTimeMillis() - startTime
        val remainingTime = (MIN_SPLASH_TIME - elapsedTime).coerceAtLeast(0)
        
        handler.postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra(EXTRA_LOGIN_STATUS, STATUS_FAILED)
            intent.putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
            startActivity(intent)
            finish()
        }, remainingTime)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Remover callbacks pendientes para evitar memory leaks
        handler.removeCallbacksAndMessages(null)
    }
} 