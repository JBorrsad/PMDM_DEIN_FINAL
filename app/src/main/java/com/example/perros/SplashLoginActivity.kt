package com.example.perros

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import com.example.perros.preloadImages
import com.example.perros.preloadUserData
import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import androidx.core.content.edit

/**
 * # SplashLoginActivity
 * 
 * Pantalla de inicio con animaciones que maneja la autenticación y precarga de recursos
 * para el sistema de monitorización de perros.
 * 
 * ## Funcionalidad principal
 * Esta clase actúa como punto de entrada de la aplicación, proporcionando:
 * - Experiencia visual atractiva mientras se cargan los recursos iniciales
 * - Gestión completa del proceso de autenticación con múltiples métodos
 * - Precarga optimizada de datos e imágenes necesarios para la aplicación
 * - Transición fluida hacia la pantalla principal mediante animaciones 
 * - Verificación de estado de sesión para usuarios recurrentes
 * - Sincronización inicial con Firebase para los datos del usuario
 * 
 * ## Características técnicas implementadas:
 * - **Splash Screen API**: Integración con el sistema nativo de pantalla de inicio de Android
 * - **Animaciones sofisticadas**: ObjectAnimator para movimientos y transiciones fluidas
 * - **Corrutinas de Kotlin**: Operaciones asíncronas para precarga sin bloquear la UI
 * - **Firebase Authentication**: Soporte para inicio de sesión con email/password y Google
 * - **Firebase Realtime Database**: Sincronización inicial de datos de usuario y perros
 * - **Material Design 3**: Componentes visuales modernos y consistentes con las guías de diseño
 * - **Transiciones de actividad**: Efectos visuales avanzados entre pantallas
 * 
 * ## Proceso de inicio:
 * 1. Muestra animaciones de carga con el logo de la aplicación
 * 2. Verifica si existe una sesión previa activa
 * 3. Permite al usuario elegir entre varios métodos de autenticación
 * 4. Ejecuta procesos de precarga de datos mientras se realiza la autenticación
 * 5. Redirige al usuario a la pantalla principal de mapas una vez completado
 * 
 * Esta actividad mejora significativamente la experiencia de primer uso y
 * recurrente, optimizando los tiempos de carga y proporcionando feedback visual.
 * 
 * @property auth Instancia de FirebaseAuth para la gestión de la autenticación de usuarios
 * @property progressBar Barra de progreso animada que muestra el estado de la precarga
 * @property statusText Información textual sobre el proceso de carga actual
 * @property preloadCounter Contador atómico para coordinar múltiples procesos de precarga
 */
class SplashLoginActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SplashLoginActivity"
        
        // Tipos de login
        const val LOGIN_TYPE_EMAIL = 1
        const val LOGIN_TYPE_GOOGLE = 2
        const val LOGIN_TYPE_ANONYMOUS = 3
        // LOGIN_TYPE_SESSION_ACTIVE se usa cuando el usuario ya tiene una sesión activa
        // y viene directamente de MainActivity sin necesidad de volver a autenticarse
        const val LOGIN_TYPE_SESSION_ACTIVE = 4
        
        // Estados de login
        @Suppress("unused")
        const val STATUS_SUCCESS = 1
        const val STATUS_FAILED = 0
        
        // Extras para Intent
        const val EXTRA_LOGIN_TYPE = "login_type"
        const val EXTRA_EMAIL = "email"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_REMEMBER_ME = "remember_me"
        const val EXTRA_ID_TOKEN = "id_token"
        const val EXTRA_LOGIN_STATUS = "login_status"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        
        // Tiempo mínimo de splash (ms)
        private const val MIN_SPLASH_TIME = 1500
    }
    
    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var logoImage: ImageView
    
    // Control de carga
    private val loadingTasksCount = AtomicInteger(0)
    private var startTime = 0L
    private var loginSuccess = false
    
    /**
     * Inicializa la actividad y configura las animaciones y procesos de carga.
     * 
     * Este método establece:
     * - La configuración visual de la pantalla de inicio
     * - Las referencias a los elementos de UI para animaciones
     * - La instancia de Firebase Authentication
     * - El acceso a las preferencias compartidas
     * - Las animaciones iniciales de los elementos visuales
     * - La verificación de sesión de usuario existente
     * 
     * Además, inicia la secuencia asíncrona de autenticación y precarga
     * que se ejecuta en paralelo para optimizar el tiempo de inicio.
     * 
     * @param savedInstanceState Estado guardado de la instancia si la actividad se está recreando
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_login)
        
        // Inicializar componentes
        auth = FirebaseAuth.getInstance()
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        logoImage = findViewById(R.id.logoImage)
        sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        
        // Inicializar tiempo de inicio
        startTime = System.currentTimeMillis()
        
        // Configurar animaciones iniciales
        setupInitialAnimations()
        
        // Verificar si hay una sesión activa
        checkExistingSession()
    }
    
    /**
     * Inicia las animaciones de la pantalla de splash
     */
    private fun setupInitialAnimations() {
        // Animación de pulsación del logo
        val pulseAnimation = ObjectAnimator.ofFloat(logoImage, "scaleX", 1f, 1.1f, 1f)
        pulseAnimation.duration = 1500
        pulseAnimation.interpolator = AccelerateDecelerateInterpolator()
        pulseAnimation.repeatCount = ObjectAnimator.INFINITE
        pulseAnimation.start()
        
        // Animación complementaria en Y
        val pulseAnimationY = ObjectAnimator.ofFloat(logoImage, "scaleY", 1f, 1.1f, 1f)
        pulseAnimationY.duration = 1500
        pulseAnimationY.interpolator = AccelerateDecelerateInterpolator()
        pulseAnimationY.repeatCount = ObjectAnimator.INFINITE
        pulseAnimationY.start()
    }
    
    /**
     * Verifica si existe una sesión activa
     */
    private fun checkExistingSession() {
        val loginType = intent.getIntExtra(EXTRA_LOGIN_TYPE, LOGIN_TYPE_EMAIL)
        when (loginType) {
            LOGIN_TYPE_EMAIL -> {
                val email = intent.getStringExtra(EXTRA_EMAIL) ?: ""
                val password = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
                val rememberMe = intent.getBooleanExtra(EXTRA_REMEMBER_ME, false)
                
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    loginWithEmail(email, password, rememberMe)
                } else {
                    fallarLogin("Email o contraseña vacíos")
                }
            }
            LOGIN_TYPE_GOOGLE -> {
                val idToken = intent.getStringExtra(EXTRA_ID_TOKEN)
                if (idToken != null) {
                    loginWithGoogle(idToken)
                } else {
                    fallarLogin("Token de Google no proporcionado")
                }
            }
            LOGIN_TYPE_SESSION_ACTIVE -> {
                // Este tipo de login se usa cuando el usuario ya tiene una sesión activa
                // (viene de MainActivity con una sesión no expirada)
                // No necesitamos autenticar al usuario nuevamente, simplemente actualizamos
                // el tiempo de sesión y cargamos los datos necesarios para la aplicación
                Log.d(TAG, "Procesando sesión activa, cargando datos sin re-autenticación")
                updateStatus("Actualizando datos...")
                loginSuccess = true
                
                // Registrar tiempo de login para timeout de sesión
                // Esto mantiene la sesión activa otros 5 minutos más
                sharedPreferences.edit {
                    putLong("last_login_time", Date().time)
                }
                
                // Cargar los datos iniciales (perros, ubicaciones, etc.)
                cargarDatosIniciales()
            }
            else -> {
                fallarLogin("Tipo de login no válido")
            }
        }
    }
    
    /**
     * Realiza el inicio de sesión con email y contraseña
     */
    private fun loginWithEmail(email: String, password: String, rememberMe: Boolean) {
        updateStatus("Iniciando sesión...")
        
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Login con email exitoso")
                    
                    // Guardar email si "recordarme" está activado
                    if (rememberMe) {
                        sharedPreferences.edit {
                            putString("saved_email", email)
                            putBoolean("remember_me", true)
                        }
                    } else {
                        sharedPreferences.edit {
                            remove("saved_email")
                            putBoolean("remember_me", false)
                        }
                    }
                    
                    // Registrar tiempo de login para timeout de sesión
                    sharedPreferences.edit {
                        putLong("last_login_time", Date().time)
                    }
                    
                    loginSuccess = true
                    cargarDatosIniciales()
                } else {
                    Log.e(TAG, "Error en login con email", task.exception)
                    fallarLogin("Error en la autenticación: ${task.exception?.message}")
                }
            }
    }
    
    /**
     * Realiza el inicio de sesión con Google
     */
    private fun loginWithGoogle(idToken: String) {
        updateStatus("Iniciando sesión con Google...")
        
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Login con Google exitoso")
                    
                    // Registrar tiempo de login para timeout de sesión
                    sharedPreferences.edit {
                        putLong("last_login_time", Date().time)
                    }
                    
                    loginSuccess = true
                    cargarDatosIniciales()
                } else {
                    Log.e(TAG, "Error en login con Google", task.exception)
                    fallarLogin("Error en la autenticación con Google: ${task.exception?.message}")
                }
            }
    }
    
    /**
     * Carga los datos iniciales necesarios para la aplicación
     */
    private fun cargarDatosIniciales() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            fallarLogin("No se pudo obtener el ID del usuario")
            return
        }
        
        updateStatus("Cargando datos...")
        
        // Limpiar datos previos
        DatosPrecargados.limpiarDatos()
        
        // Guardar ID del usuario
        DatosPrecargados.guardarIdUsuario(userId)
        
        // Incrementar contador de tareas
        loadingTasksCount.incrementAndGet()
        
        // 1. Cargar datos del usuario
        updateStatus("Cargando perfil...")
        val database = FirebaseDatabase.getInstance().reference
        database.child("users").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Guardar datos del usuario
                    DatosPrecargados.guardarDatosUsuario(snapshot)
                    DatosPrecargados.guardarUsuario(userId, snapshot)
                    
                    // Precargar imágenes
                    val imagenBase64 = snapshot.child("imagenBase64").getValue(String::class.java)
                    if (!imagenBase64.isNullOrEmpty()) {
                        preloadImages(applicationContext, listOf(imagenBase64))
                    }
                    
                    taskCompleted()
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error al cargar datos del usuario", error.toException())
                    taskCompleted()
                }
            })
        
        // 2. Cargar perros del usuario
        loadingTasksCount.incrementAndGet()
        updateStatus("Cargando mascotas...")
        database.child("users")
            .orderByChild("dueñoId")
            .equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Guardar lista de perros
                        DatosPrecargados.guardarPerrosUsuario(userId, snapshot)
                        
                        // Lista para precargar imágenes
                        val imagesList = mutableListOf<String>()
                        
                        // Contador para tareas adicionales
                        val perrosCount = AtomicInteger(snapshot.childrenCount.toInt())
                        
                        // Para cada perro, cargar su ubicación y zona segura
                        snapshot.children.forEach { perroSnapshot ->
                            val perroId = perroSnapshot.key ?: return@forEach
                            val isPerro = perroSnapshot.child("isPerro").getValue(Boolean::class.java) == true
                            
                            if (isPerro) {
                                // Guardar perro individualmente
                                DatosPrecargados.guardarPerro(perroId, perroSnapshot)
                                
                                // Recopilar imagen para precargar
                                val imagenBase64 = perroSnapshot.child("imagenBase64").getValue(String::class.java)
                                if (!imagenBase64.isNullOrEmpty()) {
                                    imagesList.add(imagenBase64)
                                }
                                
                                // 3. Cargar ubicación del perro
                                loadingTasksCount.incrementAndGet()
                                database.child("locations").child(perroId)
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(ubicacionSnapshot: DataSnapshot) {
                                            DatosPrecargados.guardarUbicacionPerro(perroId, ubicacionSnapshot)
                                            taskCompleted()
                                        }
                                        
                                        override fun onCancelled(error: DatabaseError) {
                                            Log.e(TAG, "Error al cargar ubicación del perro $perroId", error.toException())
                                            taskCompleted()
                                        }
                                    })
                                
                                // 4. Cargar zona segura del perro
                                loadingTasksCount.incrementAndGet()
                                database.child("users").child(perroId).child("zonaSegura")
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(zonaSnapshot: DataSnapshot) {
                                            DatosPrecargados.guardarZonaSeguraPerro(perroId, zonaSnapshot)
                                            taskCompleted()
                                        }
                                        
                                        override fun onCancelled(error: DatabaseError) {
                                            Log.e(TAG, "Error al cargar zona segura del perro $perroId", error.toException())
                                            taskCompleted()
                                        }
                                    })
                                
                                // 5. Cargar dueño del perro (si es necesario)
                                val duenoId = perroSnapshot.child("dueñoId").getValue(String::class.java)
                                if (duenoId != null && duenoId != userId) {
                                    loadingTasksCount.incrementAndGet()
                                    database.child("users").child(duenoId)
                                        .addListenerForSingleValueEvent(object : ValueEventListener {
                                            override fun onDataChange(duenoSnapshot: DataSnapshot) {
                                                DatosPrecargados.guardarUsuario(duenoId, duenoSnapshot)
                                                
                                                // Recopilar imagen del dueño para precargar
                                                val imagenDueno = duenoSnapshot.child("imagenBase64").getValue(String::class.java)
                                                if (!imagenDueno.isNullOrEmpty()) {
                                                    imagesList.add(imagenDueno)
                                                }
                                                
                                                taskCompleted()
                                            }
                                            
                                            override fun onCancelled(error: DatabaseError) {
                                                Log.e(TAG, "Error al cargar dueño $duenoId", error.toException())
                                                taskCompleted()
                                            }
                                        })
                                }
                            }
                            
                            // Contar que se procesó un perro
                            if (perrosCount.decrementAndGet() == 0) {
                                // Precargar todas las imágenes recopiladas
                                if (imagesList.isNotEmpty()) {
                                    preloadImages(applicationContext, imagesList)
                                }
                            }
                        }
                    }
                    
                    taskCompleted()
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error al cargar perros del usuario", error.toException())
                    taskCompleted()
                }
            })
    }
    
    /**
     * Método llamado cuando una tarea de carga se completa
     */
    private fun taskCompleted() {
        val remainingTasks = loadingTasksCount.decrementAndGet()
        Log.d(TAG, "Tarea completada. Quedan: $remainingTasks")
        
        runOnUiThread {
            // Actualizar progreso en la UI
            val progress = 100 - (remainingTasks * 10).coerceAtMost(100)
            progressBar.progress = progress
            
            // Si todas las tareas están completas y el login fue exitoso
            if (remainingTasks <= 0 && loginSuccess) {
                // Marcar inicialización como completa
                DatosPrecargados.marcarInicializacionCompleta()
                
                // Mostrar resumen de datos cargados
                DatosPrecargados.mostrarEstadoDatos()
                
                // Calcular tiempo transcurrido
                val elapsedTime = System.currentTimeMillis() - startTime
                
                if (elapsedTime >= MIN_SPLASH_TIME) {
                    // Si ya pasó el tiempo mínimo, ir a la siguiente actividad
                    iniciarMapActivity()
                } else {
                    // Esperar hasta completar el tiempo mínimo
                    val remainingTime = MIN_SPLASH_TIME - elapsedTime
                    updateStatus("¡Datos cargados! Iniciando aplicación...")
                    // Usar lifecycleScope en lugar de Handler para programar tareas demoradas
                    lifecycleScope.launch {
                        delay(remainingTime)
                        iniciarMapActivity()
                    }
                }
            }
        }
    }
    
    /**
     * Actualiza el texto de estado en la UI
     */
    private fun updateStatus(message: String) {
        runOnUiThread {
            statusText.text = message
        }
    }
    
    /**
     * Inicia la actividad principal (MapsActivity)
     */
    private fun iniciarMapActivity() {
        val intent = Intent(this, MapsActivity::class.java)
        
        // Crear ActivityOptions con animaciones de transición
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this,
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
        
        // Iniciar actividad con las opciones de animación
        startActivity(intent, options.toBundle())
        finish()
        // Ya no es necesario usar overridePendingTransition
    }
    
    /**
     * Maneja el fallo de inicio de sesión
     */
    private fun fallarLogin(errorMessage: String) {
        Log.e(TAG, "Fallo en login: $errorMessage")
        loginSuccess = false
        
        // Devolver a MainActivity con mensaje de error
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra(EXTRA_LOGIN_STATUS, STATUS_FAILED)
        intent.putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
        startActivity(intent)
        finish()
    }
} 