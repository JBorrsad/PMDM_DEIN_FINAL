package com.example.perros

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
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

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

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
        // Especifica la URL de tu base de datos (si es necesario)
        database = FirebaseDatabase.getInstance("https://pmdm-dein-default-rtdb.europe-west1.firebasedatabase.app").reference
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Configuración de elementos de la interfaz
        spinnerPerros = findViewById(R.id.nombreperro)
        btnEditarZonaSegura = findViewById(R.id.btn_home)
        btnEditarPerfilPerro = findViewById(R.id.btnEditarPerro)
        btnPerfilUsuario = findViewById(R.id.ivPerfilUsuario)

        // Cargar la lista de perros asociados al usuario
        cargarPerrosUsuario()

        // Evento al seleccionar un perro
        spinnerPerros.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                perroSeleccionadoId = listaPerros[position].second // Se guarda el ID del perro seleccionado
                Log.d("MapsActivity", "Perro seleccionado: ${listaPerros[position].first} (ID: $perroSeleccionadoId)")
                mostrarZonaSegura()
                mostrarUbicacionPerro() // Se adjunta el listener para la ubicación del perro
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Evento para editar la zona segura
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

        // Evento para editar el perfil del perro
        btnEditarPerfilPerro.setOnClickListener {
            if (perroSeleccionadoId.isNullOrEmpty()) {
                Toast.makeText(this, "Selecciona un perro primero", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, EditarPerro::class.java)
                intent.putExtra("perroId", perroSeleccionadoId)
                startActivity(intent)
            }
        }

        // Evento para abrir el perfil del usuario
        btnPerfilUsuario.setOnClickListener {
            val intent = Intent(this, PerfilUsuario::class.java)
            startActivity(intent)
        }

        // Inicializar el mapa
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Configurar solicitud de ubicación para el usuario (owner)
        locationRequest = LocationRequest.create().apply {
            interval = 5000             // Actualización cada 5 segundos
            fastestInterval = 2000        // Intervalo mínimo de 2 segundos
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                // Recorrer cada ubicación recibida y actualizar Firebase
                for (location in locationResult.locations) {
                    updateOwnerLocationInFirebase(location)
                }
            }
        }

    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        enableMyLocation()

        // Escucha y muestra la ubicación actual del usuario (owner)
        listenForOwnerLocation()

        // Permitir seleccionar nueva zona segura en modo edición
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

    // Actualiza la ubicación del usuario (owner) en Firebase (se escribe en "locations/ownerId")
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

    // Escucha la ubicación del usuario (owner) en Firebase y actualiza su marcador
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

    // Cargar la lista de perros asociados (donde "dueñoId" es el UID del usuario logueado)
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
                        // Selecciona el primer perro por defecto
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

    // Muestra la zona segura del perro (según datos en "users/perroId/zonaSegura")
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

    // Escucha la ubicación en tiempo real del perro seleccionado (desde "locations/perroId")
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
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error al obtener ubicación del perro: ${error.message}")
            }
        }
        dogRef.addValueEventListener(dogLocationListener as ValueEventListener)
    }

    // Elimina el listener de ubicación del perro si existe
    private fun eliminarDogLocationListener() {
        if (perroSeleccionadoId != null && dogLocationListener != null) {
            database.child("locations").child(perroSeleccionadoId!!).removeEventListener(dogLocationListener as ValueEventListener)
            dogLocationListener = null
        }
    }

    // Guarda la zona segura del perro en Firebase (en "users/perroId/zonaSegura")
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

    // Activa el modo de edición para definir una nueva zona segura
    private fun activarModoEdicionZonaSegura() {
        if (perroSeleccionadoId.isNullOrEmpty()) {
            Toast.makeText(this, "Selecciona un perro primero", Toast.LENGTH_SHORT).show()
            return
        }
        modoEdicionZonaSegura = true
        btnEditarZonaSegura.text = "Guardar"
        Toast.makeText(this, "Toca en el mapa para definir la nueva zona segura", Toast.LENGTH_LONG).show()
    }

    // Permite definir la zona segura al tocar el mapa
    private fun definirZonaSegura(latLng: LatLng) {
        val radio = 100.0 // Radio por defecto
        zonaSeguraCircle?.remove()
        zonaSeguraCircle = mMap.addCircle(
            CircleOptions()
                .center(latLng)
                .radius(radio)
                .strokeColor(Color.GREEN)
                .fillColor(Color.argb(50, 0, 255, 0))
        )
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
    }
}
