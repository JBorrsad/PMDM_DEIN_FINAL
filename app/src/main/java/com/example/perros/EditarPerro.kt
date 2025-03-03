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
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.yalantis.ucrop.UCrop
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import coil.load
import java.util.Locale

/**
 * # EditarPerro
 *
 * Actividad para editar los datos de un perro en el sistema de monitorización de mascotas.
 *
 * ## Funcionalidad principal
 * Esta clase permite a los usuarios modificar la información de un perro registrado, incluyendo:
 * - Datos básicos (nombre, raza, peso)
 * - Gestión de fechas relevantes (nacimiento, adopción)
 * - Procesamiento y actualización de la imagen del perro
 * - Asignación o cambio del dueño
 *
 * ## Características técnicas implementadas:
 * - **Material Design 3**: Utiliza componentes MD3 como [MaterialCardView], [ShapeableImageView] y [FloatingActionButton]
 * - **Layouts responsivos**: Implementa un diseño adaptable con [NestedScrollView] y dimensiones relativas
 * - **Procesamiento de imágenes**: Integra la biblioteca UCrop para recortar y optimizar imágenes
 * - **Firebase**: Almacena información del perro y sus relaciones con usuarios en la base de datos en tiempo real
 * - **Animaciones**: Implementa transiciones personalizadas entre actividades
 * - **Imágenes responsivas**: Utiliza ShapeableImageView para mostrar imágenes con bordes redondeados
 *
 * ## Estructura de datos en Firebase:
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
 * @property database Referencia a Firebase Realtime Database para operaciones CRUD
 * @property auth Instancia de Firebase Authentication para verificación de usuarios
 * @property perroId Identificador único del perro que se está editando
 *
 * @see PerfilPerro Actividad que muestra el perfil completo del perro
 * @see UCrop Biblioteca externa para recortar imágenes de forma eficiente
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
                    Toast.makeText(this, R.string.error_seleccionar_imagen, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

    /**
     * Gestor de resultados del recorte de imagen con UCrop.
     * 
     * Procesa la imagen recortada cuando UCrop finaliza exitosamente.
     * Maneja tanto los casos de éxito (cargando y guardando la imagen)
     * como los errores (mostrando mensajes descriptivos).
     */
    private val uCropLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val resultUri = UCrop.getOutput(result.data!!)
                try {
                    // Convertir Uri a Bitmap
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, resultUri)

                    // Cargar la imagen recortada usando el método de extensión
                    ivImagen.loadBase64Image(bitmapToBase64OriginalQuality(bitmap))

                    // Guardar la imagen en Firebase
                    guardarImagenRecortadaEnFirebase(bitmap)
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        getString(R.string.error_recortar_imagen, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                val cropError = UCrop.getError(result.data!!)
                Toast.makeText(
                    this,
                    getString(R.string.error_recortar_imagen, cropError?.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    /**
     * Inicializa la actividad, configura la interfaz y carga los datos del perro.
     *
     * Configura:
     * - El layout responsivo con elementos Material Design 3
     * - El callback para el botón Back con animaciones personalizadas
     * - La inicialización de Firebase para persistencia de datos
     * - Los selectores de fecha con diálogos personalizados
     * 
     * @param savedInstanceState Estado guardado de la instancia si la actividad se está recreando
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.editar_perro)

        // Configurar la respuesta del botón Back
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                volverAPerfilPerro()
            }
        })

        inicializarVistas()
        inicializarFirebase()
        configurarFechas()
        cargarDatosPerro()
        configurarBotones()
    }

    /**
     * Inicializa las referencias a las vistas de la interfaz.
     *
     * Mapea todas las vistas del layout [R.layout.editar_perro] a propiedades de la clase,
     * incluyendo elementos Material Design como:
     * - [ShapeableImageView] para la imagen del perro con bordes redondeados
     * - [MaterialCardView] para agrupar información relacionada
     * - Campos de texto con estilos consistentes con el tema de la aplicación
     *
     * Esta función implementa el principio de abstracción de Android UI,
     * separando la inicialización de vistas de su configuración.
     */
    private fun inicializarVistas() {
        btnGuardar = findViewById(R.id.btnGuardarUsuario)
        btnBack = findViewById(R.id.btnBack)
        ivImagen = findViewById(R.id.ivFoto)
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

    /**
     * Inicializa las conexiones con Firebase para autenticación y base de datos.
     *
     * Establece:
     * - La instancia de autenticación para verificar permisos
     * - La referencia a la base de datos en tiempo real para operaciones CRUD
     * - El ID del perro desde los extras del intent
     *
     * Firebase sirve como backend para la aplicación, permitiendo la sincronización
     * de datos entre dispositivos y facilitando el monitoreo en tiempo real.
     */
    private fun inicializarFirebase() {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        perroId = intent.getStringExtra("perroId")
    }

    /**
     * Carga la lista de posibles dueños para el perro desde Firebase.
     *
     * Realiza:
     * - Una consulta filtrada a Firebase para obtener usuarios que no son perros
     * - La creación de un adaptador para el spinner con nombres de usuarios
     * - La configuración del listener para detectar selecciones del usuario
     * - La selección automática del dueño actual si existe
     *
     * Este método implementa la relación entre entidades (Perro-Usuario)
     * que permite el sistema de monitorización de mascotas.
     */
    private fun cargarListaDuenos() {
        database.child("users")
            .orderByChild("isPerro")
            .equalTo(false)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val duenos = mutableListOf<Pair<String, String>>()

                    for (userSnapshot in snapshot.children) {
                        val nombre = userSnapshot.child("nombre").getValue(String::class.java) ?: ""
                        val apellidos =
                            userSnapshot.child("apellidos").getValue(String::class.java) ?: ""
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

                    spinnerDueno.onItemSelectedListener =
                        object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(
                                parent: AdapterView<*>?,
                                view: View?,
                                position: Int,
                                id: Long
                            ) {
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
                    Toast.makeText(
                        this@EditarPerro,
                        R.string.error_cargar_duenos,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    /**
     * Configura los selectores de fecha para nacimiento y adopción.
     *
     * Establece listeners en los campos de texto para mostrar un diálogo
     * personalizado de selección de fecha cuando el usuario hace clic.
     *
     * Estos selectores utilizan un diseño que sigue las guías de Material Design 3
     * para ofrecer una experiencia de usuario coherente con el resto de la aplicación.
     */
    private fun configurarFechas() {
        tvFechaNacimiento.setOnClickListener {
            mostrarDateSpinnerDialog(true)
        }

        tvFechaAdopcion.setOnClickListener {
            mostrarDateSpinnerDialog(false)
        }
    }

    /**
     * Muestra un diálogo personalizado para seleccionar una fecha.
     *
     * Crea un diálogo con:
     * - Selectores de año, mes y día con diseño Material Design
     * - Validación para asegurar fechas válidas
     * - Actualización dinámica de los días disponibles según el mes y año seleccionados
     * - Botones para confirmar o cancelar la selección
     *
     * La fecha seleccionada se utiliza para actualizar el campo correspondiente
     * y, en caso de la fecha de nacimiento, calcular automáticamente la edad.
     *
     * @param isNacimiento Indica si se está seleccionando la fecha de nacimiento (true) o adopción (false)
     */
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
        val months = arrayOf(
            "Ene",
            "Feb",
            "Mar",
            "Abr",
            "May",
            "Jun",
            "Jul",
            "Ago",
            "Sep",
            "Oct",
            "Nov",
            "Dic"
        )
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

    /**
     * Actualiza el selector de días según el mes y año seleccionados.
     *
     * Ajusta dinámicamente el número máximo de días disponibles en el selector
     * basándose en el mes seleccionado y teniendo en cuenta años bisiestos.
     *
     * Esta función mejora la experiencia de usuario evitando selecciones de fechas inválidas,
     * siguiendo las mejores prácticas de validación de formularios.
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
     * Actualiza el campo de edad basado en la fecha de nacimiento.
     *
     * Calcula la edad en años a partir de la fecha de nacimiento proporcionada
     * y actualiza el campo correspondiente en la interfaz.
     *
     * @param fechaNacimientoMillis Timestamp de la fecha de nacimiento en milisegundos
     */
    private fun actualizarEdad(fechaNacimientoMillis: Long) {
        val edad = calcularEdad(fechaNacimientoMillis)
        tvEdad.text = "$edad años"
    }

    /**
     * Carga los datos del perro desde Firebase.
     *
     * Realiza una consulta a la base de datos para obtener toda la información
     * del perro y actualiza la interfaz con los datos recibidos.
     *
     * Este método implementa el patrón Observer, reaccionando a una respuesta
     * asíncrona de Firebase para mostrar los datos en la UI.
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
                    val fechaNacimiento =
                        snapshot.child("fechaNacimiento").getValue(Long::class.java) ?: 0L
                    val fechaAdopcion =
                        snapshot.child("fechaAdopcion").getValue(Long::class.java) ?: 0L
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
                    Toast.makeText(this@EditarPerro, "Error al cargar datos", Toast.LENGTH_SHORT)
                        .show()
                }
            })
    }

    /**
     * Actualiza la interfaz de usuario con los datos del perro.
     *
     * Completa todos los campos del formulario con la información recibida,
     * incluyendo:
     * - Datos básicos (nombre, raza, peso)
     * - Cálculo de edad a partir de la fecha de nacimiento
     * - Formateo de fechas según el locale del dispositivo
     *
     * @param nombre Nombre del perro
     * @param raza Raza del perro
     * @param peso Peso del perro en kg
     * @param fechaNacimiento Timestamp de la fecha de nacimiento
     * @param fechaAdopcion Timestamp de la fecha de adopción
     */
    private fun actualizarUI(
        nombre: String,
        raza: String,
        peso: Double,
        fechaNacimiento: Long,
        fechaAdopcion: Long
    ) {
        tvNombreMascota.setText(nombre)
        tvTipoRaza.setText(raza)
        tvPeso.setText(String.format(Locale.getDefault(), "%.1f", peso))

        // Calcular edad
        val edad = calcularEdad(fechaNacimiento)
        tvEdad.text = "$edad años"

        // Formatear fechas
        selectedBirthDate = Date(fechaNacimiento)
        selectedAdoptionDate = Date(fechaAdopcion)
        tvFechaNacimiento.setText(dateFormat.format(selectedBirthDate!!))
        tvFechaAdopcion.setText(dateFormat.format(selectedAdoptionDate!!))
    }

    /**
     * Calcula la edad en años a partir de una fecha de nacimiento.
     *
     * Implementa un cálculo preciso de edad que tiene en cuenta:
     * - La diferencia de años entre la fecha actual y la de nacimiento
     * - Ajuste si aún no se ha llegado al día del cumpleaños en el año actual
     *
     * @param fechaNacimiento Timestamp de la fecha de nacimiento en milisegundos
     * @return Edad calculada en años
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
     * Carga la imagen del perro utilizando codificación Base64.
     *
     * Utiliza la extensión [loadBase64Image] para cargar la imagen:
     * - Si hay una imagen guardada, la decodifica y muestra
     * - Si no hay imagen, muestra una imagen predeterminada
     *
     * Este método implementa el procesamiento eficiente de imágenes
     * evitando la necesidad de almacenamiento externo de archivos.
     *
     * @param imagenBase64 Representación en Base64 de la imagen del perro, puede ser null
     */
    private fun cargarImagenPerro(imagenBase64: String?) {
        ivImagen.loadBase64Image(imagenBase64)
    }

    /**
     * Configura los botones de la interfaz y sus acciones.
     *
     * Establece listeners para:
     * - Botón de retroceso, para volver al perfil con animación
     * - Botón de adjuntar imagen, para abrir el selector de imágenes
     * - Botón de guardar, para validar y guardar los cambios
     *
     * Implementa patrones de navegación y feedback visual siguiendo
     * las directrices de Material Design 3.
     */
    private fun configurarBotones() {
        btnBack.setOnClickListener {
            volverAPerfilPerro()
        }
        val btnAdjuntarImagen = findViewById<FloatingActionButton>(R.id.btnAdjuntarImagen)
        btnAdjuntarImagen.setOnClickListener {
            val pickIntent = Intent(
                Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            )
            val takePictureIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            val chooser = Intent.createChooser(pickIntent, "Selecciona una imagen")
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takePictureIntent))
            imagePickerLauncher.launch(chooser)
        }
        btnGuardar.setOnClickListener {
            guardarCambios()
        }
    }

    /**
     * Regresa a la pantalla de perfil del perro con animación personalizada.
     *
     * Implementa:
     * - Creación del intent con el ID del perro
     * - Aplicación de opciones de transición con animaciones personalizadas
     * - Finalización de la actividad actual
     *
     * Las animaciones mejoran la experiencia del usuario proporcionando
     * feedback visual sobre la navegación dentro de la aplicación.
     */
    private fun volverAPerfilPerro() {
        // Crear opciones de transición con animaciones personalizadas
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this,
            R.anim.slide_right_to_left,
            R.anim.slide_left_to_right
        )

        // Iniciar la actividad de perfil con las animaciones
        val intent = Intent(this, PerfilPerro::class.java)
        intent.putExtra("perroId", perroId)
        startActivity(intent, options.toBundle())

        finish()
    }

    /**
     * Valida y guarda los cambios en el perfil del perro.
     *
     * Realiza:
     * - Validación de campos obligatorios y formatos correctos
     * - Creación de un mapa con los cambios a realizar
     * - Actualización en Firebase con gestión de éxito/error
     * - Navegación a la pantalla de perfil con animación en caso de éxito
     *
     * Este método implementa la persistencia de datos en Firebase,
     * manteniendo sincronizados los datos entre dispositivos.
     */
    private fun guardarCambios() {
        perroId?.let { id ->
            val updates = mutableMapOf<String, Any>()
            // Obtener los valores de los EditText
            val nombre = tvNombreMascota.text.toString().trim()
            val raza = tvTipoRaza.text.toString().trim()
            val pesoText = tvPeso.text.toString().trim()
            // Validar que los campos no estén vacíos
            if (nombre.isEmpty() || raza.isEmpty() || pesoText.isEmpty()) {
                Toast.makeText(this, R.string.campos_obligatorios, Toast.LENGTH_SHORT).show()
                return@let
            }
            // Convertir el peso a Double
            val peso = try {
                pesoText.toDouble()
            } catch (e: NumberFormatException) {
                Toast.makeText(this, R.string.peso_numero, Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, R.string.datos_actualizados, Toast.LENGTH_SHORT).show()

                    // Crear opciones de transición con animaciones personalizadas
                    val options = ActivityOptionsCompat.makeCustomAnimation(
                        this,
                        R.anim.slide_right_to_left,
                        R.anim.slide_left_to_right
                    )

                    val intent = Intent(this, PerfilPerro::class.java)
                    intent.putExtra("perroId", perroId)
                    startActivity(intent, options.toBundle())
                    finish()
                }
                .addOnFailureListener { error ->
                    Toast.makeText(
                        this,
                        getString(R.string.error_actualizar, error.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        } ?: run {
            Toast.makeText(this, R.string.error_id_perro, Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------- Funcionalidad de manejo de imagen --------------------

    /**
     * Inicia el proceso de recorte de imagen con UCrop.
     *
     * Configura:
     * - URI de origen y destino para la imagen
     * - Relación de aspecto 1:1 para perfil circular
     * - Tamaño máximo para optimizar rendimiento y almacenamiento
     * - Manejo de errores con feedback al usuario
     *
     * Utiliza la biblioteca externa UCrop para proporcionar una
     * experiencia de recorte de imagen profesional e intuitiva.
     *
     * @param sourceUri URI de la imagen original seleccionada
     */
    private fun iniciarUCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(cacheDir, "cropped_image_dog.jpg"))

        try {
            val uCropIntent = UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(800, 800)
                .getIntent(this)

            uCropLauncher.launch(uCropIntent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.error_recortar_imagen, e.message),
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
        perroId?.let { id ->
            // Mantener la calidad original de la imagen
            val imageBase64 = bitmapToBase64OriginalQuality(bitmap)

            database.child("users").child(id).child("imagenBase64").setValue(imageBase64)
                .addOnSuccessListener {
                    Toast.makeText(this, R.string.imagen_guardada, Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, R.string.error_guardar_imagen, Toast.LENGTH_SHORT).show()
                }
        } ?: run {
            Toast.makeText(this, R.string.error_id_perro, Toast.LENGTH_SHORT).show()
        }
    }
}
