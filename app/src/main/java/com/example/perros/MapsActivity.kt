package com.example.perros

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.gms.tasks.Tasks
// Importaciones para Google Maps Utils
import com.google.maps.android.clustering.ClusterManager
import com.example.perros.DogsClusterManager.DogItem
// Importación para loadBase64Image
import com.example.perros.loadBase64Image

/**
 * Actividad principal del mapa que gestiona el monitoreo y seguimiento de perros.
 *
 * Esta actividad coordina la visualización del mapa, interacción con el usuario y navegación,
 * delegando la lógica específica a clases auxiliares para una mejor organización.
 *
 * Proporciona funcionalidades para:
 * - Visualizar ubicaciones de perros en tiempo real
 * - Gestionar zonas seguras para cada perro
 * - Cambiar entre diferentes perros registrados
 * - Acceder a perfiles y ajustes
 *
 * @property mMap Instancia del mapa de Google
 * @property database Referencia a Firebase Realtime Database
 * @property auth Instancia de Firebase Authentication
 */
@Suppress("DEPRECATION")
class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth

    private lateinit var spinnerPerros: Spinner
    private lateinit var btnEditarZonaSegura: LinearLayout
    private lateinit var btnPerfilPerro: LinearLayout
    private lateinit var btnPerfilUsuario: ShapeableImageView
    private lateinit var btnZonaText: TextView
    
    // Nuevos elementos de UI para edición de zona
    private lateinit var tvEditingZone: TextView
    private lateinit var layoutZoneButtons: LinearLayout
    private lateinit var btnSaveZone: FloatingActionButton
    private lateinit var btnCancelZone: FloatingActionButton
    private lateinit var btnEditZone: FloatingActionButton

    // Lista de perros: par (nombreDelPerro, idDelPerro)
    private var listaPerros = mutableListOf<Pair<String, String>>()
    private var perroSeleccionadoId: String? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val requestLocationPermission = 1
    
    // Instancias de las clases auxiliares
    private lateinit var locationManager: DogLocationManager
    private lateinit var clusterManager: DogsClusterManager

    /**
     * Inicializa y configura la actividad.
     * Verifica el estado de precarga de datos y decide si proceder con normalidad
     * o iniciar una carga de respaldo.
     *
     * @param savedInstanceState Estado guardado de la actividad
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Establecer el layout primero para poder inicializar las vistas
        setContentView(R.layout.activity_maps)
        
        // Inicializar Firebase Auth y Database aquí para garantizar que estén disponibles en todas las rutas de código
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        
        // Inicializar vistas básicas necesarias para todos los flujos
        btnPerfilUsuario = findViewById(R.id.ivPerfilUsuario)
        
        // Verificar si los datos están inicializados antes de continuar
        Log.d("MapsActivity", "onCreate: Verificando estado de inicialización de datos")
        Log.d("MapsActivity", "Estado de inicialización: ${DatosPrecargados.isInicializado()}")
        
        // Mostrar estado de datos precargados (debug)
        DatosPrecargados.mostrarEstadoDatos()
        
        // Si los datos no están inicializados, podríamos tener que cargarlos aquí
        if (!DatosPrecargados.isInicializado()) {
            Log.w("MapsActivity", "Los datos no están completamente inicializados. Iniciando carga de respaldo.")
            
            // Esta es una precaución extra, en caso de que la carga en SplashLoginActivity fallara
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                Toast.makeText(this, "Cargando datos de perros...", Toast.LENGTH_LONG).show()
                
                // Intentar precargar datos
                Log.d("MapsActivity", "Refrescando datos precargados")
                
                // Ya tenemos auth inicializado arriba
                val usuarioActual = auth.currentUser
                
                if (usuarioActual != null) {
                    // ACTUALIZADO: Ya no es necesario pasar el context
                    preloadUserData(usuarioActual.uid) {
                        Log.d("MapsActivity", "Datos actualizado correctamente")
                        Handler(Looper.getMainLooper()).post {
                            cargarPerrosUsuario()
                        }
                    }
                }
            } else {
                Log.e("MapsActivity", "No hay usuario autenticado para cargar datos")
                Toast.makeText(this, "Error: No hay sesión activa", Toast.LENGTH_LONG).show()
                
                // Redirigir al login
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                return
            }
        } else {
            // Los datos están inicializados, continuar normalmente
            continuarInicializacion(savedInstanceState)
        }
    }

    /**
     * Continúa con la inicialización normal de la actividad después de verificar los datos.
     * 
     * @param unused Estado guardado de la actividad (no utilizado)
     */
    @Suppress("UNUSED_PARAMETER")
    private fun continuarInicializacion(unused: Bundle?) {
        Log.d("MapsActivity", "Continuando inicialización")
        
        // El layout ya fue establecido en onCreate
        
        // La autenticación y la base de datos ya están inicializadas en el onCreate
        // No es necesario volver a inicializarlas aquí
        
        // Inicializar referencias a la base de datos
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Inicializar todas las vistas (btnPerfilUsuario ya fue inicializado en onCreate)
        initializeViews()
        setupMapFragment()
        setupButtonListeners()
        
        // Asegurar que los elementos de UI tengan el estado inicial correcto
        tvEditingZone.visibility = View.GONE
        layoutZoneButtons.visibility = View.GONE
        btnEditZone.visibility = View.VISIBLE
        
        // Cargar perros del usuario
        Log.d("MapsActivity", "Cargando perros del usuario")
        cargarPerrosUsuario()
        
        // Verificar y solicitar permisos
        verificarYPedirPermisos()
    }

    /**
     * Inicializa las referencias a las vistas de la UI.
     */
    private fun initializeViews() {
        // btnPerfilUsuario ya fue inicializado en onCreate
        
        spinnerPerros = findViewById(R.id.nombreperro)
        btnEditarZonaSegura = findViewById(R.id.btn_home)
        btnPerfilPerro = findViewById(R.id.btnPerfilPerro)
        btnZonaText = findViewById(R.id.btnZonaText)
        
        // Inicializar nuevas vistas
        tvEditingZone = findViewById(R.id.tvEditingZone)
        layoutZoneButtons = findViewById(R.id.layoutZoneButtons)
        btnSaveZone = findViewById(R.id.btnSaveZone)
        btnCancelZone = findViewById(R.id.btnCancelZone)
        btnEditZone = findViewById(R.id.btnEditZone)

        setupSpinnerListener()
    }

    /**
     * Configura el listener para el spinner de selección de perros.
     */
    private fun setupSpinnerListener() {
        spinnerPerros.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                perroSeleccionadoId = listaPerros[position].second
                Log.d("MapsActivity", "Perro seleccionado: ${listaPerros[position].first} (ID: $perroSeleccionadoId)")

                // Aseguramos que la vista del elemento seleccionado se actualiza correctamente
                val adapter = spinnerPerros.adapter as? DogSpinnerAdapter
                adapter?.notifyDataSetChanged()
                
                // Si ya tenemos el mapa y el gestor de ubicaciones inicializado
                if (::locationManager.isInitialized && perroSeleccionadoId != null) {
                // Lee la zona segura de este perro y dibuja el círculo
                    locationManager.mostrarZonaSegura(perroSeleccionadoId!!)
                    // Lee la ubicación del perro y muestra el marker
                    locationManager.mostrarUbicacionPerro(perroSeleccionadoId!!)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /**
     * Configura el fragmento del mapa para inicializarlo de forma asíncrona.
     */
    private fun setupMapFragment() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    /**
     * Configura los listeners para los botones de la interfaz.
     */
    private fun setupButtonListeners() {
        btnEditarZonaSegura.setOnClickListener {
            if (perroSeleccionadoId.isNullOrEmpty()) {
                Toast.makeText(this, "Selecciona un perro primero", Toast.LENGTH_SHORT).show()
            } else if (::locationManager.isInitialized) {
                locationManager.centrarMapaEnZonaSegura(perroSeleccionadoId!!)
            }
        }

        btnPerfilPerro.setOnClickListener {
            if (perroSeleccionadoId.isNullOrEmpty()) {
                Toast.makeText(this, "Selecciona un perro primero", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, PerfilPerro::class.java)
                intent.putExtra("perroId", perroSeleccionadoId)
                startActivity(intent)
            }
        }

        btnPerfilUsuario.setOnClickListener {
            startActivity(Intent(this, PerfilUsuario::class.java))
        }
        
        // Botón de ajustes
        findViewById<View>(R.id.btnAjustes).setOnClickListener {
            startActivity(Intent(this, AjustesActivity::class.java))
        }
        
        // Botones de edición de zona segura
        btnSaveZone.setOnClickListener {
            if (::locationManager.isInitialized && perroSeleccionadoId != null) {
                locationManager.guardarZonaSegura(perroSeleccionadoId!!)
                desactivarModoEdicionZonaSegura()
            } else {
                Toast.makeText(this, "No se puede guardar la zona segura", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnCancelZone.setOnClickListener {
            desactivarModoEdicionZonaSegura()
        }
        
        btnEditZone.setOnClickListener {
            activarModoEdicionZonaSegura()
        }
    }

    /**
     * Activa el modo de edición de la zona segura.
     */
    private fun activarModoEdicionZonaSegura() {
        if (perroSeleccionadoId.isNullOrEmpty()) {
            Toast.makeText(this, "Selecciona un perro primero", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Actualizar UI para modo edición
        tvEditingZone.visibility = View.VISIBLE
        layoutZoneButtons.visibility = View.VISIBLE
        btnEditZone.visibility = View.GONE
        
        Toast.makeText(this, "Toca en el mapa para definir la nueva zona segura", Toast.LENGTH_LONG).show()

        if (::locationManager.isInitialized) {
            locationManager.activarModoEdicionZona(perroSeleccionadoId!!)
        }
    }
    
    /**
     * Desactiva el modo de edición de la zona segura.
     */
    private fun desactivarModoEdicionZonaSegura() {
        // Ocultar banner y botones de edición
        tvEditingZone.visibility = View.GONE
        layoutZoneButtons.visibility = View.GONE
        btnEditZone.visibility = View.VISIBLE
        
        if (::locationManager.isInitialized) {
            locationManager.desactivarModoEdicionZona()
        }
    }

    /**
     * Método llamado cuando el mapa está listo para ser usado.
     * Configura el mapa, inicializa los gestores de ubicación y clusters.
     *
     * @param googleMap Instancia inicializada del mapa
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Inicializar gestores especializados
        val rawClusterManager = ClusterManager<DogItem>(this, mMap)
        this.clusterManager = DogsClusterManager(this, mMap, rawClusterManager)
        locationManager = DogLocationManager(this, mMap, database, fusedLocationClient, clusterManager)

        // Configurar listeners del mapa
        mMap.setOnMapClickListener { latLng ->
            if (::locationManager.isInitialized && locationManager.modoEdicionZonaSegura && perroSeleccionadoId != null) {
                locationManager.definirZonaSegura(latLng)
            }
        }
        
        // Configurar mapa
        mMap.uiSettings.isZoomControlsEnabled = false
        mMap.uiSettings.isMyLocationButtonEnabled = false
        
        // Personalizar el estilo del mapa
        try {
            val success = mMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    this, R.raw.map_style
                )
            )
            if (!success) {
                Log.e("MapsActivity", "Error al aplicar estilo al mapa")
            }
        } catch (e: Exception) {
            Log.e("MapsActivity", "Error al cargar estilo del mapa", e)
        }
        
        // Verificar permisos y habilitar ubicación
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
            locationManager.startLocationUpdates()
            
            // Solo centrar en la ubicación si no hay perros cargados
            if (listaPerros.isEmpty()) {
                locationManager.centrarMapaEnUbicacionActual()
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                requestLocationPermission
            )
        }

        // Si ya hay un perro seleccionado, mostrar su ubicación y zona segura
        if (perroSeleccionadoId != null) {
            locationManager.mostrarUbicacionPerro(perroSeleccionadoId!!)
            locationManager.mostrarZonaSegura(perroSeleccionadoId!!)
        }
    }

    /**
     * Carga la lista de perros asociados al usuario actual.
     * Intenta primero obtener los datos de la caché, y si no están disponibles, los solicita a Firebase.
     */
    private fun cargarPerrosUsuario() {
        listaPerros.clear()
        val firebaseAuth = FirebaseAuth.getInstance()
        val userId = firebaseAuth.currentUser?.uid
        
        if (userId == null) {
            Toast.makeText(this, "Error: No hay sesión activa", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d("MapsActivity", "Cargando perros del usuario: $userId")
        
        // Verificar que btnPerfilUsuario esté inicializado antes de usarlo
        if (::btnPerfilUsuario.isInitialized) {
            // Cargar la imagen del usuario (dueño)
            database.child("users").child(userId).child("imagenBase64")
                .get().addOnSuccessListener { snapshot ->
                    val imageBase64 = snapshot.getValue(String::class.java)
                    btnPerfilUsuario.loadBase64Image(imageBase64, applyCircleCrop = true)
                    Log.d("MapsActivity", "Imagen de perfil del usuario cargada")
                }.addOnFailureListener {
                    Log.e("MapsActivity", "Error al cargar imagen de perfil del usuario")
                    btnPerfilUsuario.loadBase64Image(null)
                }
        } else {
            Log.e("MapsActivity", "btnPerfilUsuario no está inicializado aún, no se cargará la imagen")
        }
        
        // Primero cargar desde datos precargados
        val perrosIds = DatosPrecargados.obtenerIdsPerrosUsuario(userId)
        
        if (perrosIds.isNotEmpty()) {
            Log.d("MapsActivity", "Usando perros de datos precargados: ${perrosIds.size} perros")
            
            val perrosTemp = mutableListOf<Pair<String, String>>()
            
            // Para cada ID de perro, obtener sus datos
            perrosIds.forEach { perroId ->
                val perroSnapshot = DatosPrecargados.obtenerPerro(perroId)
                if (perroSnapshot != null) {
                    val nombre = perroSnapshot.child("nombre").getValue(String::class.java) ?: "Sin nombre"
                    perrosTemp.add(Pair(nombre, perroId))
                }
            }
            
            if (perrosTemp.isNotEmpty()) {
                listaPerros.addAll(perrosTemp)
                // Verificar si podemos mostrar los perros (requiere que spinnerPerros esté inicializado)
                if (::spinnerPerros.isInitialized) {
                    mostrarPerrosEnSpinner()
                } else {
                    Log.e("MapsActivity", "spinnerPerros no está inicializado, no se pueden mostrar los perros")
                }
                return
            }
        }
        
        // Si no hay datos precargados, consultar a Firebase
        Log.d("MapsActivity", "No hay datos precargados, obteniendo de Firebase")
            
        // Consulta a Firebase para obtener los perros asociados al dueño
        database.child("users")
            .orderByChild("dueñoId")
            .equalTo(userId)
            .get()
            .addOnSuccessListener { perrosSnapshot ->
                if (perrosSnapshot.exists()) {
                    Log.d("MapsActivity", "Perros encontrados en Firebase: ${perrosSnapshot.childrenCount}")
                    
                    // Guardar en datos precargados para futuras consultas
                    DatosPrecargados.guardarPerrosUsuario(userId, perrosSnapshot)
                    
                    procesarListaPerros(perrosSnapshot)
                } else {
                    Log.d("MapsActivity", "No se encontraron perros para este usuario")
                    // Verificar si podemos mostrar el mensaje (requiere que la actividad siga activa)
                    try {
                        mostrarMensajeNoPerros()
                    } catch (e: Exception) {
                        Log.e("MapsActivity", "Error al mostrar mensaje de no perros: ${e.message}")
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("MapsActivity", "Error al cargar perros: ${exception.message}")
                try {
                    Toast.makeText(this, "Error al cargar tus perros", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("MapsActivity", "Error al mostrar toast: ${e.message}")
                }
            }
    }

    /**
     * Procesa la lista de perros obtenida y actualiza la interfaz
     * 
     * @param perrosSnapshot Snapshot de Firebase con los datos de los perros
     */
    private fun procesarListaPerros(perrosSnapshot: DataSnapshot) {
        val perrosTemp = mutableListOf<Pair<String, String>>()
        
        perrosSnapshot.children.forEach { perroSnapshot ->
            // Asegurarse de que es un perro verificando el campo isPerro
            val isPerro = perroSnapshot.child("isPerro").getValue(Boolean::class.java) == true
            
            if (isPerro) {
                val perroId = perroSnapshot.key ?: return@forEach
                val nombre = perroSnapshot.child("nombre").getValue(String::class.java) ?: "Sin nombre"
                
                Log.d("MapsActivity", "Añadiendo perro: $nombre (ID: $perroId)")
                perrosTemp.add(Pair(nombre, perroId))
            }
        }
        
        if (perrosTemp.isEmpty()) {
            Log.d("MapsActivity", "No se encontraron perros válidos para este usuario")
            mostrarMensajeNoPerros()
            return
        }
        
        listaPerros.addAll(perrosTemp)
        
        // Una vez cargados los perros, mostrarlos en el spinner
        mostrarPerrosEnSpinner()
    }
    
    /**
     * Muestra un mensaje cuando el usuario no tiene perros registrados
     */
    private fun mostrarMensajeNoPerros() {
        Toast.makeText(this, "No tienes perros registrados", Toast.LENGTH_LONG).show()
        // Deshabilitar funciones que requieren perros
        spinnerPerros.isEnabled = false
        btnEditarZonaSegura.isEnabled = false
    }

    /**
     * Verifica permisos de ubicación y los solicita si es necesario
     */
    private fun verificarYPedirPermisos() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                requestLocationPermission
            )
        }
    }

    /**
     * Muestra los perros cargados en el spinner y configura su comportamiento
     */
    private fun mostrarPerrosEnSpinner() {
        if (listaPerros.isEmpty()) {
            Log.d("MapsActivity", "No hay perros para mostrar en el spinner")
            return
        }
        
        // Verificar que spinnerPerros esté inicializado
        if (!::spinnerPerros.isInitialized) {
            Log.e("MapsActivity", "spinnerPerros no está inicializado, no se pueden mostrar los perros")
            return
        }
        
        Log.d("MapsActivity", "Mostrando ${listaPerros.size} perros en el spinner")
        
        // Lista para almacenar los perros con sus datos completos (nombre, id, imagen)
        val perrosItems = mutableListOf<Triple<String, String, Bitmap?>>()
        
        // Para cada perro, intentamos obtener su imagen
        listaPerros.forEach { (nombre, id) ->
            Log.d("MapsActivity", "Procesando perro para spinner: $nombre (ID: $id)")
            var dogBitmap: Bitmap? = null
            
            // 1. Intentar obtener imagen desde datos precargados
            val perroSnapshot = DatosPrecargados.obtenerPerro(id)
            
            if (perroSnapshot != null && perroSnapshot.exists()) {
                Log.d("MapsActivity", "Datos del perro encontrados en datos precargados")
                val imagenBase64 = perroSnapshot.child("imagenBase64").getValue(String::class.java)
                
                if (!imagenBase64.isNullOrEmpty()) {
                    try {
                        val imageBytes = Base64.decode(imagenBase64, Base64.DEFAULT)
                        dogBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        Log.d("MapsActivity", "Imagen cargada correctamente")
                    } catch (e: Exception) {
                        Log.e("MapsActivity", "Error decodificando imagen: ${e.message}")
                    }
                }
            } else {
                Log.d("MapsActivity", "Intentando cargar imagen directamente desde Firebase")
                
                try {
                    val task = database.child("users").child(id).child("imagenBase64").get()
                    val snapshot = Tasks.await(task)
                    
                    if (snapshot.exists()) {
                        val imagenBase64 = snapshot.getValue(String::class.java)
                        if (!imagenBase64.isNullOrEmpty()) {
                            try {
                                val imageBytes = Base64.decode(imagenBase64, Base64.DEFAULT)
                                dogBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                Log.d("MapsActivity", "Imagen cargada desde Firebase")
                            } catch (e: Exception) {
                                Log.e("MapsActivity", "Error decodificando imagen desde Firebase: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MapsActivity", "Error cargando imagen desde Firebase: ${e.message}")
                }
            }
            
            // Añadir el perro a la lista de items con o sin imagen
            perrosItems.add(Triple(nombre, id, dogBitmap))
        }
        
        try {
            // Crear el adaptador personalizado con la lista de perros e imágenes
            val adapter = DogSpinnerAdapter(this, perrosItems)
            
            // Configurar el spinner
            spinnerPerros.adapter = adapter
            
            // Si hay perros, seleccionar el primer perro por defecto si no hay uno seleccionado
            if (perrosItems.isNotEmpty() && perroSeleccionadoId == null) {
                perroSeleccionadoId = perrosItems[0].second
                
                // Mostrar la ubicación y zona segura del perro seleccionado inicialmente
                // (sólo si ya tenemos el mapa inicializado)
                if (::locationManager.isInitialized && ::mMap.isInitialized) {
                    locationManager.mostrarUbicacionPerro(perroSeleccionadoId!!)
                    locationManager.mostrarZonaSegura(perroSeleccionadoId!!)
                }
            }
        } catch (e: Exception) {
            Log.e("MapsActivity", "Error configurando el spinner: ${e.message}")
        }
    }

    /**
     * Maneja el resultado de la solicitud de permisos.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestLocationPermission) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                try {
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED && ::mMap.isInitialized
                    ) {
                        mMap.isMyLocationEnabled = true
                        
                        if (::locationManager.isInitialized) {
                            locationManager.startLocationUpdates()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MapsActivity", "Error al habilitar ubicación: ${e.message}")
                }
            }
        }
    }

    /**
     * Detiene las actualizaciones de ubicación cuando la actividad está en pausa.
     */
    override fun onPause() {
        super.onPause()
        if (::locationManager.isInitialized) {
            locationManager.stopLocationUpdates()
        }
    }

    /**
     * Libera recursos cuando la actividad se destruye.
     */
    override fun onDestroy() {
        super.onDestroy()
        if (::locationManager.isInitialized) {
            locationManager.limpiarRecursos()
        }
    }
}


