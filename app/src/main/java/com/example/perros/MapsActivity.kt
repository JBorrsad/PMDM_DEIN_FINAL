package com.example.perros

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError

/**
 * Actividad principal del mapa que gestiona el monitoreo y seguimiento de perros.
 *
 * Esta actividad proporciona una interfaz completa para:
 * - Visualizar la ubicación en tiempo real de los perros
 * - Gestionar zonas seguras (geofencing) para cada perro
 * - Monitorear cuando un perro sale de su zona segura
 * - Cambiar entre diferentes perros registrados
 * - Acceder a perfiles de perros y usuarios
 *
 * Estructura de datos en Firebase:
 * ```
 * locations/
 *   ├── {userId}/          # Ubicación del dueño
 *   │     ├── latitude: Double
 *   │     └── longitude: Double
 *   └── {perroId}/         # Ubicación del perro
 *         ├── latitude: Double
 *         └── longitude: Double
 *
 * users/
 *   └── {perroId}/
 *         ├── nombre: String
 *         ├── imagenBase64: String?
 *         └── zonaSegura/
 *               ├── latitud: Double
 *               ├── longitud: Double
 *               └── radio: Int
 * ```
 *
 * @property mMap Instancia del mapa de Google
 * @property database Referencia a Firebase Realtime Database
 * @property auth Instancia de Firebase Authentication
 * @property listaPerros Lista de perros asociados al usuario actual
 * @property perroSeleccionadoId ID del perro actualmente seleccionado
 * @property handler Manejador para verificaciones periódicas de zona segura
 *
 * @see DogItem clase que representa los datos de un perro
 * @see ActivityMonitor para el registro de actividad física
 */
