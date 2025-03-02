package com.example.perros

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
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
        sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
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
     * - Actualiza la preferencia de notificaciones en SharedPreferences
     * - Muestra un mensaje informativo al usuario
     *
     * @param enable `true` para activar las notificaciones, `false` para desactivarlas
     */
    private fun toggleNotifications(enable: Boolean) {
        // Guardar preferencia
        sharedPreferences.edit {
            putBoolean("notifications_enabled", enable)
        }

        // Mostrar mensaje
        val message = if (enable) {
            "Notificaciones activadas"
        } else {
            "Notificaciones desactivadas"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
}