package com.example.perros

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.Base64
import androidx.core.graphics.createBitmap
import com.google.android.material.imageview.ShapeableImageView

@Suppress("DEPRECATION")
class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private val handler = android.os.Handler()
    private val comprobacionZonaSeguraRunnable = object : Runnable {
        override fun run() {
            comprobarYNotificarZonaSegura()
            handler.postDelayed(this, 5000) // Repetir cada 5 segundos
        }
    }

    private lateinit var mMap: GoogleMap
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth

    private lateinit var spinnerPerros: Spinner
    private lateinit var btnEditarZonaSegura: LinearLayout
    private lateinit var btnEditarPerfilPerro: LinearLayout
    private lateinit var btnPerfilUsuario: ShapeableImageView
    private lateinit var btnZonaText: TextView

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
        cargarPerrosUsuario()
    }

    private fun initializeViews() {
        spinnerPerros = findViewById(R.id.nombreperro)
        btnEditarZonaSegura = findViewById(R.id.btn_home)
        btnEditarPerfilPerro = findViewById(R.id.btnEditarPerro)
        btnPerfilUsuario = findViewById(R.id.ivPerfilUsuario)
        btnZonaText = findViewById(R.id.btnZonaText)

        setupSpinnerListener()
    }

    private fun setupSpinnerListener() {
        spinnerPerros.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                perroSeleccionadoId = listaPerros[position].second
                Log.d("MapsActivity", "Perro seleccionado: ${listaPerros[position].first} (ID: $perroSeleccionadoId)")
                mostrarZonaSegura()
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

        btnEditarPerfilPerro.setOnClickListener {
            if (perroSeleccionadoId.isNullOrEmpty()) {
                Toast.makeText(this, "Selecciona un perro primero", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, EditarPerro::class.java)
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

    private fun cargarPerrosUsuario() {
        val usuarioId = auth.currentUser?.uid ?: return
        database.child("users").orderByChild("dueñoId").equalTo(usuarioId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    listaPerros.clear()
                    val dogItems = mutableListOf<DogItem>()

                    for (perroSnapshot in snapshot.children) {
                        val nombre = perroSnapshot.child("nombre").getValue(String::class.java) ?: "Sin Nombre"
                        val id = perroSnapshot.key ?: continue
                        val imageBase64 = perroSnapshot.child("imagenBase64").getValue(String::class.java)

                        listaPerros.add(Pair(nombre, id))
                        dogItems.add(DogItem(id, nombre, imageBase64))
                    }

                    if (dogItems.isNotEmpty()) {
                        val adapter = DogSpinnerAdapter(this@MapsActivity, dogItems)
                        spinnerPerros.adapter = adapter
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

        // Cargar imagen del usuario
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
                        val pos = LatLng(lat, lng)
                        zonaSeguraCircle = mMap.addCircle(
                            CircleOptions()
                                .center(pos)
                                .radius(radio.toDouble())
                                .strokeColor(Color.RED)
                                .fillColor(Color.argb(50, 255, 0, 0))
                        )
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

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

                    val dogImageRef = database.child("users").child(perroSeleccionadoId!!).child("imagenBase64")
                    dogImageRef.get().addOnSuccessListener { snapshot ->
                        val imageBase64 = snapshot.getValue(String::class.java)
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
                        }
                    }
                    actualizarColorZonaSegura()
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
            database.child("locations").child(perroSeleccionadoId!!).removeEventListener(dogLocationListener as ValueEventListener)
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
        zonaSeguraCircle = mMap.addCircle(
            CircleOptions()
                .center(latLng)
                .radius(radio)
                .strokeColor(Color.GREEN)
                .fillColor(Color.argb(50, 0, 255, 0))
        )
    }

    private fun actualizarColorZonaSegura() {
        if (perroSeleccionadoId.isNullOrEmpty() || zonaSeguraCircle == null || dogMarker == null) return

        val zonaCentro = zonaSeguraCircle!!.center
        val zonaRadio = zonaSeguraCircle!!.radius
        val posicionPerro = dogMarker!!.position
        val distancia = FloatArray(1)

        Location.distanceBetween(
            zonaCentro.latitude, zonaCentro.longitude,
            posicionPerro.latitude, posicionPerro.longitude,
            distancia
        )

        val dentroZonaSegura = distancia[0] <= zonaRadio
        val color = if (dentroZonaSegura) Color.argb(50, 0, 255, 0) else Color.argb(50, 255, 0, 0)
        zonaSeguraCircle!!.fillColor = color

        handler.post(comprobacionZonaSeguraRunnable)
    }

    private fun comprobarYNotificarZonaSegura() {
        if (zonaSeguraCircle == null) return
        val perroFuera = zonaSeguraCircle!!.fillColor == Color.argb(50, 255, 0, 0)
        if (perroFuera) {
            enviarNotificacionZonaInsegura()
        }
    }

    private fun enviarNotificacionZonaInsegura() {
        val channelId = "geofence_alert"
        val notificationId = 1001

        val intent = Intent(this, MapsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⚠ Alerta de Zona Segura")
            .setContentText("¡Tu perro ha salido de la zona segura!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Alertas de Geocerca",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

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
                database.child("locations").child(user.uid).removeEventListener(ownerLocationListener as ValueEventListener)
            }
        }
        handler.removeCallbacks(comprobacionZonaSeguraRunnable)
    }

    private fun createCustomMarker(context: Context, bitmap: Bitmap): BitmapDescriptor {
        val markerSize = 150
        val shadowSize = 30

        val bmp = Bitmap.createBitmap(markerSize + shadowSize * 2, markerSize + shadowSize * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val shadowPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            maskFilter = android.graphics.BlurMaskFilter(20f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            alpha = 100
        }
        val shadowRadius = (markerSize / 2) + shadowSize
        canvas.drawCircle(shadowRadius.toFloat(), shadowRadius.toFloat(), (markerSize / 2).toFloat(), shadowPaint)

        val markerDrawable = ContextCompat.getDrawable(context, R.drawable.custom_marker1)!!
        markerDrawable.setBounds(shadowSize, shadowSize, markerSize + shadowSize, markerSize + shadowSize)
        markerDrawable.draw(canvas)

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