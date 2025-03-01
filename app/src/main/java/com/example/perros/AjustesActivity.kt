package com.example.perros

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.perros.databinding.ActivityAjustesBinding
import com.google.firebase.auth.FirebaseAuth

/**
 * Actividad que gestiona la configuración y preferencias de la aplicación.
 *
 * Esta actividad permite al usuario personalizar diferentes aspectos de la aplicación:
 * - Alternar entre el tema claro y oscuro
 * - Activar o desactivar las notificaciones
 * - Cerrar la sesión actual
 *
 * La actividad utiliza ViewBinding para interactuar con las vistas y SharedPreferences
 * para persistir las configuraciones del usuario entre sesiones.
 *
 * @property binding Objeto de vinculación para acceder a las vistas
 * @property sharedPreferences Almacenamiento de preferencias del usuario
 * @property auth Instancia de Firebase Authentication para gestionar la sesión
 */
class AjustesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAjustesBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var auth: FirebaseAuth

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
        binding = ActivityAjustesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicialización
        sharedPreferences = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        auth = FirebaseAuth.getInstance()

        // Configurar estado inicial de los switches
        setupInitialState()
        
        // Configurar listeners
        setupListeners()
    }

    /**
     * Configura el estado inicial de los componentes interactivos según las preferencias guardadas.
     *
     * Este método recupera las preferencias del usuario desde SharedPreferences y
     * actualiza la interfaz para reflejar esas preferencias:
     * - Estado del switch de tema oscuro/claro
     * - Estado del switch de notificaciones
     */
    private fun setupInitialState() {
        // Estado del tema (claro/oscuro)
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)
        binding.switchTema.isChecked = isDarkMode
        updateSwitchTheme(isDarkMode)
        
        // Estado de las notificaciones
        val notificationsEnabled = sharedPreferences.getBoolean("notifications_enabled", true)
        binding.switchNotificaciones.isChecked = notificationsEnabled
    }

    /**
     * Configura los listeners para todos los elementos interactivos de la interfaz.
     *
     * Este método establece:
     * - Listener para el botón de retroceso, que navega a MapsActivity
     * - Listener para el switch de tema, que alterna entre modo claro y oscuro
     * - Listener para el switch de notificaciones, que habilita/deshabilita las notificaciones
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
        try {
            val switchColor = if (isDarkMode) {
                getColor(R.color.switch_theme_dark)
            } else {
                getColor(R.color.switch_theme_light)
            }
            binding.switchTema.thumbTintList = android.content.res.ColorStateList.valueOf(switchColor)
        } catch (e: Exception) {
            // Si hay algún problema al aplicar el tinte, simplemente lo ignoramos
            // y dejamos el switch con su apariencia predeterminada
            e.printStackTrace()
        }
    }

    /**
     * Alterna entre el modo claro y oscuro de la aplicación.
     *
     * Este método:
     * 1. Guarda la preferencia del usuario en SharedPreferences
     * 2. Aplica el tema correspondiente a través de AppCompatDelegate
     *
     * El cambio de tema se aplica inmediatamente, pero para una aplicación
     * completa del tema puede requerirse reiniciar la actividad.
     *
     * @param enable `true` para activar el modo oscuro, `false` para el modo claro
     */
    private fun toggleDarkMode(enable: Boolean) {
        // Guardar preferencia
        sharedPreferences.edit().putBoolean("dark_mode", enable).apply()
        
        // Aplicar tema
        if (enable) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    /**
     * Activa o desactiva las notificaciones de la aplicación.
     *
     * Este método:
     * 1. Guarda la preferencia del usuario en SharedPreferences
     * 2. Muestra un mensaje Toast confirmando la acción
     *
     * La implementación actual solo guarda la preferencia. La lógica para
     * activar/desactivar las notificaciones reales debe implementarse
     * en un servicio de notificaciones específico.
     *
     * @param enable `true` para habilitar notificaciones, `false` para deshabilitarlas
     */
    private fun toggleNotifications(enable: Boolean) {
        // Guardar preferencia
        sharedPreferences.edit().putBoolean("notifications_enabled", enable).apply()
        
        // Mostrar mensaje informativo
        val message = if (enable) 
            "Notificaciones activadas" 
        else 
            "Notificaciones desactivadas"
        
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Cierra la sesión del usuario actual y redirige a la pantalla de inicio.
     *
     * Este método:
     * 1. Llama a `signOut()` en la instancia de FirebaseAuth
     * 2. Crea un Intent para navegar a MainActivity (pantalla de login)
     * 3. Establece flags para limpiar la pila de actividades
     * 4. Inicia la actividad MainActivity y finaliza la actual
     *
     * Al establecer los flags FLAG_ACTIVITY_NEW_TASK y FLAG_ACTIVITY_CLEAR_TASK,
     * se asegura que el usuario no pueda volver a esta pantalla presionando el
     * botón Atrás después de cerrar sesión.
     */
    private fun logout() {
        auth.signOut()
        
        // Redirigir a MainActivity o LoginActivity
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
} 