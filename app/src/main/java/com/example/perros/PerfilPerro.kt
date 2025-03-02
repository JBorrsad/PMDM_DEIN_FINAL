package com.example.perros

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityOptionsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Actividad que muestra el perfil detallado de un perro.
 *
 * Esta actividad muestra información completa sobre un perro, incluyendo:
 * - Datos básicos (nombre, raza, peso)
 * - Información temporal (edad, fechas de nacimiento y adopción)
 * - Información del dueño
 * - Imágenes del perro y del dueño
 *
 * Estructura de datos en Firebase:
 * ```
 * users/
 *   └── {perroId}/
 *         ├── nombre: String
 *         ├── raza: String
 *         ├── peso: Double
 *         ├── fechaNacimiento: Long
 *         ├── fechaAdopcion: Long
 *         ├── dueñoId: String
 *         └── imagenBase64: String?
 * ```
 *
 * @property database Referencia a Firebase Realtime Database
 * @property auth Instancia de Firebase Authentication
 * @property perroId Identificador único del perro a mostrar
 */
class PerfilPerro : AppCompatActivity() {

    private lateinit var btnEditar: Button
    private lateinit var btnBack: ImageButton
    private lateinit var ivFoto: ShapeableImageView
    private lateinit var tvNombreMascota: TextView
    private lateinit var tvTipoRaza: TextView
    private lateinit var tvNombre: TextView
    private lateinit var tvRaza: TextView
    private lateinit var tvPeso: TextView
    private lateinit var tvEdad: TextView
    private lateinit var tvFechaNacimiento: TextView
    private lateinit var tvFechaAdopcion: TextView
    private lateinit var ivImagenDueno: ShapeableImageView
    private lateinit var tvNombreDueno: TextView
    private lateinit var tvLoginDueno: TextView

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var perroId: String? = null
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    /**
     * Inicializa la actividad y carga los datos del perro.
     *
     * @param savedInstanceState Estado guardado de la actividad
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.perfil_perro)

        inicializarVistas()
        inicializarFirebase()
        cargarDatosPerro()
        configurarBotones()
    }

    /**
     * Inicializa todas las vistas de la interfaz.
     *
     * Encuentra y asigna las referencias a:
     * - Botones de acción
     * - Campos de texto informativos
     * - Imágenes del perro y dueño
     * Configura la imagen por defecto si es necesario
     */
    private fun inicializarVistas() {
        btnEditar = findViewById(R.id.btnEditar)
        btnBack = findViewById(R.id.btnBack)
        ivFoto = findViewById(R.id.ivFoto)
        tvNombreMascota = findViewById(R.id.tvNombreMascota)
        tvTipoRaza = findViewById(R.id.tvTipoRaza)
        tvNombre = findViewById(R.id.tvNombre)
        tvRaza = findViewById(R.id.tvRaza)
        tvPeso = findViewById(R.id.tvPeso)
        tvEdad = findViewById(R.id.tvEdad)
        tvFechaNacimiento = findViewById(R.id.tvFechaNacimiento)
        tvFechaAdopcion = findViewById(R.id.tvFechaAdopcion)
        ivImagenDueno = findViewById(R.id.ivImagenDueno)
        tvNombreDueno = findViewById(R.id.tvNombreDueno)
        tvLoginDueno = findViewById(R.id.tvLoginDueno)

        if (ivFoto.drawable == null) {
            ivFoto.loadBase64Image(null)
        }
    }

    /**
     * Inicializa las conexiones con Firebase.
     *
     * Configura:
     * - Firebase Authentication
     * - Realtime Database
     * - ID del perro desde los extras del Intent
     */
    private fun inicializarFirebase() {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        perroId = intent.getStringExtra("perroId")
    }

