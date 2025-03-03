package com.example.perros

import android.content.Intent

import android.os.Bundle

import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import android.util.Base64
import android.graphics.BitmapFactory

/**
 * PerfilUsuario
 * 
 * Funcionalidad principal
 * 
 * Clase que gestiona la visualización y edición del perfil de usuario dentro del
 * sistema de monitorización de mascotas. Proporciona una interfaz para ver la información
 * personal, editar el perfil, cerrar sesión y navegar a otras secciones críticas.
 * 
 * Características técnicas implementadas
 * 
 * - Material Design 3: Implementación de componentes modernos con CardView y ShapeableImageView
 * - Firebase Authentication: Integración para gestión de sesiones de usuario
 * - Firebase Realtime Database: Almacenamiento y recuperación de información de perfil
 * - Procesamiento de imágenes: Transformación y visualización de fotos de perfil desde Base64
 * 
 * Estructura de datos en Firebase
 * ```
 * usuarios/{userId}/
 * │
 * ├─ nombre: String
 * ├─ apellidos: String
 * ├─ email: String
 * ├─ fechaNacimiento: String (formato DD/MM/YYYY)
 * ├─ isPerro: Boolean
 * └─ imagenBase64: String (opcional)
 * ```
 * 
 * Modos de operación
 * 
 * 1. Modo usuario normal: Visualización/edición de datos del usuario autenticado
 * 2. Modo desarrollador: Visualización de perfil de desarrollador en modo de solo lectura
 * 
 * @property database Referencia a la base de datos de Firebase para recuperar datos de usuario
 * @property auth Instancia de Firebase Auth para gestionar la sesión actual
 * @property userId Identificador único del usuario autenticado
 * @property desarrollador Boolean que indica si se está visualizando un perfil de desarrollador
 * @property perfilImageView ImageView donde se muestra la foto de perfil del usuario
 * @property nombreTextView TextView donde se muestra el nombre completo del usuario
 * @property emailTextView TextView donde se muestra el correo electrónico del usuario
 * @property edadTextView TextView donde se muestra la edad calculada del usuario
 */
@Suppress("DEPRECATION")
class PerfilUsuario : AppCompatActivity() {

    private lateinit var ivFoto: ImageView
    private lateinit var tvNombreUsuarioGrande: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvNombre: TextView
    private lateinit var tvApellidos: TextView
    private lateinit var tvFechaNacimiento: TextView
    private lateinit var tvEdad: TextView
    private lateinit var tvEsPerro: TextView
    private lateinit var btnEditar: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnCerrarSesion: ImageButton

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var usuarioId: String? = null
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    
    // Flag para indicar si venimos de Ajustes
    private var fromSettings = false
    
    // Datos del desarrollador (si corresponde)
    private var developerData: Map<String, String>? = null

    /**
     * Inicializa la actividad y carga los datos del perfil del usuario.
     *
     * @param savedInstanceState Estado guardado de la actividad
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.perfil_usuario)

        ivFoto = findViewById(R.id.ivFoto)
        tvNombreUsuarioGrande = findViewById(R.id.tvNombreUsuarioGrande)
        tvEmail = findViewById(R.id.tvEmail)
        tvNombre = findViewById(R.id.tvNombreValor)
        tvApellidos = findViewById(R.id.tvApellidosValor)
        tvFechaNacimiento = findViewById(R.id.tvFechaNacimiento)
        tvEdad = findViewById(R.id.tvEdad)
        tvEsPerro = findViewById(R.id.tvEsPerroValor)
        btnEditar = findViewById(R.id.btnEditar)
        btnBack = findViewById(R.id.btnBack)
        btnCerrarSesion = findViewById(R.id.btnCerrarSesion)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        usuarioId = auth.currentUser?.uid
        
        // Comprobar si estamos visualizando un perfil de desarrollador
        fromSettings = intent.getBooleanExtra("FROM_SETTINGS", false)
        
        if (fromSettings) {
            // Estamos mostrando el perfil de un desarrollador
            cargarPerfilDesarrollador()
            btnEditar.visibility = android.view.View.GONE // Ocultar botón de editar
        } else if (usuarioId != null) {
            // Estamos mostrando el perfil del usuario normal
            cargarPerfil()
            cargarImagenDesdeFirebase()
        } else {
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show()
        }

        configurarBotones()
    }

    /**
     * Configura los listeners de los botones.
     *
     * Configura:
     * - Botón de retroceso: vuelve al mapa o a ajustes según el origen
     * - Botón de cerrar sesión: cierra la sesión y vuelve al login
     * - Botón de edición: abre la actividad de edición del perfil
     */
    private fun configurarBotones() {
        btnBack.setOnClickListener {
            if (fromSettings) {
                // Si venimos de ajustes, volvemos a la actividad de ajustes
                val intent = Intent(this, AjustesActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                // Comportamiento normal: volver a MapsActivity
                val intent = Intent(this, MapsActivity::class.java)
                startActivity(intent)
                finish()
            }
        }

        btnCerrarSesion.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnEditar.setOnClickListener {
            val intent = Intent(this@PerfilUsuario, EditarUsuario::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_up, R.anim.slide_down)
        }
    }
    
    /**
     * Carga y muestra los datos del perfil de un desarrollador.
     */
    private fun cargarPerfilDesarrollador() {
        // Obtener datos del intent
        val nombre = intent.getStringExtra("DEVELOPER_NAME") ?: "Desconocido"
        val apellidos = intent.getStringExtra("DEVELOPER_LASTNAME") ?: "Desconocido"
        val email = intent.getStringExtra("DEVELOPER_EMAIL") ?: "Desconocido"
        val fechaNacimiento = intent.getStringExtra("DEVELOPER_BIRTHDAY") ?: ""
        val isPerro = intent.getBooleanExtra("DEVELOPER_IS_DOG", false)
        val userId = intent.getStringExtra("DEVELOPER_USER_ID")
        
        // Mostrar los datos en la interfaz
        tvNombre.text = nombre
        tvApellidos.text = apellidos
        tvNombreUsuarioGrande.text = "$nombre $apellidos"
        tvEmail.text = email
        
        if (fechaNacimiento.isNotEmpty()) {
            tvFechaNacimiento.text = fechaNacimiento
            try {
                val fecha = dateFormat.parse(fechaNacimiento)
                if (fecha != null) {
                    val edad = calcularEdad(fecha)
                    tvEdad.text = "$edad años"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                tvEdad.text = "N/A"
            }
        } else {
            tvFechaNacimiento.text = "No especificada"
            tvEdad.text = "N/A"
        }
        
        tvEsPerro.text = if (isPerro) "Sí" else "No"
        
        // Cargar imagen desde Firebase
        if (!userId.isNullOrEmpty()) {
            cargarImagenDesarrolladorDesdeFirebase(userId)
        } else {
            mostrarImagenPorDefecto()
        }
    }

    /**
     * Carga la imagen de perfil de un desarrollador desde Firebase.
     *
     * @param userId ID del usuario en Firebase
     */
    private fun cargarImagenDesarrolladorDesdeFirebase(userId: String) {
        database.child("users").child(userId).child("imagenBase64")
            .get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val imageBase64 = snapshot.getValue(String::class.java)
                    ivFoto.loadBase64Image(imageBase64, true)
                } else {
                    mostrarImagenPorDefecto()
                }
            }.addOnFailureListener {
                Log.e("PerfilUsuario", "Error al cargar imagen del desarrollador", it)
                mostrarImagenPorDefecto()
            }
    }

