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
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.yalantis.ucrop.UCrop
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import coil.load
import com.example.perros.bitmapToBase64OriginalQuality

/**
 * Actividad para editar el perfil de usuario.
 *
 * Esta actividad permite:
 * - Modificar datos personales (nombre, apellidos)
 * - Actualizar fecha de nacimiento
 * - Cambiar imagen de perfil
 * - Configurar tipo de usuario (perro/humano)
 * - Asignar dueño si es un perro
 *
 * Estructura de datos en Firebase:
 * ```
 * users/
 *   └── {userId}/
 *         ├── nombre: String
 *         ├── apellidos: String
 *         ├── email: String
 *         ├── fechaNacimiento: String
 *         ├── isPerro: Boolean
 *         ├── dueñoId: String?
 *         └── imagenBase64: String?
 * ```
 *
 * @property database Referencia a Firebase Realtime Database
 * @property auth Instancia de Firebase Authentication
 * @property usuarioId ID del usuario actual
 *
 * @see PerfilUsuario actividad que muestra el perfil
 * @see UCrop biblioteca para recortar imágenes
 */
class EditarUsuario : AppCompatActivity() {

    private lateinit var btnGuardar: Button
    private lateinit var btnBack: ImageButton
    private lateinit var ivImagen: ImageView
    private lateinit var tvNombreUsuarioGrande: TextView
    private lateinit var etEmail: EditText
    private lateinit var etNombre: EditText
    private lateinit var etApellidos: EditText
    private lateinit var etFechaNacimiento: EditText
    private lateinit var etEdad: EditText
    private lateinit var switchEsPerro: SwitchMaterial
    private lateinit var tvDueño: TextView
    private lateinit var spinnerDueño: Spinner
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var usuarioId: String? = null
    private var dueñoSeleccionadoId: String? = null
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var selectedDate: Date? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.editar_usuario)

        inicializarVistas()
        inicializarFirebase()
        configurarSwitch()
        configurarFechaNacimiento()
        cargarDatosUsuario()
        cargarImagenDesdeFirebase()
        configurarBotones()
    }

    private fun inicializarVistas() {
        ivImagen = findViewById(R.id.ivImagen)
        tvNombreUsuarioGrande = findViewById(R.id.tvNombreUsuarioGrande)
        etEmail = findViewById(R.id.etEmail)
        etNombre = findViewById(R.id.etNombre)
        etApellidos = findViewById(R.id.etApellidos)
        etFechaNacimiento = findViewById(R.id.etFechaNacimiento)
        etEdad = findViewById(R.id.etEdad)
        btnGuardar = findViewById(R.id.btnGuardarUsuario)
        btnBack = findViewById(R.id.btnBack)
        switchEsPerro = findViewById(R.id.switchEsPerro)
        tvDueño = findViewById(R.id.tvDueño)
        spinnerDueño = findViewById(R.id.spinnerDueño)

        if (ivImagen.drawable == null) {
            ivImagen.loadBase64Image(null)
        }

        etEdad.isEnabled = false
    }

    private fun inicializarFirebase() {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        usuarioId = auth.currentUser?.uid

        etEmail.setText(auth.currentUser?.email)
    }

    private fun configurarFechaNacimiento() {
        etFechaNacimiento.setOnClickListener {
            mostrarDateSpinnerDialog()
        }
    }

    private fun mostrarDateSpinnerDialog() {
        val calendar = Calendar.getInstance()
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

        // Listeners para actualizar días cuando cambia mes o año
        yearPicker.setOnValueChangedListener { _, _, newVal ->
            updateDayPicker(dayPicker, newVal, monthPicker.value)
        }

        monthPicker.setOnValueChangedListener { _, _, newVal ->
            updateDayPicker(dayPicker, yearPicker.value, newVal)
        }

        // Botones
        dialog.findViewById<Button>(R.id.btnAceptar).setOnClickListener {
            calendar.set(yearPicker.value, monthPicker.value, dayPicker.value)
            selectedDate = calendar.time
            etFechaNacimiento.setText(dateFormat.format(selectedDate!!))
            actualizarEdad(selectedDate!!)
            dialog.dismiss()
        }

        dialog.findViewById<Button>(R.id.btnCancelar).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun actualizarEdad(fechaNacimiento: Date) {
        val edad = calcularEdad(fechaNacimiento)
        etEdad.setText("$edad años")
    }

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

    private fun updateDayPicker(dayPicker: NumberPicker, year: Int, month: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, 1)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        dayPicker.minValue = 1
        dayPicker.maxValue = daysInMonth
    }

    private fun configurarSwitch() {
        switchEsPerro.setOnCheckedChangeListener { _, isChecked ->
            tvDueño.visibility = if (isChecked) View.VISIBLE else View.GONE
            spinnerDueño.visibility = if (isChecked) View.VISIBLE else View.GONE

            if (isChecked) {
                cargarListaDueños()
            }
        }
    }

    private fun cargarListaDueños() {
        database.child("users")
            .orderByChild("isPerro")
            .equalTo(false)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val dueños = mutableListOf<Pair<String, String>>()

                    for (userSnapshot in snapshot.children) {
                        val nombre = userSnapshot.child("nombre").getValue(String::class.java) ?: ""
                        val apellidos = userSnapshot.child("apellidos").getValue(String::class.java) ?: ""
                        val nombreCompleto = "$nombre $apellidos"
                        dueños.add(Pair(userSnapshot.key!!, nombreCompleto))
                    }

                    val adapter = ArrayAdapter(
                        this@EditarUsuario,
                        android.R.layout.simple_spinner_item,
                        dueños.map { it.second }
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerDueño.adapter = adapter

                    spinnerDueño.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            dueñoSeleccionadoId = dueños[position].first
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) {
                            dueñoSeleccionadoId = null
                        }
                    }

                    // Si hay un dueño seleccionado previamente, seleccionarlo
                    if (dueñoSeleccionadoId != null) {
                        val index = dueños.indexOfFirst { it.first == dueñoSeleccionadoId }
                        if (index != -1) {
                            spinnerDueño.setSelection(index)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        this@EditarUsuario,
                        "Error al cargar la lista de dueños",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun cargarDatosUsuario() {
        usuarioId?.let { id ->
            database.child("users").child(id)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val nombre = snapshot.child("nombre").getValue(String::class.java) ?: ""
                        val apellidos = snapshot.child("apellidos").getValue(String::class.java) ?: ""

                        etNombre.setText(nombre)
                        etApellidos.setText(apellidos)
                        tvNombreUsuarioGrande.text = "$nombre $apellidos"

                        val fechaNacimiento = snapshot.child("fechaNacimiento").getValue(String::class.java)
                        if (!fechaNacimiento.isNullOrEmpty()) {
                            etFechaNacimiento.setText(fechaNacimiento)
                            try {
                                selectedDate = dateFormat.parse(fechaNacimiento)
                                selectedDate?.let { actualizarEdad(it) }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        val isPerro = snapshot.child("isPerro").getValue(Boolean::class.java) == true
                        switchEsPerro.isChecked = isPerro

                        if (isPerro) {
                            tvDueño.visibility = View.VISIBLE
                            spinnerDueño.visibility = View.VISIBLE

                            val dueñoId = snapshot.child("dueñoId").getValue(String::class.java)
                            if (dueñoId != null) {
                                dueñoSeleccionadoId = dueñoId
                                cargarListaDueños()
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(this@EditarUsuario, "Error al cargar datos", Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }

    private fun configurarBotones() {
        val btnAdjuntarImagen = findViewById<FloatingActionButton>(R.id.btnAdjuntarImagen)
        btnAdjuntarImagen.setOnClickListener {
            abrirGaleriaOCamara()
        }

        btnGuardar.setOnClickListener {
            guardarCambios()
        }

        btnBack.setOnClickListener {
            redirigirAPerfil()
        }

        etNombre.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                actualizarNombreGrande()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        etApellidos.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                actualizarNombreGrande()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun actualizarNombreGrande() {
        val nombre = etNombre.text.toString()
        val apellidos = etApellidos.text.toString()
        tvNombreUsuarioGrande.text = "$nombre $apellidos"
    }

    private fun guardarCambios() {
        usuarioId?.let { id ->
            val updates = mutableMapOf<String, Any>()

            updates["nombre"] = etNombre.text.toString()
            updates["apellidos"] = etApellidos.text.toString()

            selectedDate?.let {
                updates["fechaNacimiento"] = dateFormat.format(it)
            }

            updates["isPerro"] = switchEsPerro.isChecked
            if (switchEsPerro.isChecked && dueñoSeleccionadoId != null) {
                updates["dueñoId"] = dueñoSeleccionadoId!!
            }

            database.child("users").child(id).updateChildren(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Datos actualizados", Toast.LENGTH_SHORT).show()
                    redirigirAPerfil()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al actualizar", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun abrirGaleriaOCamara() {
        val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        val chooser = Intent.createChooser(pickIntent, "Selecciona una imagen")
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takePictureIntent))

        imagePickerLauncher.launch(chooser)
    }

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

    private fun iniciarUCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(cacheDir, "cropped_image.jpg"))

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
                
                // Cargar la imagen recortada usando el método de extensión que muestra el spinner
                ivImagen.loadBase64Image(bitmapToBase64OriginalQuality(bitmap))
                
                // Guardar la imagen en Firebase
                guardarImagenRecortadaEnFirebase(bitmap)
            } catch (e: Exception) {
                Toast.makeText(this, "Error al recortar imagen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == UCrop.REQUEST_CROP && resultCode == UCrop.RESULT_ERROR) {
            val cropError = UCrop.getError(data!!)
            Toast.makeText(
                this,
                "Error al recortar la imagen: ${cropError?.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun guardarImagenRecortadaEnFirebase(bitmap: Bitmap) {
        usuarioId?.let { id ->
            // Mantener la calidad original de la imagen
            val imageBase64 = bitmapToBase64OriginalQuality(bitmap)
            
            database.child("users").child(id).child("imagenBase64").setValue(imageBase64)
                .addOnSuccessListener {
                    Toast.makeText(this, "Imagen guardada correctamente", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al guardar la imagen", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun cargarImagenDesdeFirebase() {
        usuarioId?.let { id ->
            database.child("users").child(id).child("imagenBase64")
                .get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val imageBase64 = snapshot.getValue(String::class.java)
                        ivImagen.loadBase64Image(imageBase64)
                    } else {
                        ivImagen.loadBase64Image(null)
                    }
                }.addOnFailureListener {
                    Toast.makeText(this, "Error al cargar imagen", Toast.LENGTH_SHORT).show()
                    ivImagen.loadBase64Image(null)
                }
        }
    }

    private fun redirigirAPerfil() {
        val intent = Intent(this, PerfilUsuario::class.java)
        startActivity(intent)
        finish()
    }
}