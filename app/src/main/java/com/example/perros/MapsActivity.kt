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

@Suppress("DEPRECATION")
class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    // Handler y Runnable para verificar periódicamente si el perro está fuera
    private val handler = Handler()
    private val comprobacionZonaSeguraRunnable = object : Runnable {
        override fun run() {
            comprobarYNotificarZonaInsegura() // Notifica cada vez que se verifica
            handler.postDelayed(this, 5000)   // Repetir cada 5 segundos
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

    private fun setupLocationRequest() {
        locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 2000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

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
     * Carga la lista de perros pertenecientes al usuario actual (dueñoId = usuarioId)
     * y rellena el spinner con su nombre e imagen.
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

    // Lee la zona segura en "users/{perroId}/zonaSegura" y dibuja el círculo
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

    // Lee la ubicación del perro en "locations/{perroId}" y actualiza el marcador
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
     * Verifica la distancia del perro cada 5s y, si está fuera, manda notificación
     * (sin filtro para repetir notificaciones).
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                enableMyLocation()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

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
     * Crea un icono circular personalizado para el marcador del perro.
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
