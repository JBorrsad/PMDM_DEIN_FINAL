package com.example.perros


import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.yalantis.ucrop.UCrop
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import coil.load

/**
 * Actividad para editar los datos de un perro existente.
 *
 * Esta actividad permite:
 * - Modificar información básica del perro (nombre, raza, peso)
 * - Actualizar fechas (nacimiento y adopción)
 * - Cambiar la imagen del perro
 * - Asignar o cambiar el dueño
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
 * @property perroId ID del perro a editar
 *
 * @see PerfilPerro actividad que muestra el perfil del perro
 * @see UCrop biblioteca para recortar imágenes
 */
class EditarPerro : AppCompatActivity() {

    private lateinit var btnGuardar: Button
    private lateinit var btnBack: ImageButton
    private lateinit var ivImagen: ShapeableImageView
    private lateinit var tvNombreMascota: EditText
    private lateinit var tvTipoRaza: EditText
    private lateinit var tvPeso: EditText
    private lateinit var tvEdad: TextView
    private lateinit var tvFechaNacimiento: EditText
    private lateinit var tvFechaAdopcion: EditText
    private lateinit var spinnerDueno: Spinner
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var perroId: String? = null
    private var duenoSeleccionadoId: String? = null
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var selectedBirthDate: Date? = null
    private var selectedAdoptionDate: Date? = null
    private var imagenRecortada: Bitmap? = null

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val selectedImageUri: Uri? = result.data!!.data
                if (selectedImageUri != null) {
                    iniciarUCrop(selectedImageUri)
                } else {
                    Toast.makeText(this, "Error al seleccionar imagen", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.editar_perro)

        inicializarVistas()
        inicializarFirebase()
        configurarFechas()
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
        spinnerDueno = findViewById(R.id.spinnerDueno)

        // Configurar los EditText de fechas como no focusables pero clickeables
        tvFechaNacimiento.isFocusable = false
        tvFechaNacimiento.isClickable = true
        tvFechaAdopcion.isFocusable = false
        tvFechaAdopcion.isClickable = true
    }

    private fun inicializarFirebase() {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        perroId = intent.getStringExtra("perroId")
    }

    private fun cargarListaDuenos() {
        database.child("users")
            .orderByChild("isPerro")
            .equalTo(false)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val duenos = mutableListOf<Pair<String, String>>()

                    for (userSnapshot in snapshot.children) {
                        val nombre = userSnapshot.child("nombre").getValue(String::class.java) ?: ""
                        val apellidos = userSnapshot.child("apellidos").getValue(String::class.java) ?: ""
                        val nombreCompleto = "$nombre $apellidos"
                        duenos.add(Pair(userSnapshot.key!!, nombreCompleto))
                    }

                    val adapter = ArrayAdapter(
                        this@EditarPerro,
                        android.R.layout.simple_spinner_item,
                        duenos.map { it.second }
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerDueno.adapter = adapter

                    spinnerDueno.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            duenoSeleccionadoId = duenos[position].first
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) {
                            duenoSeleccionadoId = null
                        }
                    }

                    // Seleccionar el dueño actual si existe
                    if (duenoSeleccionadoId != null) {
                        val index = duenos.indexOfFirst { it.first == duenoSeleccionadoId }
                        if (index != -1) {
                            spinnerDueno.setSelection(index)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@EditarPerro, "Error al cargar la lista de dueños", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun configurarFechas() {
        tvFechaNacimiento.setOnClickListener {
            mostrarDateSpinnerDialog(true)
        }

        tvFechaAdopcion.setOnClickListener {
            mostrarDateSpinnerDialog(false)
        }
    }

    private fun mostrarDateSpinnerDialog(isNacimiento: Boolean) {
        val calendar = Calendar.getInstance()
        val selectedDate = if (isNacimiento) selectedBirthDate else selectedAdoptionDate
        selectedDate?.let { date ->
            calendar.time = date
        }

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.date_spinner_dialog)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val yearPicker = dialog.findViewById<NumberPicker>(R.id.yearPicker)
        val monthPicker = dialog.findViewById<NumberPicker>(R.id.monthPicker)
        val dayPicker = dialog.findViewById<NumberPicker>(R.id.dayPicker)

        // Configurar Year Picker
        val currentYear = calendar.get(Calendar.YEAR)
        yearPicker.minValue = 1900
        yearPicker.maxValue = currentYear
        yearPicker.value = calendar.get(Calendar.YEAR)

        // Configurar Month Picker
        val months = arrayOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
        monthPicker.minValue = 0
        monthPicker.maxValue = 11
        monthPicker.displayedValues = months
        monthPicker.value = calendar.get(Calendar.MONTH)

        // Configurar Day Picker
        updateDayPicker(dayPicker, yearPicker.value, monthPicker.value)
        dayPicker.value = calendar.get(Calendar.DAY_OF_MONTH)

        // Actualizar días al cambiar año o mes
        yearPicker.setOnValueChangedListener { _, _, newVal ->
            updateDayPicker(dayPicker, newVal, monthPicker.value)
        }
        monthPicker.setOnValueChangedListener { _, _, newVal ->
            updateDayPicker(dayPicker, yearPicker.value, newVal)
        }

        // Configurar botones del diálogo
        dialog.findViewById<Button>(R.id.btnAceptar).setOnClickListener {
            calendar.set(yearPicker.value, monthPicker.value, dayPicker.value)
            if (isNacimiento) {
                selectedBirthDate = calendar.time
                tvFechaNacimiento.setText(dateFormat.format(selectedBirthDate!!))
                actualizarEdad(selectedBirthDate!!.time)
            } else {
                selectedAdoptionDate = calendar.time
                tvFechaAdopcion.setText(dateFormat.format(selectedAdoptionDate!!))
            }
            dialog.dismiss()
        }
        dialog.findViewById<Button>(R.id.btnCancelar).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun updateDayPicker(dayPicker: NumberPicker, year: Int, month: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, 1)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        dayPicker.minValue = 1
        dayPicker.maxValue = daysInMonth
    }

    private fun actualizarEdad(fechaNacimientoMillis: Long) {
        val edad = calcularEdad(fechaNacimientoMillis)
        tvEdad.text = "$edad años"
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
                        duenoSeleccionadoId = duenoId
                    }
                    cargarListaDuenos()
                }
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@EditarPerro, "Error al cargar datos", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun actualizarUI(nombre: String, raza: String, peso: Double, fechaNacimiento: Long, fechaAdopcion: Long) {
        tvNombreMascota.setText(nombre)
        tvTipoRaza.setText(raza)
        tvPeso.setText(String.format("%.1f", peso))

        // Calcular edad
        val edad = calcularEdad(fechaNacimiento)
        tvEdad.text = "$edad años"

        // Formatear fechas
        selectedBirthDate = Date(fechaNacimiento)
        selectedAdoptionDate = Date(fechaAdopcion)
        tvFechaNacimiento.setText(dateFormat.format(selectedBirthDate!!))
        tvFechaAdopcion.setText(dateFormat.format(selectedAdoptionDate!!))
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
        ivImagen.loadBase64Image(imagenBase64)
    }