    /**
     * Carga los datos del perro desde Firebase y actualiza la interfaz.
     *
     * Obtiene y muestra:
     * - Información básica del perro
     * - Imagen del perro
     * - Datos del dueño
     */
    private fun cargarDatosPerro() {
        if (perroId == null) {
            Toast.makeText(this, "Error al cargar el perro", Toast.LENGTH_SHORT).show()
            return
        }

        database.child("users").child(perroId!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val nombre = snapshot.child("nombre").getValue(String::class.java) ?: ""
                    val raza = snapshot.child("raza").getValue(String::class.java) ?: ""
                    val peso = snapshot.child("peso").getValue(Double::class.java) ?: 0.0
                    val fechaNacimiento = snapshot.child("fechaNacimiento").getValue(Long::class.java) ?: 0L
                    val fechaAdopcion = snapshot.child("fechaAdopcion").getValue(Long::class.java) ?: 0L
                    val duenoId = snapshot.child("dueñoId").getValue(String::class.java)
                    val imagenBase64 = snapshot.child("imagenBase64").getValue(String::class.java)

                    actualizarUI(nombre, raza, peso, fechaNacimiento, fechaAdopcion)
                    cargarImagenPerro(imagenBase64)
                    if (duenoId != null) {
                        cargarDatosDueno(duenoId)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@PerfilPerro, "Error al cargar datos", Toast.LENGTH_SHORT).show()
                }
            })
    }

    /**
     * Actualiza la interfaz con los datos del perro.
     *
     * @param nombre Nombre del perro
     * @param raza Raza del perro
     * @param peso Peso en kilogramos
     * @param fechaNacimiento Timestamp de nacimiento
     * @param fechaAdopcion Timestamp de adopción
     */
    private fun actualizarUI(nombre: String, raza: String, peso: Double, fechaNacimiento: Long, fechaAdopcion: Long) {
        tvNombreMascota.text = nombre
        tvTipoRaza.text = raza
        tvNombre.text = nombre
        tvRaza.text = raza
        tvPeso.text = String.format(Locale.getDefault(), "%.1f kg", peso)

        // Calcular edad
        val edad = calcularEdad(fechaNacimiento)
        tvEdad.text = "$edad años"

        // Formatear fechas
        val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        tvFechaNacimiento.text = formatoFecha.format(Date(fechaNacimiento))
        tvFechaAdopcion.text = formatoFecha.format(Date(fechaAdopcion))
    }

    /**
     * Calcula la edad del perro en años.
     *
     * @param fechaNacimiento Timestamp de la fecha de nacimiento
     * @return Edad en años
     */
    private fun calcularEdad(fechaNacimiento: Long): Int {
        val hoy = Calendar.getInstance()
        val nacimiento = Calendar.getInstance()
        nacimiento.timeInMillis = fechaNacimiento

        var edad = hoy.get(Calendar.YEAR) - nacimiento.get(Calendar.YEAR)
        if (hoy.get(Calendar.DAY_OF_YEAR) < nacimiento.get(Calendar.DAY_OF_YEAR)) {
            edad--
        }
        return edad
    }

    /**
     * Carga y muestra la imagen del perro.
     *
     * Utiliza Coil para cargar la imagen Base64 y mostrar un spinner durante la carga.
     *
     * @param imagenBase64 Imagen codificada en Base64
     */
    private fun cargarImagenPerro(imagenBase64: String?) {
        ivFoto.loadBase64Image(imagenBase64)
    }

    /**
     * Carga los datos del dueño desde Firebase.
     *
     * Obtiene y muestra:
     * - Nombre completo
     * - Email
     * - Imagen de perfil
     *
     * @param duenoId ID del dueño en Firebase
     */
    private fun cargarDatosDueno(duenoId: String) {
        database.child("users").child(duenoId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val nombre = snapshot.child("nombre").getValue(String::class.java) ?: ""
                    val apellidos = snapshot.child("apellidos").getValue(String::class.java) ?: ""
                    val email = snapshot.child("email").getValue(String::class.java) ?: ""
                    val imagenBase64 = snapshot.child("imagenBase64").getValue(String::class.java)

                    tvNombreDueno.text = "$nombre $apellidos"
                    tvLoginDueno.text = email
                    cargarImagenDueno(imagenBase64)
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@PerfilPerro, "Error al cargar datos del dueño", Toast.LENGTH_SHORT).show()
                }
            })
    }

    /**
     * Carga y muestra la imagen del dueño.
     *
     * Utiliza Coil para cargar la imagen Base64 y mostrar un spinner durante la carga.
     *
     * @param imagenBase64 Imagen codificada en Base64
     */
    private fun cargarImagenDueno(imagenBase64: String?) {
        ivImagenDueno.loadBase64Image(imagenBase64)
    }

    /**
     * Configura los listeners de los botones.
     *
     * Configura:
     * - Botón de retroceso: vuelve a la actividad anterior
     * - Botón de edición: abre la actividad de edición del perro
     */
    private fun configurarBotones() {
        btnBack.setOnClickListener {
            finish()
        }

        btnEditar.setOnClickListener {
            val intent = Intent(this, EditarPerro::class.java)
            intent.putExtra("perroId", perroId)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.slide_up,
                R.anim.slide_down
            )
            startActivity(intent, options.toBundle())
        }
    }
}