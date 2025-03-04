package com.example.perros

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * # EditarUsuario
 *
 * Actividad para editar el perfil de usuario en el sistema de monitorización de mascotas.
 *
 * ## Funcionalidad principal
 * Esta clase permite a los usuarios modificar su información personal, incluyendo:
 * - Datos básicos (nombre, apellidos)
 * - Fecha de nacimiento con cálculo automático de edad
 * - Tipo de usuario (persona o perro en simulación)
 * - Selección de dueño en caso de ser un perfil de tipo perro
 * - Procesamiento y actualización de la imagen de perfil
 *
 * ## Características técnicas implementadas:
 * - **Material Design 3**: Utiliza componentes como [MaterialCardView], [SwitchMaterial], y [ShapeableImageView]
 * - **Layouts responsivos**: Implementa diseño adaptable con NestedScrollView y dimensiones relativas
 * - **Procesamiento de imágenes**: Integra UCrop para recortar y optimizar imágenes de perfil
 * - **Firebase**: Almacena información del usuario y relaciones entre perfiles
 * - **Animaciones**: Implementa transiciones personalizadas entre actividades
 * - **Temas**: Adapta la interfaz a los temas claro y oscuro definidos en la aplicación
 *
 * ## Estructura de datos en Firebase:
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
 * @property database Referencia a Firebase Realtime Database para operaciones CRUD
 * @property auth Instancia de Firebase Authentication para verificación de identidad
 * @property usuarioId Identificador único del usuario que se está editando
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
    private lateinit var tvDuenio: TextView
    private lateinit var spinnerDuenio: Spinner
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var usuarioId: String? = null
    private var duenioSeleccionadoId: String? = null
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var selectedDate: Date? = null
    private lateinit var btnCambiarPassword: MaterialButton

    /**
     * Inicializa la actividad, configura la interfaz y carga los datos del usuario.
     *
     * Configura:
     * - El layout responsivo con elementos Material Design 3
     * - La inicialización de vistas y conexiones a Firebase
     * - El switch para alternar entre perfiles de persona/perro
     * - El selector de fecha de nacimiento
     * - La carga inicial de datos y configuración de botones
     *
     * @param savedInstanceState Estado guardado de la instancia si la actividad se está recreando
     */
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

    /**
     * Inicializa las referencias a las vistas de la interfaz.
     *
     * Mapea todas las vistas del layout [R.layout.editar_usuario] a propiedades de la clase,
     * incluyendo elementos Material Design como:
     * - [ShapeableImageView] para la imagen de perfil con bordes redondeados
     * - [SwitchMaterial] para seleccionar tipo de usuario (persona/perro)
     * - [MaterialCardView] para agrupar información relacionada
     * - Campos de texto con estilos consistentes según el tema de la aplicación
     *
     * Esta función implementa el principio de abstracción de Android UI,
     * separando la inicialización de vistas de su configuración.
     */
    private fun inicializarVistas() {
        ivImagen = findViewById(R.id.ivFoto)
        tvNombreUsuarioGrande = findViewById(R.id.tvNombreUsuarioGrande)
        etEmail = findViewById(R.id.etEmail)
        etNombre = findViewById(R.id.etNombre)
        etApellidos = findViewById(R.id.etApellidos)
        etFechaNacimiento = findViewById(R.id.etFechaNacimiento)
        etEdad = findViewById(R.id.etEdad)
        btnGuardar = findViewById(R.id.btnGuardarUsuario)
        btnBack = findViewById(R.id.btnBack)
        switchEsPerro = findViewById(R.id.switchEsPerro)
        tvDuenio = findViewById(R.id.tvDueño)
        spinnerDuenio = findViewById(R.id.spinnerDueño)
        btnCambiarPassword = findViewById(R.id.btnCambiarPassword)

        if (ivImagen.drawable == null) {
            ivImagen.loadBase64Image(null)
        }

        etEdad.isEnabled = false
    }

    /**
     * Inicializa las conexiones con Firebase para autenticación y base de datos.
     *
     * Establece:
     * - La instancia de autenticación para obtener el usuario actual
     * - La referencia a la base de datos en tiempo real
     * - El ID del usuario desde la sesión actual
     * - El email del usuario en el campo correspondiente
     *
     * Firebase sirve como backend para la aplicación, permitiendo la sincronización
     * de datos entre dispositivos y la gestión de autenticación.
     */
    private fun inicializarFirebase() {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        usuarioId = auth.currentUser?.uid

        etEmail.setText(auth.currentUser?.email)
    }

    /**
     * Configura el selector de fecha de nacimiento.
     *
     * Establece un listener en el campo de texto para mostrar un diálogo
     * personalizado de selección de fecha cuando el usuario hace clic.
     *
     * Este selector utiliza un diseño que sigue las guías de Material Design 3
     * para ofrecer una experiencia de usuario coherente con el resto de la aplicación.
     */
    private fun configurarFechaNacimiento() {
        etFechaNacimiento.setOnClickListener {
            mostrarDateSpinnerDialog()
        }
    }

    /**
     * Muestra un diálogo personalizado para seleccionar la fecha de nacimiento.
     *
     * Crea un diálogo con:
     * - Selectores de año, mes y día con diseño Material Design
     * - Validación para asegurar fechas válidas
     * - Actualización dinámica de los días disponibles según el mes y año
     * - Botones para confirmar o cancelar la selección
     *
     * La fecha seleccionada se utiliza para actualizar el campo correspondiente
     * y calcular automáticamente la edad.
     */
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

    /**
     * Actualiza el campo de edad basado en la fecha de nacimiento seleccionada.
     *
     * Calcula la edad en años a partir de la fecha proporcionada
     * y actualiza el campo correspondiente en la interfaz.
     *
     * @param fechaNacimiento Fecha de nacimiento como objeto Date
     */
    private fun actualizarEdad(fechaNacimiento: Date) {
        val edad = calcularEdad(fechaNacimiento)
        etEdad.setText("$edad años")
    }

    /**
     * Calcula la edad en años a partir de una fecha de nacimiento.
     *
     * Implementa un cálculo preciso de edad que tiene en cuenta:
     * - La diferencia de años entre la fecha actual y la de nacimiento
     * - Ajuste si aún no se ha llegado al día del cumpleaños en el año actual
     *
     * @param fechaNacimiento Fecha de nacimiento como objeto Date
     * @return Edad calculada en años
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
     * Actualiza el selector de días según el mes y año seleccionados.
     *
     * Ajusta dinámicamente el número máximo de días disponibles en el selector
     * basándose en el mes seleccionado y teniendo en cuenta años bisiestos.
     *
     * Esta función mejora la experiencia de usuario evitando selecciones
     * de fechas inválidas.
     *
     * @param dayPicker Selector de días a actualizar
     * @param year Año seleccionado actualmente
     * @param month Mes seleccionado actualmente (0-11)
     */
    private fun updateDayPicker(dayPicker: NumberPicker, year: Int, month: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, 1)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        dayPicker.minValue = 1
        dayPicker.maxValue = daysInMonth
    }

    /**
     * Configura el comportamiento del switch para cambiar entre perfil de persona y perro.
     *
     * Establece un listener para:
     * - Mostrar/ocultar el selector de dueño cuando el tipo cambia
     * - Cargar la lista de posibles dueños cuando se selecciona tipo perro
     *
     * Este switch permite la simulación de perros en la aplicación, habilitando
     * pruebas de funcionalidad sin necesidad de múltiples dispositivos.
     */
    private fun configurarSwitch() {
        switchEsPerro.setOnCheckedChangeListener { _, isChecked ->
            tvDuenio.visibility = if (isChecked) View.VISIBLE else View.GONE
            spinnerDuenio.visibility = if (isChecked) View.VISIBLE else View.GONE

            if (isChecked) {
                cargarListaDueños()
            }
        }
    }

    /**
     * Carga la lista de posibles dueños desde Firebase.
     *
     * Realiza:
     * - Una consulta filtrada para obtener usuarios que no son perros
     * - La creación de un adaptador para el spinner con nombres de usuarios
     * - La configuración del listener para detectar selecciones
     * - La selección automática del dueño actual si existe
     *
     * Esta función implementa la relación entre entidades (Perro-Usuario)
     * que permite el sistema de monitorización de mascotas.
     */
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
                    spinnerDuenio.adapter = adapter

                    spinnerDuenio.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            duenioSeleccionadoId = dueños[position].first
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) {
                            duenioSeleccionadoId = null
                        }
                    }

                    // Si hay un dueño seleccionado previamente, seleccionarlo
                    if (duenioSeleccionadoId != null) {
                        val index = dueños.indexOfFirst { it.first == duenioSeleccionadoId }
                        if (index != -1) {
                            spinnerDuenio.setSelection(index)
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

    /**
     * Carga los datos del usuario desde Firebase.
     *
     * Realiza una consulta a la base de datos para obtener toda la información
     * del usuario actual y actualiza la interfaz con los datos recibidos.
     *
     * Este método implementa el patrón Observer, reaccionando a una respuesta
     * asíncrona de Firebase para mostrar los datos en la UI.
     */
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
                            tvDuenio.visibility = View.VISIBLE
                            spinnerDuenio.visibility = View.VISIBLE

                            val dueñoId = snapshot.child("dueñoId").getValue(String::class.java)
                            if (dueñoId != null) {
                                duenioSeleccionadoId = dueñoId
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

    /**
     * Configura los botones de la interfaz y sus acciones.
     *
     * Establece listeners para:
     * - Botón de adjuntar imagen, para abrir el selector
     * - Botón de guardar, para validar y guardar los cambios
     * - Botón de retroceso, para volver al perfil con animación
     * - Cambios en los campos de texto, para actualizar el nombre mostrado
     *
     * Implementa patrones de navegación y feedback visual siguiendo
     * las directrices de Material Design 3.
     */
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

        btnCambiarPassword.setOnClickListener {
            val intent = Intent(this, ChangePasswordActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_right_to_left, R.anim.slide_left_to_right)
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

    /**
     * Actualiza el nombre mostrado en la parte superior al editar los campos.
     *
     * Combina el nombre y apellidos para mostrar una vista previa
     * del nombre completo mientras el usuario realiza ediciones.
     *
     * Esta función proporciona feedback visual inmediato, mejorando
     * la experiencia de usuario durante la edición.
     */
    private fun actualizarNombreGrande() {
        val nombre = etNombre.text.toString()
        val apellidos = etApellidos.text.toString()
        tvNombreUsuarioGrande.text = "$nombre $apellidos"
    }

    /**
     * Valida y guarda los cambios en el perfil del usuario.
     *
     * Realiza:
     * - Creación de un mapa con los cambios a realizar
     * - Actualización en Firebase con gestión de éxito/error
     * - Navegación a la pantalla de perfil con animación en caso de éxito
     *
     * Este método implementa la persistencia de datos en Firebase,
     * manteniendo sincronizados los datos entre dispositivos.
     */
    private fun guardarCambios() {
        usuarioId?.let { id ->
            val updates = mutableMapOf<String, Any>()

            updates["nombre"] = etNombre.text.toString()
            updates["apellidos"] = etApellidos.text.toString()

            selectedDate?.let {
                updates["fechaNacimiento"] = dateFormat.format(it)
            }

            updates["isPerro"] = switchEsPerro.isChecked
            if (switchEsPerro.isChecked && duenioSeleccionadoId != null) {
                updates["dueñoId"] = duenioSeleccionadoId!!
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

    /**
     * Abre un selector para elegir una imagen de perfil desde la galería o cámara.
     *
     * Crea un intent chooser que combina:
     * - Opción para seleccionar una imagen de la galería
     * - Opción para capturar una nueva foto con la cámara
     *
     * Utiliza el sistema de contratos de actividad de Android para
     * manejar el resultado de forma limpia y segura.
     */
    private fun abrirGaleriaOCamara() {
        val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        val chooser = Intent.createChooser(pickIntent, "Selecciona una imagen")
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takePictureIntent))

        imagePickerLauncher.launch(chooser)
    }

    /**
     * Gestor de selección de imágenes desde galería o cámara.
     * 
     * Utiliza el nuevo sistema de contratos de actividad para manejar
     * de forma segura los resultados de la selección de imágenes.
     * Soporta tanto selección desde galería como captura con cámara.
     */
    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val selectedImageUri: Uri? = result.data!!.data
                if (selectedImageUri != null) {
                    // Si la imagen viene de la galería
                    iniciarUCrop(selectedImageUri)
                } else if (result.data!!.extras?.containsKey("data") == true) {
                    // Si la imagen viene de la cámara (se recibe como Bitmap)
                    val imageBitmap = result.data!!.extras?.get("data") as Bitmap
                    // Guardamos temporalmente el bitmap en un archivo para pasarlo a UCrop
                    val tempFile = File.createTempFile("camera_image", ".jpg", cacheDir)
                    val outputStream = FileOutputStream(tempFile)
                    imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    outputStream.close()
                    // Iniciamos UCrop con este archivo temporal
                    iniciarUCrop(Uri.fromFile(tempFile))
                } else {
                    Toast.makeText(this, "Error al seleccionar imagen", Toast.LENGTH_SHORT).show()
                }
            }
        }

    /**
     * Inicia el proceso de recorte de imagen con UCrop.
     *
     * Configura:
     * - URI de origen y destino para la imagen
     * - Relación de aspecto 1:1 para perfil circular
     * - Tamaño máximo para optimizar rendimiento y almacenamiento
     *
     * Utiliza la biblioteca externa UCrop para proporcionar una
     * experiencia de recorte de imagen profesional e intuitiva.
     *
     * @param sourceUri URI de la imagen original seleccionada
     */
    private fun iniciarUCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(cacheDir, "cropped_image.jpg"))

        UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(800, 800)
            .start(this)
    }

    /**
     * Procesa el resultado de la actividad UCrop.
     *
     * Maneja:
     * - Éxito: obtiene la imagen recortada, la muestra y la guarda en Firebase
     * - Error: muestra un mensaje informativo al usuario
     *
     * Sobrescribe el método estándar de Android para procesar resultados
     * de actividades, específicamente adaptado para UCrop.
     *
     * @param requestCode Código de la solicitud
     * @param resultCode Código del resultado (éxito o error)
     * @param data Intent con datos del resultado, incluida la URI de la imagen
     */
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

    /**
     * Guarda la imagen recortada en Firebase.
     *
     * Proceso:
     * - Convierte el bitmap a una cadena Base64 manteniendo calidad
     * - Actualiza el campo correspondiente en Firebase
     * - Proporciona feedback al usuario sobre el resultado
     *
     * La codificación Base64 permite almacenar imágenes directamente
     * en la base de datos Firebase sin necesidad de servicios adicionales.
     *
     * @param bitmap Imagen recortada como Bitmap
     */
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

    /**
     * Carga la imagen de perfil del usuario desde Firebase.
     *
     * Realiza:
     * - Una consulta específica al nodo de imagen del usuario
     * - La decodificación y carga de la imagen si existe
     * - La carga de una imagen predeterminada si no hay imagen guardada
     *
     * Utiliza la extensión [loadBase64Image] para gestionar eficientemente
     * el procesamiento y visualización de la imagen.
     */
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

    /**
     * Navega al perfil de usuario con animación personalizada.
     *
     * Implementa:
     * - Creación e inicio del intent para la actividad de perfil
     * - Finalización de la actividad actual para evitar acumulación en la pila
     * - Aplicación de animaciones de transición personalizadas
     *
     * Las animaciones mejoran la experiencia del usuario proporcionando
     * feedback visual sobre la navegación dentro de la aplicación.
     */
    private fun redirigirAPerfil() {
        val intent = Intent(this, PerfilUsuario::class.java)
        startActivity(intent)
        finish()
        // Aplicar la animación de deslizamiento de izquierda a derecha
        overridePendingTransition(R.anim.slide_right_to_left, R.anim.slide_left_to_right)
    }
    
    /**
     * Sobrescribe el comportamiento del botón Back con animación personalizada.
     *
     * Mantiene la funcionalidad estándar del botón Back pero añade
     * la animación de transición deslizante al volver al perfil.
     *
     * Este método implementa coherencia en la navegación, asegurando
     * que todas las formas de volver al perfil utilicen la misma animación.
     */
    override fun onBackPressed() {
        super.onBackPressed()
        // Aplicar la animación de deslizamiento de izquierda a derecha
        overridePendingTransition(R.anim.slide_right_to_left, R.anim.slide_left_to_right)
    }
}