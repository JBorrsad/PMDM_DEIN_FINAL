package com.example.perros

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import android.content.SharedPreferences
import java.util.Date
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import android.widget.CheckBox
import android.app.ProgressDialog
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.GoogleAuthProvider

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

    companion object {
        private const val TAG = "MainActivity"
        private const val RC_SIGN_IN = 9001
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences
    private val SESSION_TIMEOUT = 5 * 60 * 1000 // 5 minutos en milisegundos
    
    // Google Sign In
    private lateinit var googleSignInClient: GoogleSignInClient

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
        // Restaurar tema normal (quitar splash screen)
        setTheme(R.style.AppTheme)
        
        super.onCreate(savedInstanceState)

        // Cargar preferencias y aplicar tema antes de establecer el contenido de vista
        sharedPreferences = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        applyUserTheme()

        auth = FirebaseAuth.getInstance()

        // Verificar si la sesión ha expirado
        if (auth.currentUser != null) {
            val lastLoginTime = sharedPreferences.getLong("last_login_time", 0)
            val currentTime = Date().time

            if (currentTime - lastLoginTime > SESSION_TIMEOUT) {
                // La sesión ha expirado, cerrar sesión
                auth.signOut()
                sharedPreferences.edit().clear().apply()
            } else {
                // IMPORTANTE: No podemos ir directamente a MapsActivity porque necesitamos
                // asegurarnos de que los datos precargados (perros, ubicaciones, etc.) estén
                // inicializados. Por eso redirigimos a SplashLoginActivity con un tipo especial
                // de login que indica que hay una sesión activa, y SplashLoginActivity se
                // encargará de cargar los datos antes de ir a MapsActivity.
                Log.d(TAG, "Sesión activa detectada, redirigiendo a SplashLoginActivity para cargar datos")
                val intent = Intent(this, SplashLoginActivity::class.java).apply {
                    putExtra(SplashLoginActivity.EXTRA_LOGIN_TYPE, SplashLoginActivity.LOGIN_TYPE_SESSION_ACTIVE)
                }
                startActivity(intent)
                finish()
                return
            }
        }

        setContentView(R.layout.activity_main)

        // Configurar Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoToRegister = findViewById<TextView>(R.id.btnGoToRegister)
        val rememberMeCheckbox = findViewById<CheckBox>(R.id.rememberMeCheckbox)
        val btnGoogleSignIn = findViewById<SignInButton>(R.id.btnGoogleSignIn)

        // Cargar credenciales guardadas si existen
        val savedEmail = sharedPreferences.getString("saved_email", "")
        val isRememberEnabled = sharedPreferences.getBoolean("remember_me", false)
        
        if (isRememberEnabled && !savedEmail.isNullOrEmpty()) {
            etUsername.setText(savedEmail)
            rememberMeCheckbox.isChecked = true
        }

        solicitarPermisosNotificacion()

        btnGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnLogin.setOnClickListener {
            val email = etUsername.text.toString().trim()
            val password = etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Iniciar SplashLoginActivity con los datos para login con email
            val intent = Intent(this, SplashLoginActivity::class.java).apply {
                putExtra(SplashLoginActivity.EXTRA_LOGIN_TYPE, SplashLoginActivity.LOGIN_TYPE_EMAIL)
                putExtra(SplashLoginActivity.EXTRA_EMAIL, email)
                putExtra(SplashLoginActivity.EXTRA_PASSWORD, password)
                putExtra(SplashLoginActivity.EXTRA_REMEMBER_ME, rememberMeCheckbox.isChecked)
            }
            startActivity(intent)
        }
        
        // Configurar botón de Google Sign In
        btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }
    }
    
    /**
     * Inicia el proceso de inicio de sesión con Google.
     * Abre el selector de cuentas de Google para que el usuario elija una.
     */
    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }
    
    /**
     * Maneja el resultado del inicio de sesión con Google.
     * Procesa la cuenta seleccionada por el usuario y autentica con Firebase.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Resultado de inicio de sesión con Google
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }
    
    /**
     * Procesa el resultado de la autenticación con Google.
     * Si es exitoso, autentica con Firebase usando las credenciales de Google.
     */
    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            // Iniciar SplashLoginActivity con los datos para login con Google
            val intent = Intent(this, SplashLoginActivity::class.java).apply {
                putExtra(SplashLoginActivity.EXTRA_LOGIN_TYPE, SplashLoginActivity.LOGIN_TYPE_GOOGLE)
                putExtra(SplashLoginActivity.EXTRA_ID_TOKEN, account.idToken)
            }
            startActivity(intent)
        } catch (e: ApiException) {
            // Error en la autenticación con Google
            Toast.makeText(this, "Error al iniciar sesión con Google: ${e.message}", Toast.LENGTH_SHORT).show()
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
        
        // Verificar si venimos de un intento de login fallido
        val loginStatus = intent.getIntExtra(SplashLoginActivity.EXTRA_LOGIN_STATUS, -1)
        val errorMessage = intent.getStringExtra(SplashLoginActivity.EXTRA_ERROR_MESSAGE)
        
        if (loginStatus == SplashLoginActivity.STATUS_FAILED && !errorMessage.isNullOrEmpty()) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            // Limpiar los extras para no repetir el mensaje
            intent.removeExtra(SplashLoginActivity.EXTRA_LOGIN_STATUS)
            intent.removeExtra(SplashLoginActivity.EXTRA_ERROR_MESSAGE)
        }
        
        // Verificar sesión activa
        if (auth.currentUser != null) {
            val lastLoginTime = sharedPreferences.getLong("last_login_time", 0)
            val currentTime = Date().time

            if (currentTime - lastLoginTime > SESSION_TIMEOUT) {
                // La sesión ha expirado, cerrar sesión
                auth.signOut()
                sharedPreferences.edit().clear().apply()
            } else {
                // IMPORTANTE: No podemos ir directamente a MapsActivity porque necesitamos
                // asegurarnos de que los datos precargados (perros, ubicaciones, etc.) estén
                // inicializados. Por eso redirigimos a SplashLoginActivity con un tipo especial
                // de login que indica que hay una sesión activa, y SplashLoginActivity se
                // encargará de cargar los datos antes de ir a MapsActivity.
                Log.d(TAG, "Sesión activa detectada en onResume, redirigiendo a SplashLoginActivity")
                val intent = Intent(this, SplashLoginActivity::class.java).apply {
                    putExtra(SplashLoginActivity.EXTRA_LOGIN_TYPE, SplashLoginActivity.LOGIN_TYPE_SESSION_ACTIVE)
                }
                startActivity(intent)
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

    /**
     * Aplica el tema configurado por el usuario según las preferencias guardadas.
     *
     * Este método verifica si el usuario ha habilitado el modo oscuro y lo aplica
     * mediante AppCompatDelegate.
     */
    private fun applyUserTheme() {
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}