    /**
     * Carga el perfil del usuario desde Firebase.
     *
     * Obtiene y muestra:
     * - Datos personales
     * - Edad calculada
     * - Estado (perro/humano)
     * - Imagen de perfil
     */
    private fun cargarPerfil() {
        usuarioId?.let { id ->
            Log.d("PerfilUsuario", "Cargando datos para usuario ID: $id")

            val email = auth.currentUser?.email
            tvEmail.text = email ?: "Desconocido"

            database.child("users").child(id).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val nombre = snapshot.child("nombre").getValue(String::class.java) ?: "Desconocido"
                        val apellidos = snapshot.child("apellidos").getValue(String::class.java) ?: "Desconocido"

                        tvNombre.text = nombre
                        tvApellidos.text = apellidos
                        tvNombreUsuarioGrande.text = buildString {
                            append(nombre)
                            append(" ")
                            append(apellidos)
                        }

                        val fechaNacimiento = snapshot.child("fechaNacimiento").getValue(String::class.java)
                        if (!fechaNacimiento.isNullOrEmpty()) {
                            tvFechaNacimiento.text = fechaNacimiento
                            try {
                                val fecha = dateFormat.parse(fechaNacimiento)
                                if (fecha != null) {
                                    val edad = calcularEdad(fecha)
                                    tvEdad.text = buildString {
                                        append(edad)
                                        append(" años")
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                tvEdad.text = "N/A"
                            }
                        } else {
                                tvFechaNacimiento.text = buildString {
            append("No especificada")
        }
                            tvEdad.text = "N/A"
                        }

                        val esPerro = snapshot.child("isPerro").getValue(Boolean::class.java) == true
                        tvEsPerro.text = if (esPerro) "Sí" else "No"
                    } else {
                        Log.e("PerfilUsuario", "No se encontraron datos del usuario en Firebase.")
                        Toast.makeText(this@PerfilUsuario, "No se encontraron datos del usuario", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("PerfilUsuario", "Error al cargar datos: ${error.message}")
                    Toast.makeText(this@PerfilUsuario, "Error al cargar datos", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    /**
     * Calcula la edad del usuario en años.
     *
     * @param fechaNacimiento Fecha de nacimiento del usuario
     * @return Edad en años
     */
    private fun calcularEdad(fechaNacimiento: Date): Int {
        val hoy = Calendar.getInstance()
        val nacimiento = Calendar.getInstance()
        nacimiento.time = fechaNacimiento

        var edad = hoy.get(Calendar.YEAR) - nacimiento.get(Calendar.YEAR)

        if (hoy.get(Calendar.DAY_OF_YEAR) < nacimiento.get(Calendar.DAY_OF_YEAR)) {
            edad--
        }

        return edad
    }

    /**
     * Carga la imagen de perfil del usuario desde Firebase.
     *
     * Obtiene la imagen en Base64, la decodifica y la muestra.
     * Si no hay imagen o hay error, muestra una imagen por defecto.
     */
    private fun cargarImagenDesdeFirebase() {
        usuarioId?.let { id ->
            database.child("users").child(id).child("imagenBase64")
                .get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val imageBase64 = snapshot.getValue(String::class.java)
                        ivFoto.loadBase64Image(imageBase64, true)
                    } else {
                        mostrarImagenPorDefecto()
                    }
                }.addOnFailureListener {
                    Toast.makeText(this, "Error al cargar imagen", Toast.LENGTH_SHORT).show()
                    mostrarImagenPorDefecto()
                }
        }
    }

    /**
     * Muestra la imagen por defecto en el ImageView.
     *
     * Se utiliza cuando:
     * - No hay imagen en Firebase
     * - Hay error al cargar la imagen
     * - La imagen está corrupta
     */
    private fun mostrarImagenPorDefecto() {
        ivFoto.loadBase64Image(null, true)
    }
}