@Suppress("DEPRECATION")
class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    /**
     * Handler y Runnable para verificar periódicamente si el perro está fuera de la zona segura.
     * Se ejecuta cada 5 segundos y envía notificaciones si el perro está fuera de la zona.
     */
    private val handler = Handler()
    private val comprobacionZonaSeguraRunnable = object : Runnable {
        override fun run() {
            comprobarYNotificarZonaInsegura()
            handler.postDelayed(this, 5000)
        }
    }

    private lateinit var mMap: GoogleMap
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth

    private lateinit var spinnerPerros: Spinner
    private lateinit var btnEditarZonaSegura: LinearLayout
    private lateinit var btnPerfilPerro: LinearLayout
    private lateinit var btnPerfilUsuario: ShapeableImageView
    private lateinit var btnZonaText: TextView

    // Lista de perros: par (nombreDelPerro, idDelPerro)
    private var listaPerros = mutableListOf<Pair<String, String>>()
    private var perroSeleccionadoId: String? = null

    private var modoEdicionZonaSegura = false
    private var zonaSeguraCircle: Circle? = null
    private var dogMarker: Marker? = null

    private var ownerLocationListener: ValueEventListener? = null
    private var dogLocationListener: ValueEventListener? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private val REQUEST_LOCATION_PERMISSION = 1

    // Variables para la zona segura
    private var zonaCentroLat: Double? = null
    private var zonaCentroLng: Double? = null
    private var zonaRadio: Double = 0.0

    /**
     * Inicializa y configura todos los componentes necesarios de la actividad.
     *
     * Realiza las siguientes tareas:
     * - Inicializa Firebase Auth y Database
     * - Configura el cliente de ubicación
     * - Inicializa las vistas y listeners
     * - Carga la lista de perros del usuario
     *
     * @param savedInstanceState Estado guardado de la actividad
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        initializeViews()
        setupLocationRequest()
        setupLocationCallback()
        setupMapFragment()
        setupButtonListeners()
        cargarPerrosUsuario()  // Carga los perros de este dueño y rellena el spinner
    }

    private fun initializeViews() {
        spinnerPerros = findViewById(R.id.nombreperro)
        btnEditarZonaSegura = findViewById(R.id.btn_home)
        btnPerfilPerro = findViewById(R.id.btnPerfilPerro)
        btnPerfilUsuario = findViewById(R.id.ivPerfilUsuario)
        btnZonaText = findViewById(R.id.btnZonaText)

        setupSpinnerListener()
    }

    private fun setupSpinnerListener() {
        spinnerPerros.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                perroSeleccionadoId = listaPerros[position].second
                Log.d("MapsActivity", "Perro seleccionado: ${listaPerros[position].first} (ID: $perroSeleccionadoId)")

                // Lee la zona segura de este perro y dibuja el círculo
                mostrarZonaSegura()
                // Lee la ubicación del perro en "locations/{perroId}" y muestra el marker
                mostrarUbicacionPerro()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /**
     * Configura el cliente de ubicación.
     *
     * Establece los parámetros para la actualización de ubicación:
     * - Intervalo de actualización: 5 segundos
     * - Intervalo más rápido: 2 segundos
     * - Prioridad: Alta precisión
     */
    private fun setupLocationRequest() {
        locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 2000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    /**
     * Configura el callback para recibir actualizaciones de ubicación.
     *
     * Cuando se recibe una nueva ubicación:
     * - Se actualiza la posición del dueño en Firebase
     * - Se mantiene un registro de la última ubicación conocida
     */
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    updateOwnerLocationInFirebase(location)
                }
            }
        }
    }

    private fun setupMapFragment() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupButtonListeners() {
        btnEditarZonaSegura.setOnClickListener {
            if (perroSeleccionadoId.isNullOrEmpty()) {
                Toast.makeText(this, "Selecciona un perro primero", Toast.LENGTH_SHORT).show()
            } else {
                if (modoEdicionZonaSegura) {
                    guardarZonaSegura()
                } else {
                    activarModoEdicionZonaSegura()
                }
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
    }

    /**
     * Configura el mapa cuando está listo para ser usado.
     * Habilita la ubicación del usuario y configura los listeners necesarios.
     *
     * @param googleMap Instancia del mapa de Google
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        enableMyLocation()
        listenForOwnerLocation()

        mMap.setOnMapClickListener { latLng ->
            if (modoEdicionZonaSegura && !perroSeleccionadoId.isNullOrEmpty()) {
                definirZonaSegura(latLng)
            }
        }
    }

    /**
     * Habilita la ubicación en tiempo real en el mapa.
     *
     * Este método:
     * - Verifica los permisos de ubicación necesarios
     * - Activa el botón "Mi Ubicación" en el mapa
     * - Inicia las actualizaciones de ubicación si los permisos están concedidos
     */
    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
            startLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        }
    }

    /**
     * Inicia las actualizaciones periódicas de ubicación.
     *
     * Solo se ejecuta si los permisos necesarios están concedidos:
     * - ACCESS_FINE_LOCATION
     * - ACCESS_COARSE_LOCATION
     */
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }

    private fun updateOwnerLocationInFirebase(location: Location) {
        val user = auth.currentUser ?: run {
            Log.e("Firebase", "Usuario no autenticado")
            return
        }
        val ownerRef = database.child("locations").child(user.uid)
        val locData = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude
        )
        ownerRef.setValue(locData)
            .addOnSuccessListener { Log.d("Firebase", "Ubicación del owner actualizada") }
            .addOnFailureListener { e -> Log.e("Firebase", "Error al actualizar ubicación del owner: ${e.message}") }
    }

    private var firstLocationUpdate = true

    private fun listenForOwnerLocation() {
        val user = auth.currentUser ?: return
        val ownerRef = database.child("locations").child(user.uid)
        ownerLocationListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("latitude").getValue(Double::class.java)
                val lng = snapshot.child("longitude").getValue(Double::class.java)
                if (lat != null && lng != null) {
                    val pos = LatLng(lat, lng)
                    if (firstLocationUpdate) {
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f))
                        firstLocationUpdate = false
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error al escuchar ubicación del owner: ${error.message}")
            }
        }
        ownerRef.addValueEventListener(ownerLocationListener as ValueEventListener)
    }

    /**
     * Carga y muestra la lista de perros asociados al usuario actual.
     *
     * Este método:
     * - Consulta Firebase para obtener los perros del usuario
     * - Filtra solo las entradas marcadas como perros (isPerro = true)
     * - Actualiza el spinner con la lista de perros
     * - Carga las imágenes de perfil de los perros
     * - Configura el perro seleccionado por defecto
     */
    private fun cargarPerrosUsuario() {
        val usuarioId = auth.currentUser?.uid ?: return
        // Busca en "users" aquellos con "dueñoId = usuarioId" e "isPerro = true"
        database.child("users")
            .orderByChild("dueñoId")
            .equalTo(usuarioId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    listaPerros.clear()
                    val dogItems = mutableListOf<DogItem>()

                    for (perroSnapshot in snapshot.children) {
                        // Solo si isPerro = true
                        val esPerro = perroSnapshot.child("isPerro").getValue(Boolean::class.java) == true
                        if (!esPerro) continue

                        val nombre = perroSnapshot.child("nombre").getValue(String::class.java) ?: "Sin Nombre"
                        val id = perroSnapshot.key ?: continue
                        val imageBase64 = perroSnapshot.child("imagenBase64").getValue(String::class.java)

                        listaPerros.add(Pair(nombre, id))
                        dogItems.add(DogItem(id, nombre, imageBase64))
                    }

                    if (dogItems.isNotEmpty()) {
                        val adapter = DogSpinnerAdapter(this@MapsActivity, dogItems)
                        spinnerPerros.adapter = adapter
                        // Seleccionar el primero por defecto
                        perroSeleccionadoId = dogItems.first().id
                        mostrarZonaSegura()
                        mostrarUbicacionPerro()
                    } else {
                        Toast.makeText(this@MapsActivity, "No hay perros asociados a este usuario", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "Error al cargar perros: ${error.message}")
                }
            })

        // Carga la imagen del usuario (dueño)
        database.child("users").child(usuarioId).child("imagenBase64")
            .get().addOnSuccessListener { snapshot ->
                val imageBase64 = snapshot.getValue(String::class.java)
                if (!imageBase64.isNullOrEmpty()) {
                    try {
                        val imageBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        btnPerfilUsuario.setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        btnPerfilUsuario.setImageResource(R.drawable.img)
                    }
                } else {
                    btnPerfilUsuario.setImageResource(R.drawable.img)
                }
            }.addOnFailureListener {
                btnPerfilUsuario.setImageResource(R.drawable.img)
            }
    }

    /**
     * Muestra y actualiza la zona segura del perro seleccionado.
     *
     * Dibuja un círculo en el mapa que representa:
     * - Centro de la zona segura
     * - Radio de la zona permitida
     * - Color según si el perro está dentro (verde) o fuera (rojo)
     *
     * Los datos de la zona se obtienen de Firebase:
     * users/{perroId}/zonaSegura/{latitud,longitud,radio}
     */
    private fun mostrarZonaSegura() {
        if (perroSeleccionadoId.isNullOrEmpty()) return
        database.child("users").child(perroSeleccionadoId!!).child("zonaSegura")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val lat = snapshot.child("latitud").getValue(Double::class.java)
                    val lng = snapshot.child("longitud").getValue(Double::class.java)
                    val radio = snapshot.child("radio").getValue(Int::class.java)

                    zonaSeguraCircle?.remove()
                    if (lat != null && lng != null && radio != null) {
                        zonaCentroLat = lat
                        zonaCentroLng = lng
                        zonaRadio = radio.toDouble()
                        val pos = LatLng(lat, lng)
                        zonaSeguraCircle = mMap.addCircle(
                            CircleOptions()
                                .center(pos)
                                .radius(zonaRadio)
                                .strokeColor(Color.RED)
                                .fillColor(Color.argb(50, 255, 0, 0))
                        )
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /**
     * Actualiza la ubicación del perro en tiempo real y su representación en el mapa.
     *
     * Este método:
     * - Escucha cambios en la ubicación del perro en Firebase
     * - Actualiza el marcador en el mapa con la nueva posición
     * - Carga y aplica la imagen del perro al marcador
     * - Verifica si el perro está dentro de su zona segura
     *
     * @see createCustomMarker para la creación del marcador personalizado
     * @see actualizarColorZonaSegura para la verificación de la zona
     */
    private fun mostrarUbicacionPerro() {
        if (perroSeleccionadoId.isNullOrEmpty()) return
        eliminarDogLocationListener()

        val dogRef = database.child("locations").child(perroSeleccionadoId!!)
        dogLocationListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("latitude").getValue(Double::class.java)
                val lng = snapshot.child("longitude").getValue(Double::class.java)
                if (lat != null && lng != null) {
                    val pos = LatLng(lat, lng)

                    // Cargar la imagen del perro para el marker
                    database.child("users").child(perroSeleccionadoId!!)
                        .child("imagenBase64")
                        .get()
                        .addOnSuccessListener { snapImg ->
                            val imageBase64 = snapImg.getValue(String::class.java)
                            if (!imageBase64.isNullOrEmpty()) {
                                val imageBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                val dogIcon = createCustomMarker(this@MapsActivity, bitmap)

                                if (dogMarker == null) {
                                    dogMarker = mMap.addMarker(
                                        MarkerOptions()
                                            .position(pos)
                                            .title("Ubicación de ${listaPerros.find { it.second == perroSeleccionadoId }?.first}")
                                            .icon(dogIcon)
                                    )
                                } else {
                                    dogMarker!!.setIcon(dogIcon)
                                    dogMarker!!.position = pos
                                }
                            } else {
                                // Si no tiene imagen, marcador por defecto
                                if (dogMarker == null) {
                                    dogMarker = mMap.addMarker(
                                        MarkerOptions()
                                            .position(pos)
                                            .title("Ubicación de ${listaPerros.find { it.second == perroSeleccionadoId }?.first}")
                                    )
                                } else {
                                    dogMarker!!.position = pos
                                }
                            }
                            // Cada vez que actualizamos la posición del perro, ajustamos color de la zona
                            actualizarColorZonaSegura()
                        }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error al obtener ubicación del perro: ${error.message}")
            }
        }
        dogRef.addValueEventListener(dogLocationListener as ValueEventListener)
    }

    private fun eliminarDogLocationListener() {
        if (perroSeleccionadoId != null && dogLocationListener != null) {
            database.child("locations").child(perroSeleccionadoId!!)
                .removeEventListener(dogLocationListener as ValueEventListener)
            dogLocationListener = null
        }
    }

    /**
     * Guarda la zona segura definida para el perro actual en Firebase.
     * Actualiza la interfaz de usuario después de guardar.
     */
    @SuppressLint("SetTextI18n")
    private fun guardarZonaSegura() {
        if (perroSeleccionadoId.isNullOrEmpty() || zonaSeguraCircle == null) {
            Toast.makeText(this, "Define la zona segura antes de guardar", Toast.LENGTH_SHORT).show()
            return
        }
        val zonaSegura = mapOf(
            "latitud" to zonaSeguraCircle!!.center.latitude,
            "longitud" to zonaSeguraCircle!!.center.longitude,
            "radio" to zonaSeguraCircle!!.radius.toInt()
        )
        database.child("users").child(perroSeleccionadoId!!).child("zonaSegura")
            .setValue(zonaSegura)
            .addOnSuccessListener {
                Toast.makeText(this, "Zona segura guardada", Toast.LENGTH_SHORT).show()
                btnZonaText.text = "Editar Zona Segura"
                modoEdicionZonaSegura = false
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al guardar zona segura", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Activa el modo de edición de zona segura para el perro seleccionado.
     *
     * Este método:
     * - Verifica que haya un perro seleccionado
     * - Cambia la interfaz al modo de edición
     * - Centra el mapa en la zona segura actual si existe
     * - Prepara el mapa para recibir clicks y definir la nueva zona
     */
    private fun activarModoEdicionZonaSegura() {
        if (perroSeleccionadoId.isNullOrEmpty()) {
            Toast.makeText(this, "Selecciona un perro primero", Toast.LENGTH_SHORT).show()
            return
        }
        modoEdicionZonaSegura = true
        btnZonaText.text = "Guardar"
        Toast.makeText(this, "Toca en el mapa para definir la nueva zona segura", Toast.LENGTH_LONG).show()

        // Acercamos la cámara a la zona actual (si existe)
        database.child("users").child(perroSeleccionadoId!!).child("zonaSegura")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val lat = snapshot.child("latitud").getValue(Double::class.java)
                    val lng = snapshot.child("longitud").getValue(Double::class.java)
                    if (lat != null && lng != null) {
                        val pos = LatLng(lat, lng)
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f))
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /**
     * Define una nueva zona segura en la ubicación especificada.
     *
     * Crea un círculo en el mapa con:
     * - Centro en la posición tocada
     * - Radio fijo de 100 metros
     * - Color verde para indicar que es una zona nueva
     *
     * @param latLng Coordenadas del centro de la nueva zona segura
     */
    private fun definirZonaSegura(latLng: LatLng) {
        val radio = 100.0
        zonaSeguraCircle?.remove()
        zonaCentroLat = latLng.latitude
        zonaCentroLng = latLng.longitude
        zonaRadio = radio
        zonaSeguraCircle = mMap.addCircle(
            CircleOptions()
                .center(latLng)
                .radius(radio)
                .strokeColor(Color.GREEN)
                .fillColor(Color.argb(50, 0, 255, 0))
        )
    }

    /**
     * Cambia el color de la zona segura (verde si el perro está dentro, rojo si está fuera),
     * usando la ubicación local (dogMarker) y los datos (zonaCentroLat, zonaCentroLng, zonaRadio).
     */
    private fun actualizarColorZonaSegura() {
        if (perroSeleccionadoId.isNullOrEmpty() ||
            zonaSeguraCircle == null || dogMarker == null ||
            zonaCentroLat == null || zonaCentroLng == null || zonaRadio == 0.0
        ) return

        val distancia = FloatArray(1)
        Location.distanceBetween(
            zonaCentroLat!!, zonaCentroLng!!,
            dogMarker!!.position.latitude, dogMarker!!.position.longitude,
            distancia
        )

        val dentroZona = distancia[0] <= zonaRadio
        if (dentroZona) {
            zonaSeguraCircle!!.fillColor = Color.argb(50, 0, 255, 0)
            zonaSeguraCircle!!.strokeColor = Color.GREEN
        } else {
            zonaSeguraCircle!!.fillColor = Color.argb(50, 255, 0, 0)
            zonaSeguraCircle!!.strokeColor = Color.RED
        }
        // Iniciamos el Runnable para comprobar si está fuera
        handler.post(comprobacionZonaSeguraRunnable)
    }

    /**
     * Verifica si el perro está fuera de la zona segura y envía notificaciones.
     * Se ejecuta periódicamente a través del handler.
     */
    private fun comprobarYNotificarZonaInsegura() {
        if (zonaSeguraCircle == null || dogMarker == null ||
            zonaCentroLat == null || zonaCentroLng == null || zonaRadio == 0.0
        ) return

        val distancia = FloatArray(1)
        Location.distanceBetween(
            zonaCentroLat!!, zonaCentroLng!!,
            dogMarker!!.position.latitude, dogMarker!!.position.longitude,
            distancia
        )

        if (distancia[0] > zonaRadio) {
            enviarNotificacionZonaInsegura()
        }
    }

    /**
     * Envía una notificación indicando que el perro (por nombre) está fuera de la zona.
     */
    private fun enviarNotificacionZonaInsegura() {
        val channelId = "geofence_alert"
        val notificationId = 1001

        val dogName = listaPerros.find { it.second == perroSeleccionadoId }?.first ?: "Tu perro"

        val intent = Intent(this, MapsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⚠ Alerta de Zona Segura")
            .setContentText("¡$dogName ha salido de la zona segura!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crear el canal si es Android 8.0 o superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Alertas de Geocerca",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Verificar permiso de notificaciones en Android 13+
        with(NotificationManagerCompat.from(this)) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(notificationId, builder.build())
            }
        }
    }

    /**
     * Maneja el resultado de la solicitud de permisos de ubicación.
     *
     * Si los permisos son concedidos, habilita la funcionalidad de ubicación.
     * 
     * @param requestCode Código de la solicitud de permisos
     * @param permissions Array de permisos solicitados
     * @param grantResults Resultados de la solicitud de permisos
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                enableMyLocation()
            }
        }
    }

    /**
     * Se llama cuando la actividad entra en estado de pausa.
     *
     * Detiene las actualizaciones de ubicación para conservar batería
     * cuando la aplicación no está en primer plano.
     */
    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    /**
     * Se llama cuando la actividad está siendo destruida.
     *
     * Realiza limpieza de recursos:
     * - Elimina los listeners de ubicación
     * - Detiene el handler de comprobación de zona segura
     * - Limpia las referencias a Firebase
     */
    override fun onDestroy() {
        super.onDestroy()
        eliminarDogLocationListener()
        if (ownerLocationListener != null) {
            val user = auth.currentUser
            if (user != null) {
                database.child("locations").child(user.uid)
                    .removeEventListener(ownerLocationListener as ValueEventListener)
            }
        }
        handler.removeCallbacks(comprobacionZonaSeguraRunnable)
    }

    /**
     * Crea un marcador personalizado para representar al perro en el mapa.
     *
     * Características del marcador:
     * - Forma circular con la imagen del perro
     * - Efecto de sombra para mejor visibilidad
     * - Tamaño optimizado para la visualización en el mapa
     *
     * @param context Contexto de la aplicación
     * @param bitmap Imagen del perro a usar en el marcador
     * @return BitmapDescriptor con el icono personalizado
     */
    private fun createCustomMarker(context: Context, bitmap: Bitmap): BitmapDescriptor {
        val markerSize = 150
        val shadowSize = 30

        val bmp = Bitmap.createBitmap(markerSize + shadowSize * 2, markerSize + shadowSize * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Sombra
        val shadowPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            maskFilter = android.graphics.BlurMaskFilter(20f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            alpha = 100
        }
        val shadowRadius = (markerSize / 2) + shadowSize
        canvas.drawCircle(shadowRadius.toFloat(), shadowRadius.toFloat(), (markerSize / 2).toFloat(), shadowPaint)

        // Dibuja la forma del marcador
        val markerDrawable = ContextCompat.getDrawable(context, R.drawable.custom_marker1)!!
        markerDrawable.setBounds(shadowSize, shadowSize, markerSize + shadowSize, markerSize + shadowSize)
        markerDrawable.draw(canvas)

        // Imagen circular
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 100, 100, false)
        val circularBitmap = getCircularBitmap(resizedBitmap, 100)
        val paint = Paint().apply { isAntiAlias = true }

        val imageOffsetX = shadowSize + ((markerSize - 100) / 2)
        val imageOffsetY = shadowSize + ((markerSize - 100) / 2) - 10

        canvas.drawBitmap(circularBitmap, imageOffsetX.toFloat(), imageOffsetY.toFloat(), paint)

        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    private fun getCircularBitmap(bitmap: Bitmap, size: Int): Bitmap {
        val output = createBitmap(size, size)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)
        val rectF = RectF(rect)

        canvas.drawOval(rectF, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, null, rect, paint)

        return output
    }
}