    private fun configurarBotones() {
        btnBack.setOnClickListener {
            volverAPerfilPerro()
        }
        val btnAdjuntarImagen = findViewById<FloatingActionButton>(R.id.btnAdjuntarImagen)
        btnAdjuntarImagen.setOnClickListener {
            val pickIntent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            val takePictureIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            val chooser = Intent.createChooser(pickIntent, "Selecciona una imagen")
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takePictureIntent))
            imagePickerLauncher.launch(chooser)
        }
        btnGuardar.setOnClickListener {
            guardarCambios()
        }
    }

    private fun volverAPerfilPerro() {
        finish()
        // Aplicar la animación de deslizamiento de izquierda a derecha
        overridePendingTransition(R.anim.slide_right_to_left, R.anim.slide_left_to_right)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // Aplicar la animación de deslizamiento de izquierda a derecha
        overridePendingTransition(R.anim.slide_right_to_left, R.anim.slide_left_to_right)
    }

    private fun guardarCambios() {
        perroId?.let { id ->
            val updates = mutableMapOf<String, Any>()
            // Obtener los valores de los EditText
            val nombre = tvNombreMascota.text.toString().trim()
            val raza = tvTipoRaza.text.toString().trim()
            val pesoText = tvPeso.text.toString().trim()
            // Validar que los campos no estén vacíos
            if (nombre.isEmpty() || raza.isEmpty() || pesoText.isEmpty()) {
                Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
                return@let
            }
            // Convertir el peso a Double
            val peso = try {
                pesoText.toDouble()
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "El peso debe ser un número válido", Toast.LENGTH_SHORT).show()
                return@let
            }
            // Actualizar el mapa con los nuevos valores
            updates["nombre"] = nombre
            updates["raza"] = raza
            updates["peso"] = peso
            // Actualizar fechas si están seleccionadas
            selectedBirthDate?.let {
                updates["fechaNacimiento"] = it.time
            }
            selectedAdoptionDate?.let {
                updates["fechaAdopcion"] = it.time
            }
            // Actualizar dueño si está seleccionado
            duenoSeleccionadoId?.let {
                updates["dueñoId"] = it
            }
            // Actualizar en Firebase
            database.child("users").child(id).updateChildren(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Datos actualizados correctamente", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, PerfilPerro::class.java)
                    intent.putExtra("perroId", perroId)
                    startActivity(intent)
                    finish()
                    // Aplicar la animación de deslizamiento de izquierda a derecha
                    overridePendingTransition(R.anim.slide_right_to_left, R.anim.slide_left_to_right)
                }
                .addOnFailureListener { error ->
                    Toast.makeText(this, "Error al actualizar: ${error.message}", Toast.LENGTH_SHORT).show()
                }
        } ?: run {
            Toast.makeText(this, "Error: ID del perro no encontrado", Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------- Funcionalidad de manejo de imagen --------------------

    private fun iniciarUCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(cacheDir, "cropped_image_dog.jpg"))
        UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(800, 800)
            .start(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK) {
            val resultUri = UCrop.getOutput(data!!)
            try {
                // Convertir Uri a Bitmap
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, resultUri)
                
                // Cargar la imagen recortada usando el método de extensión
                ivImagen.loadBase64Image(bitmapToBase64OriginalQuality(bitmap))
                
                // Guardar la imagen en Firebase
                guardarImagenRecortadaEnFirebase(bitmap)
            } catch (e: Exception) {
                Toast.makeText(this, "Error al recortar imagen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == UCrop.REQUEST_CROP && resultCode == UCrop.RESULT_ERROR) {
            val cropError = UCrop.getError(data!!)
            Toast.makeText(this, "Error al recortar la imagen: ${cropError?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Guarda la imagen recortada en Firebase en formato Base64
     */
    private fun guardarImagenRecortadaEnFirebase(bitmap: Bitmap) {
        perroId?.let { id ->
            // Mantener la calidad original de la imagen
            val imageBase64 = bitmapToBase64OriginalQuality(bitmap)
            
            database.child("users").child(id).child("imagenBase64").setValue(imageBase64)
                .addOnSuccessListener {
                    Toast.makeText(this, "Imagen guardada correctamente", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al guardar la imagen", Toast.LENGTH_SHORT).show()
                }
        } ?: run {
            Toast.makeText(this, "Error: ID del perro no encontrado", Toast.LENGTH_SHORT).show()
        }
    }
}
