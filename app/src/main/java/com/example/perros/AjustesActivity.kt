package com.example.perros

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.perros.databinding.ActivityAjustesBinding
import com.example.perros.databinding.ItemDeveloperBinding
import com.google.firebase.auth.FirebaseAuth
import android.util.Base64
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import com.google.firebase.database.FirebaseDatabase
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * # AjustesActivity
 * 
 * Actividad de configuración que gestiona las preferencias y ajustes del sistema de monitorización.
 * 
 * ## Funcionalidad principal
 * Esta clase proporciona una interfaz completa para configurar la aplicación, permitiendo:
 * - Personalizar la interfaz con selección de tema claro/oscuro
 * - Configurar parámetros de los servicios de localización y monitorización
 * - Gestionar permisos críticos para el funcionamiento (ubicación, notificaciones)
 * - Optimizar el consumo de batería con ajustes de frecuencia de actualización
 * - Personalizar el comportamiento de las notificaciones de zonas seguras
 * - Acceder a la información del equipo de desarrollo
 * 
 * ## Características técnicas implementadas:
 * - **Material Design 3**: Componentes de configuración siguiendo las guías de diseño modernas
 * - **ViewBinding**: Acceso tipo-seguro a las vistas mejorando la seguridad del código
 * - **Preferencias persistentes**: Almacenamiento de ajustes mediante SharedPreferences
 * - **RecyclerView**: Lista optimizada para mostrar información de desarrolladores
 * - **Gestión de permisos**: Sistema adaptativo para solicitar permisos según versión de Android
 * - **Temas dinámicos**: Implementación del selector de tema claro/oscuro con aplicación inmediata
 * - **Optimización de batería**: Configuración de excepciones para el ahorro de energía
 * 
 * ## Parámetros configurables:
 * - Tema de la aplicación (claro/oscuro/sistema)
 * - Frecuencia de actualizaciones de ubicación
 * - Intervalo de notificaciones por salida de zona segura
 * - Tipos de alertas para eventos de seguridad
 * - Permisos de ubicación en primer y segundo plano
 * - Excepciones de optimización de batería
 * 
 * @property binding Objeto de ViewBinding para acceso tipo-seguro a los elementos de la UI
 * @property sharedPreferences Almacenamiento persistente para las preferencias del usuario
 * @property auth Instancia de Firebase Authentication para gestión de sesión
 * @property permissionsLauncher Gestor de solicitudes de permisos a través del sistema de contratos
 */
class AjustesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAjustesBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var auth: FirebaseAuth
    private lateinit var developerAdapter: DeveloperAdapter

    // IDs de los desarrolladores en Firebase
    private val developerIds = listOf(
        "oO4YEvacmudmJcDVDUsthvuJ1LK2",  // Primer desarrollador - Juan Borrás
        "JHwaR0t8uta3zehVD8qZEoqguPE2",  // Segundo desarrollador - Martín Peñalva
        "rFOjAOobNMRVgywJvogZsfavO8g2"   // Tercer desarrollador - Aritz Mendive
    )
    
    // Lista para almacenar los datos de los desarrolladores una vez cargados
    private val developersList = mutableListOf<DeveloperInfo>()

    // Para gestionar la respuesta de permisos de notificaciones
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permiso concedido
                sharedPreferences.edit {
                    putBoolean("notifications_enabled", true)
                }
                // Crear los canales de notificación
                createNotificationChannels()
                Toast.makeText(this, "Notificaciones activadas", Toast.LENGTH_SHORT).show()
                
                // Enviar una notificación de prueba
                sendTestNotification()
            } else {
                // Permiso denegado
                binding.switchNotificaciones.isChecked = false
                sharedPreferences.edit {
                    putBoolean("notifications_enabled", false)
                }
                Toast.makeText(this, "Permiso de notificaciones denegado", Toast.LENGTH_SHORT).show()
            }
        }

    /**
     * Inicializa la actividad, configura las vistas y establece los listeners.
     *
     * Este método:
     * - Infla el layout usando ViewBinding
     * - Inicializa los objetos de SharedPreferences y FirebaseAuth
     * - Configura el estado inicial de los switches basado en las preferencias guardadas
     * - Establece los listeners para los controles interactivos
     *
     * @param savedInstanceState Estado guardado de la actividad, puede ser nulo
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityAjustesBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // Inicialización
            sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
            auth = FirebaseAuth.getInstance()

            // Configurar estado inicial de los switches
            try {
                setupInitialState()
            } catch (e: Exception) {
                Log.e("AjustesActivity", "Error al configurar estado inicial: ${e.message}")
                Toast.makeText(this, "Error al cargar preferencias", Toast.LENGTH_SHORT).show()
            }

            // Configurar listeners
            try {
                setupListeners()
            } catch (e: Exception) {
                Log.e("AjustesActivity", "Error al configurar listeners: ${e.message}")
            }
            
            // Configurar RecyclerView para los desarrolladores
            try {
                setupDevelopersRecyclerView()
                
                // Cargar datos de los desarrolladores
                cargarDatosDesarrolladores()
            } catch (e: Exception) {
                Log.e("AjustesActivity", "Error al configurar desarrolladores: ${e.message}")
                // No bloqueamos la navegación si falla esta característica secundaria
            }
        } catch (e: Exception) {
            Log.e("AjustesActivity", "Error fatal en onCreate: ${e.message}")
            Toast.makeText(this, "Error al inicializar la pantalla de ajustes", Toast.LENGTH_LONG).show()
            // Regresar a la actividad anterior si hay un error crítico
            finish()
        }
    }
    
    /**
     * Configura el RecyclerView para mostrar los desarrolladores.
     */
    private fun setupDevelopersRecyclerView() {
        try {
            developerAdapter = DeveloperAdapter(developersList) { developer ->
                openDeveloperProfile(developer)
            }
            
            binding.recyclerViewDevelopers.apply {
                layoutManager = LinearLayoutManager(this@AjustesActivity)
                adapter = developerAdapter
            }
            Log.d("AjustesActivity", "RecyclerView de desarrolladores configurado correctamente")
        } catch (e: Exception) {
            Log.e("AjustesActivity", "Error al configurar RecyclerView: ${e.message}")
            Toast.makeText(this, "Error al cargar la lista de desarrolladores", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Carga los datos de los desarrolladores desde Firebase.
     */
    private fun cargarDatosDesarrolladores() {
        try {
            val database = FirebaseDatabase.getInstance().reference
            
            // Limpiar la lista antes de cargar nuevos datos
            developersList.clear()
            
            // Para cada desarrollador
            for (userId in developerIds) {
                database.child("users").child(userId).get().addOnSuccessListener { snapshot ->
                    try {
                        if (snapshot.exists()) {
                            // Obtener datos del usuario
                            val nombre = snapshot.child("nombre").getValue(String::class.java) ?: "Desconocido"
                            val apellidos = snapshot.child("apellidos").getValue(String::class.java) ?: ""
                            val email = snapshot.child("email").getValue(String::class.java) ?: ""
                            val fechaNacimiento = snapshot.child("fechaNacimiento").getValue(String::class.java) ?: ""
                            val isPerro = snapshot.child("isPerro").getValue(Boolean::class.java) ?: false
                            val imagenBase64 = snapshot.child("imagenBase64").getValue(String::class.java) ?: ""
                            
                            // Crear objeto con los datos
                            val info = DeveloperInfo(
                                nombre = nombre,
                                apellidos = apellidos,
                                email = email,
                                fechaNacimiento = fechaNacimiento,
                                imagenBase64 = imagenBase64,
                                isPerro = isPerro,
                                userId = userId
                            )
                            
                            // Añadir a la lista
                            developersList.add(info)
                            
                            // Actualizar el adaptador de forma segura
                            runOnUiThread {
                                try {
                                    if (::developerAdapter.isInitialized) {
                                        developerAdapter.notifyDataSetChanged()
                                    }
                                    Log.d("AjustesActivity", "Datos cargados para desarrollador $userId: $nombre")
                                } catch (e: Exception) {
                                    Log.e("AjustesActivity", "Error al actualizar adaptador: ${e.message}")
                                }
                            }
                        } else {
                            Log.e("AjustesActivity", "No se encontraron datos para el desarrollador $userId")
                        }
                    } catch (e: Exception) {
                        Log.e("AjustesActivity", "Error procesando datos de desarrollador: ${e.message}")
                    }
                }.addOnFailureListener { error ->
                    Log.e("AjustesActivity", "Error al cargar datos del desarrollador $userId", error)
                }
            }
        } catch (e: Exception) {
            Log.e("AjustesActivity", "Error general al cargar desarrolladores: ${e.message}")
        }
    }

    /**
     * Configura el estado inicial de los componentes interactivos según las preferencias guardadas.
     *
     * Este método recupera las preferencias del usuario desde SharedPreferences y
     * actualiza la interfaz para reflejar esas preferencias:
     * - Estado del switch de tema oscuro/claro
     * - Estado del switch de notificaciones
     * - Estado del switch de optimización de batería
     */
    private fun setupInitialState() {
        // Estado del tema (claro/oscuro)
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)
        binding.switchTema.isChecked = isDarkMode
        updateSwitchTheme(isDarkMode)

        // Estado de las notificaciones
        val notificationsEnabled = sharedPreferences.getBoolean("notifications_enabled", true)
        
        // Verificar si realmente tenemos el permiso cuando las notificaciones están habilitadas
        if (notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Para Android 13 o superior
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            
            // Actualizar la preferencia si no coincide con el estado real de los permisos
            if (notificationsEnabled != hasPermission) {
                sharedPreferences.edit {
                    putBoolean("notifications_enabled", hasPermission)
                }
                binding.switchNotificaciones.isChecked = hasPermission
            } else {
                binding.switchNotificaciones.isChecked = notificationsEnabled
            }
        } else {
            binding.switchNotificaciones.isChecked = notificationsEnabled
        }
        
        // Estado de la optimización de batería (por defecto desactivada)
        val batteryOptEnabled = sharedPreferences.getBoolean("ignore_battery_optimization", false)
        binding.switchOptimizacionBateria.isChecked = batteryOptEnabled
        
        // Comprobar el estado real de la optimización de batería
        if (batteryOptEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName
            
            val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
            
            // Si la preferencia dice que está habilitado pero realmente no lo está, actualizar
            if (batteryOptEnabled && !isIgnoringBatteryOptimizations) {
                binding.switchOptimizacionBateria.isChecked = false
                sharedPreferences.edit {
                    putBoolean("ignore_battery_optimization", false)
                }
            }
        }
    }
    
    /**
     * Abre la actividad de perfil del desarrollador seleccionado.
     * 
     * @param developer Información del desarrollador
     */
    private fun openDeveloperProfile(developer: DeveloperInfo) {
        val intent = Intent(this, PerfilUsuario::class.java).apply {
            putExtra("FROM_SETTINGS", true)
            putExtra("DEVELOPER_ID", developer.userId)
            putExtra("DEVELOPER_NAME", developer.nombre)
            putExtra("DEVELOPER_LASTNAME", developer.apellidos)
            putExtra("DEVELOPER_EMAIL", developer.email)
            putExtra("DEVELOPER_BIRTHDAY", developer.fechaNacimiento)
            putExtra("DEVELOPER_IS_DOG", developer.isPerro)
            putExtra("DEVELOPER_USER_ID", developer.userId)
        }
        startActivity(intent)
    }

    /**
     * Configura los listeners para todos los elementos interactivos de la interfaz.
     *
     * Este método establece:
     * - Listener para el botón de retroceso, que navega a MapsActivity
     * - Listener para el switch de tema, que alterna entre modo claro y oscuro
     * - Listener para el switch de notificaciones, que habilita/deshabilita las notificaciones
     * - Listener para el switch de optimización de batería
     * - Listener para el botón de probar notificaciones, que envía una notificación de prueba
     * - Listener para el botón de cerrar sesión, que termina la sesión actual
     */
    private fun setupListeners() {
        // Botón para volver atrás
        binding.btnBack.setOnClickListener {
            val intent = Intent(this, MapsActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Switch para cambiar tema
        binding.switchTema.setOnCheckedChangeListener { _, isChecked ->
            toggleDarkMode(isChecked)
            updateSwitchTheme(isChecked)
        }

        // Switch para notificaciones
        binding.switchNotificaciones.setOnCheckedChangeListener { _, isChecked ->
            toggleNotifications(isChecked)
        }
        
        // Switch para optimización de batería
        binding.switchOptimizacionBateria.setOnCheckedChangeListener { _, isChecked ->
            toggleBatteryOptimization(isChecked)
        }
        
        // Botón para probar notificaciones
        binding.btnProbarNotificacion.setOnClickListener {
            if (sharedPreferences.getBoolean("notifications_enabled", true)) {
                // Si las notificaciones están activadas
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        // Ya tenemos permiso, enviar notificación de prueba
                        sendTestNotification()
                        Toast.makeText(this, "Enviando notificación de prueba...", Toast.LENGTH_SHORT).show()
                    } else {
                        // No tenemos permiso, solicitar permiso
                        Toast.makeText(this, "Se requiere permiso para enviar notificaciones", Toast.LENGTH_SHORT).show()
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    // Versiones anteriores a Android 13, no necesitan permiso
                    sendTestNotification()
                    Toast.makeText(this, "Enviando notificación de prueba...", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Las notificaciones están desactivadas
                Toast.makeText(this, "Activa las notificaciones primero", Toast.LENGTH_SHORT).show()
                binding.switchNotificaciones.isChecked = true
            }
        }

        // Botón para cerrar sesión
        binding.btnCerrarSesion.setOnClickListener {
            logout()
        }
    }

    /**
     * Actualiza la apariencia del switch de tema según el modo actual.
     *
     * @param isDarkMode `true` si estamos en modo oscuro, `false` si estamos en modo claro
     */
    private fun updateSwitchTheme(isDarkMode: Boolean) {
        binding.switchTema.text = if (isDarkMode) "Tema oscuro" else "Tema claro"
    }

    /**
     * Activa o desactiva el modo oscuro en la aplicación.
     *
     * Este método:
     * - Actualiza la preferencia de modo oscuro en SharedPreferences
     * - Aplica el cambio de tema a nivel de sistema usando AppCompatDelegate
     * - Muestra un mensaje informativo al usuario
     *
     * @param enable `true` para activar el modo oscuro, `false` para desactivarlo
     */
    private fun toggleDarkMode(enable: Boolean) {
        // Guardar preferencia
        sharedPreferences.edit {
            putBoolean("dark_mode", enable)
        }

        // Aplicar tema
        val mode = if (enable) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)

        // Mostrar mensaje
        val message = if (enable) "Modo oscuro activado" else "Modo claro activado"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Activa o desactiva las notificaciones de la aplicación.
     *
     * Este método:
     * - Solicita permisos de notificaciones con el diálogo estándar cuando se activa
     * - Revoca permisos de notificaciones directamente cuando se desactiva
     * - Actualiza la preferencia en SharedPreferences
     *
     * @param enable `true` para activar las notificaciones, `false` para desactivarlas
     */
    private fun toggleNotifications(enable: Boolean) {
        if (enable) {
            // Si el usuario quiere activar las notificaciones, solicitar permiso
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13 (API 33) o superior requiere permiso POST_NOTIFICATIONS
                // Siempre solicitamos el permiso para asegurarnos que aparece el diálogo
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                // El resto de la lógica se maneja en el callback del launcher
            } else {
                // Para versiones anteriores a Android 13
                sharedPreferences.edit {
                    putBoolean("notifications_enabled", true)
                }
                createNotificationChannels() // Crear canales de notificación
                Toast.makeText(this, "Notificaciones activadas", Toast.LENGTH_SHORT).show()
                
                // Enviar una notificación de prueba
                sendTestNotification()
            }
        } else {
            // Si el usuario quiere desactivar las notificaciones
            sharedPreferences.edit {
                putBoolean("notifications_enabled", false)
            }
            
            // Desactivar las notificaciones a nivel de sistema
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Desactivar todos los canales de notificación en Android 8.0+
                notificationManager.notificationChannels.forEach { channel ->
                    notificationManager.deleteNotificationChannel(channel.id)
                }
            }
            
            // Cancelar todas las notificaciones activas
            notificationManager.cancelAll()
            
            Toast.makeText(this, "Notificaciones desactivadas", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Crea los canales de notificación necesarios para la aplicación.
     * 
     * Para Android 8.0 (API 26) o superior, los canales de notificación son obligatorios.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Canal para alertas importantes (alta prioridad)
            val importantChannelId = "important_channel"
            val importantChannelName = "Alertas importantes"
            val importantChannelDescription = "Notificaciones urgentes de PawTracker"
            val importantChannel = android.app.NotificationChannel(
                importantChannelId,
                importantChannelName,
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = importantChannelDescription
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 100)
            }
            notificationManager.createNotificationChannel(importantChannel)
            
            // Canal para actualizaciones generales (prioridad normal)
            val generalChannelId = "general_channel"
            val generalChannelName = "Actualizaciones generales"
            val generalChannelDescription = "Notificaciones generales de PawTracker"
            val generalChannel = android.app.NotificationChannel(
                generalChannelId,
                generalChannelName,
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = generalChannelDescription
            }
            notificationManager.createNotificationChannel(generalChannel)
            
            Log.d("AjustesActivity", "Canales de notificación creados")
        }
    }

    /**
     * Envía una notificación de prueba simple para verificar que los permisos están correctamente configurados.
     */
    private fun sendTestNotification() {
        // Obtener el NotificationManager
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Crear el builder de la notificación
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            androidx.core.app.NotificationCompat.Builder(this, "important_channel")
        } else {
            androidx.core.app.NotificationCompat.Builder(this)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
        }
        
        // Configurar la notificación
        builder.setSmallIcon(R.drawable.app_icon_paw)
            .setContentTitle("Notificación de prueba")
            .setContentText("Las notificaciones están funcionando correctamente")
            .setAutoCancel(true)
        
        // Crear un intent para abrir la app al pulsar la notificación
        val intent = Intent(this, MapsActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(pendingIntent)
        
        // Mostrar la notificación
        notificationManager.notify(1001, builder.build())
        
        Log.d("AjustesActivity", "Notificación de prueba enviada")
    }

    /**
     * Cierra la sesión del usuario actual.
     *
     * Este método:
     * - Cierra la sesión en Firebase Authentication
     * - Muestra un mensaje informativo
     * - Redirige al usuario a la pantalla de login
     */
    private fun logout() {
        // Cerrar sesión en Firebase
        auth.signOut()

        // Mostrar mensaje
        Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()

        // Ir a LoginActivity
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    /**
     * Activa o desactiva la optimización de batería para la aplicación.
     *
     * Si se activa, solicita al usuario que desactive las optimizaciones de batería
     * para permitir que la app funcione correctamente en segundo plano.
     *
     * @param enable `true` para solicitar desactivar las optimizaciones, `false` para no hacer nada
     */
    private fun toggleBatteryOptimization(enable: Boolean) {
        if (enable) {
            // Guardar que el usuario quiere desactivar las optimizaciones
            sharedPreferences.edit {
                putBoolean("ignore_battery_optimization", true)
            }
            
            // Solicitar desactivar las optimizaciones
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                val packageName = packageName
                
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    // Mostrar diálogo explicando por qué se necesita
                    val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Optimización de batería")
                        .setMessage("Para que la aplicación pueda monitorear continuamente la ubicación de tu perro, es necesario desactivar la optimización de batería. Se abrirá la configuración para que puedas permitirlo.")
                        .setPositiveButton("Aceptar") { _, _ ->
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        }
                        .setNegativeButton("Cancelar") { _, _ ->
                            // Si el usuario cancela, revertir el switch
                            binding.switchOptimizacionBateria.isChecked = false
                            sharedPreferences.edit {
                                putBoolean("ignore_battery_optimization", false)
                            }
                        }
                    
                    dialogBuilder.show()
                } else {
                    Toast.makeText(this, "La optimización de batería ya está desactivada", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Para versiones anteriores a Android 6, no es necesario
                Toast.makeText(this, "No es necesario en esta versión de Android", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Guardar que el usuario no quiere desactivar las optimizaciones
            sharedPreferences.edit {
                putBoolean("ignore_battery_optimization", false)
            }
            
            Toast.makeText(this, "El monitoreo en segundo plano puede verse afectado por las optimizaciones de batería", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * DeveloperInfo
     *
     * Funcionalidad principal
     * 
     * Clase de datos que encapsula la información relevante de un desarrollador
     * para su visualización en la interfaz de usuario dentro de la pantalla de ajustes.
     * 
     * Características técnicas implementadas
     * 
     * - Parcelable: Implementación para facilitar la transferencia entre componentes Android
     * - Estructura optimizada: Organización eficiente de los datos básicos de desarrollador
     * - Formato cohesivo: Estructura alineada con el modelo de datos de usuarios
     * - Soporte para imágenes: Campo dedicado para almacenamiento de imagen de perfil
     * 
     * @property nombre Nombre del desarrollador
     * @property apellidos Apellidos del desarrollador
     * @property email Correo electrónico de contacto
     * @property fechaNacimiento Fecha de nacimiento en formato DD/MM/YYYY
     * @property imagenBase64 Imagen de perfil codificada en Base64 (opcional)
     * @property isPerro Indicador de tipo de perfil (siempre false para desarrolladores)
     * @property userId Identificador único del desarrollador
     */
    @Parcelize
    data class DeveloperInfo(
        val nombre: String,
        val apellidos: String,
        val email: String,
        val fechaNacimiento: String,
        val imagenBase64: String?,
        val isPerro: Boolean = false,
        val userId: String
    ) : Parcelable {
        // ... existing code ...
    }
    
    /**
     * DeveloperAdapter
     *
     * Funcionalidad principal
     * 
     * Adaptador especializado para la visualización de información de desarrolladores
     * en un RecyclerView, proporcionando una interfaz visual consistente y optimizada.
     * 
     * Características técnicas implementadas
     * 
     * - ViewBinding: Integración eficiente con las vistas del item de desarrollador
     * - Patrón ViewHolder: Implementación de caché de vistas para mejor rendimiento
     * - Manejo de eventos: Sistema de callback para responder a clics en elementos
     * - Gestión de imágenes: Carga optimizada de imágenes de perfil desde Base64
     * - Manejo de errores: Visualización de imágenes por defecto en caso de problemas
     * 
     * @property developers Lista de objetos DeveloperInfo a mostrar
     * @property onDeveloperClick Función de callback invocada al hacer clic en un desarrollador
     */
    inner class DeveloperAdapter(
        private val developers: List<DeveloperInfo>,
        private val onDeveloperClick: (DeveloperInfo) -> Unit
    ) : RecyclerView.Adapter<DeveloperAdapter.DeveloperViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeveloperViewHolder {
            val binding = ItemDeveloperBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return DeveloperViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: DeveloperViewHolder, position: Int) {
            val developer = developers[position]
            holder.bind(developer)
        }
        
        override fun getItemCount() = developers.size
        
        /**
         * DeveloperViewHolder
         *
         * Funcionalidad principal
         * 
         * ViewHolder que mantiene referencias a los componentes visuales de cada elemento
         * de desarrollador, optimizando el rendimiento del reciclaje de vistas y facilitando
         * la vinculación de datos con la interfaz.
         * 
         * Características técnicas implementadas
         * 
         * - Binding eficiente: Acceso directo a elementos visuales sin findViewById
         * - Patrón de reciclaje: Soporte para la reutilización eficiente de vistas
         * - Carga asíncrona: Procesamiento de imágenes en segundo plano
         * - Manejo de errores: Control de excepciones durante la carga de imágenes
         * 
         * @property binding Objeto de ViewBinding que contiene referencias a las vistas
         */
        inner class DeveloperViewHolder(private val binding: ItemDeveloperBinding) :
            RecyclerView.ViewHolder(binding.root) {
            
            fun bind(developer: DeveloperInfo) {
                try {
                    binding.tvDeveloperName.text = "${developer.nombre} ${developer.apellidos}"
                    
                    // Cargar imagen con formato circular
                    try {
                        binding.imgDeveloper.loadBase64Image(developer.imagenBase64, applyCircleCrop = true)
                    } catch (e: Exception) {
                        Log.e("DeveloperAdapter", "Error al cargar imagen: ${e.message}")
                        // Establecer una imagen por defecto en caso de error
                        binding.imgDeveloper.setImageResource(R.drawable.app_icon_paw)
                    }
                    
                    // Configurar click listener
                    binding.root.setOnClickListener {
                        onDeveloperClick(developer)
                    }
                } catch (e: Exception) {
                    Log.e("DeveloperAdapter", "Error al vincular datos: ${e.message}")
                }
            }
        }
    }
}