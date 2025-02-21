package com.example.perros

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
    private lateinit var btnEditarZonaSegura: Button
    private lateinit var btnEditarPerfilPerro: Button
    private lateinit var btnPerfilUsuario: ImageView

    // Lista de perros: Pair(nombre, id)
    private var listaPerros = mutableListOf<Pair<String, String>>()
    private var perroSeleccionadoId: String? = null

    private var modoEdicionZonaSegura = false
    private var zonaSeguraCircle: Circle? = null

    // Marcadores para el usuario (owner) y el perro
    private var ownerMarker: Marker? = null
    private var dogMarker: Marker? = null

    // Listeners para las ubicaciones
    private var ownerLocationListener: ValueEventListener? = null
    private var dogLocationListener: ValueEventListener? = null

    // Para actualizar la ubicación del usuario (owner)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private val REQUEST_LOCATION_PERMISSION = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance("https://pmdm-dein-default-rtdb.europe-west1.firebasedatabase.app").reference
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                enableMyLocation()
            } else {
                Log.e("Permiso", "Permiso de ubicación denegado")
            }
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
        Log.d("Firebase", "Guardando ubicación del owner: Lat(${location.latitude}), Lng(${location.longitude})")
        ownerRef.setValue(locData)
            .addOnSuccessListener { Log.d("Firebase", "Ubicación del owner actualizada") }
            .addOnFailureListener { e -> Log.e("Firebase", "Error al actualizar ubicación del owner: ${e.message}") }
    }

    private fun listenForOwnerLocation() {
        val user = auth.currentUser ?: return
        val ownerRef = database.child("locations").child(user.uid)
        ownerLocationListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("latitude").getValue(Double::class.java)
                val lng = snapshot.child("longitude").getValue(Double::class.java)
                if (lat != null && lng != null) {
                    val pos = LatLng(lat, lng)
                    if (ownerMarker == null) {
                        ownerMarker = mMap.addMarker(
                            MarkerOptions()
                                .position(pos)
                                .title("Tu ubicación")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        )
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f))
                    } else {
                        ownerMarker!!.position = pos
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
                    for (perroSnapshot in snapshot.children) {
                        val nombre = perroSnapshot.child("nombre").getValue(String::class.java) ?: "Sin Nombre"
                        val id = perroSnapshot.key ?: continue
                        listaPerros.add(Pair(nombre, id))
                    }
                    if (listaPerros.isNotEmpty()) {
                        val adapter = ArrayAdapter(
                            this@MapsActivity,
                            android.R.layout.simple_spinner_item,
                            listaPerros.map { it.first }
                        )
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        spinnerPerros.adapter = adapter
                        perroSeleccionadoId = listaPerros.first().second
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
                    Log.d("MapsActivity", "Ubicación del perro actualizada: Lat($lat), Lng($lng)")
                    if (dogMarker == null) {
                        dogMarker = mMap.addMarker(
                            MarkerOptions()
                                .position(pos)
                                .title("Ubicación de ${listaPerros.find { it.second == perroSeleccionadoId }?.first}")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                        )
                    } else {
                        dogMarker!!.position = pos
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
                btnEditarZonaSegura.text = "Editar Zona Segura"
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
        btnEditarZonaSegura.text = "Guardar"
        Toast.makeText(this, "Toca en el mapa para definir la nueva zona segura", Toast.LENGTH_LONG).show()
    }

    private fun definirZonaSegura(latLng: LatLng) {
        val radio = 100.0 // Radio por defecto en metros
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

        Log.d("MapsActivity", "Zona segura actualizada: ${if (dentroZonaSegura) "DENTRO" else "FUERA"}")
        handler.post(comprobacionZonaSeguraRunnable)
    }

    private fun comprobarYNotificarZonaSegura() {
        if (zonaSeguraCircle == null) return

        val perroFuera = zonaSeguraCircle!!.fillColor == Color.argb(50, 255, 0, 0)

        if (perroFuera) {
            enviarNotificacionZonaInsegura() // Enviar notificación cada 5 segundos mientras el perro esté fuera
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
                Log.d("Notificación", "Notificación enviada: ¡El perro está fuera de la zona segura!")
            } else {
                Log.e("Notificación", "Permiso de notificación denegado")
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
}