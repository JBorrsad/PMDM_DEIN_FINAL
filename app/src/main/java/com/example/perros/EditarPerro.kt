package com.example.perros

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class EditarPerro : AppCompatActivity() {

    private lateinit var btnGuardar: Button
    private lateinit var btnBack: ImageButton
    private lateinit var ivImagen: ShapeableImageView
    private lateinit var tvNombreMascota: TextView
    private lateinit var tvTipoRaza: TextView
    private lateinit var tvPeso: TextView
    private lateinit var tvEdad: TextView
    private lateinit var tvFechaNacimiento: TextView
    private lateinit var tvFechaAdopcion: TextView
    private lateinit var tvNombreDueno: TextView
    private lateinit var tvLoginDueno: TextView
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var perroId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.editar_perro)

        inicializarVistas()
        inicializarFirebase()
        cargarDatosPerro()
        configurarBotones()
    }

    private fun inicializarVistas() {
        btnGuardar = findViewById(R.id.btnGuardarUsuario)
        btnBack = findViewById(R.id.btnBack)
        ivImagen = findViewById(R.id.ivImagen)
        tvNombreMascota = findViewById(R.id.tvNombreMascota)
        tvTipoRaza = findViewById(R.id.tvTipoRaza)
        tvPeso = findViewById(R.id.tvPeso)
        tvEdad = findViewById(R.id.tvEdad)
        tvFechaNacimiento = findViewById(R.id.tvFechaNacimiento)
        tvFechaAdopcion = findViewById(R.id.tvFechaAdopcion)
        tvNombreDueno = findViewById(R.id.tvNombreDueno)
        tvLoginDueno = findViewById(R.id.tvLoginDueno)
    }

    private fun inicializarFirebase() {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        perroId = intent.getStringExtra("perroId")
    }

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
                    Toast.makeText(this@EditarPerro, "Error al cargar datos", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun actualizarUI(nombre: String, raza: String, peso: Double, fechaNacimiento: Long, fechaAdopcion: Long) {
        tvNombreMascota.text = nombre
        tvTipoRaza.text = raza
        tvPeso.text = String.format("%.1f kg", peso)

        // Calcular edad
        val edad = calcularEdad(fechaNacimiento)
        tvEdad.text = "$edad años"

        // Formatear fechas
        val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        tvFechaNacimiento.text = formatoFecha.format(Date(fechaNacimiento))
        tvFechaAdopcion.text = formatoFecha.format(Date(fechaAdopcion))
    }

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

    private fun cargarImagenPerro(imagenBase64: String?) {
        if (!imagenBase64.isNullOrEmpty()) {
            try {
                val imageBytes = Base64.decode(imagenBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ivImagen.setImageBitmap(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
                ivImagen.setImageResource(R.drawable.img) // Imagen por defecto
            }
        }
    }

    private fun cargarDatosDueno(duenoId: String) {
        database.child("users").child(duenoId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val nombre = snapshot.child("nombre").getValue(String::class.java) ?: ""
                    val apellidos = snapshot.child("apellidos").getValue(String::class.java) ?: ""
                    val email = snapshot.child("email").getValue(String::class.java) ?: ""

                    tvNombreDueno.text = "$nombre $apellidos"
                    tvLoginDueno.text = email
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@EditarPerro, "Error al cargar datos del dueño", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun configurarBotones() {
        btnBack.setOnClickListener {
            finish()
        }

        btnGuardar.setOnClickListener {
            guardarCambios()
        }
    }

    private fun guardarCambios() {
        // Aquí implementarías la lógica para guardar los cambios
        // Por ejemplo, podrías abrir un diálogo para editar los campos
        Toast.makeText(this, "Cambios guardados", Toast.LENGTH_SHORT).show()
    }
}
