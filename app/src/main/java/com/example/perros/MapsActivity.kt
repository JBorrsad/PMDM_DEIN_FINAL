package com.example.perros

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ImageView  // Importa la clase ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.perros.databinding.ActivityMapsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val REQUEST_LOCATION_PERMISSION = 1

    // Handler para la actualización periódica
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateLocationsRunnable: Runnable

    // Mapa para almacenar marcadores de usuarios
    private val userMarkers = mutableMapOf<String, Marker>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Obtener el botón desde el XML
        val btnCrearPerro = findViewById<Button>(R.id.btnCrearPerro)

        // Configurar el listener para el botón
        btnCrearPerro.setOnClickListener {
            // Iniciar la actividad CrearPerrosActivity
            val intent = Intent(this, CrearPerrosActivity::class.java)
            startActivity(intent)
        }

        // Configurar el listener para la imagen del perfil
        val ivPerfilUsuario = findViewById<ImageView>(R.id.ivPerfilUsuario)
        ivPerfilUsuario.setOnClickListener {
            // Crear un Intent para iniciar PerfilUsuarioActivity
            val intent = Intent(this, PerfilUsuario::class.java)
            startActivity(intent)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        enableMyLocation()
        startUpdatingOtherUsers()
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
            getDeviceLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        }
    }

    private fun getDeviceLocation() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.lastLocation.addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        val lastKnownLocation = task.result
                        updateLocationInFirebase(lastKnownLocation) // Guardar ubicación en Firebase
                        val latLng = LatLng(lastKnownLocation.latitude, lastKnownLocation.longitude)
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                        userMarkers["me"]?.remove() // Eliminar marcador anterior
                        userMarkers["me"] = mMap.addMarker(MarkerOptions().position(latLng).title("Mi ubicación"))!!
                    } else {
                        Log.d("MapsActivity", "Ubicación actual es nula. Usando valores predeterminados.")
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(-34.0, 151.0), 15f))
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e("MapsActivity", "Error obteniendo ubicación: ${e.message}", e)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                enableMyLocation()
            } else {
                Log.e("Permiso de ubicación", "Permiso denegado por el usuario")
            }
        }
    }

    private fun updateLocationInFirebase(location: Location) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.e("Firebase", "Error: Usuario no autenticado. No se puede guardar ubicación.")
            return
        }

        val database = FirebaseDatabase.getInstance(
            "https://pmdm-dein-default-rtdb.europe-west1.firebasedatabase.app"
        ).getReference("locations").child(user.uid)

        val userLocation = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude
        )

        Log.d("Firebase", "Guardando ubicación en Firebase: Lat(${location.latitude}), Lng(${location.longitude})")

        database.setValue(userLocation)
            .addOnSuccessListener { Log.d("Firebase", "Ubicación guardada correctamente") }
            .addOnFailureListener { e -> Log.e("Firebase", "Error al actualizar ubicación: ${e.message}") }
    }

    private fun startUpdatingOtherUsers() {
        updateLocationsRunnable = object : Runnable {
            override fun run() {
                updateOtherUsersLocations()
                handler.postDelayed(this, 5000) // Actualizar cada 5 segundos
            }
        }
        handler.post(updateLocationsRunnable)
    }

    private fun updateOtherUsersLocations() {
        Log.d("Firebase", "Actualizando ubicaciones de otros usuarios...")
        val database = FirebaseDatabase.getInstance(
            "https://pmdm-dein-default-rtdb.europe-west1.firebasedatabase.app"
        ).getReference("locations")

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (userSnapshot in snapshot.children) {
                    val lat = userSnapshot.child("latitude").getValue(Double::class.java)
                    val lng = userSnapshot.child("longitude").getValue(Double::class.java)
                    val userId = userSnapshot.key

                    if (userId != null && lat != null && lng != null) {
                        val location = LatLng(lat, lng)

                        if (userMarkers.containsKey(userId)) {
                            // Si el marcador ya existe, actualiza su posición
                            userMarkers[userId]?.position = location
                        } else {
                            // Si el marcador no existe, agrégalo al mapa
                            val marker = mMap.addMarker(
                                MarkerOptions().position(location).title("Usuario: $userId")
                            )
                            if (marker != null) {
                                userMarkers[userId] = marker
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error al obtener ubicaciones: ${error.message}")
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateLocationsRunnable)
    }
}
