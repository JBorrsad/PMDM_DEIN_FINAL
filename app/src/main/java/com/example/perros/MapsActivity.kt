package com.example.perros

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
    private var perroSeleccionadoId: String? = null
    private var listaPerros = mutableListOf<Pair<String, String>>() // Lista (Nombre, ID)
    private var modoEdicionZonaSegura = false
    private var zonaSeguraCircle: Circle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        // Configuración de elementos de la interfaz
        spinnerPerros = findViewById(R.id.nombreperro)
        btnEditarZonaSegura = findViewById(R.id.btn_home)
        btnEditarPerfilPerro = findViewById(R.id.btnEditarPerro)
        btnPerfilUsuario = findViewById(R.id.ivPerfilUsuario)

        // Cargar perros del usuario en el Spinner
        cargarPerrosUsuario()

        // Evento al seleccionar un perro
        spinnerPerros.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                perroSeleccionadoId = listaPerros[position].second // Se guarda el ID
                Log.d("MapsActivity", "Perro seleccionado: ${listaPerros[position].first} (ID: $perroSeleccionadoId)")
                mostrarZonaSegura()
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

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        enableMyLocation()

        // Permitir seleccionar nueva zona segura cuando está en modo edición
        mMap.setOnMapClickListener { latLng ->
            if (modoEdicionZonaSegura && !perroSeleccionadoId.isNullOrEmpty()) {
                definirZonaSegura(latLng)
            }
        }
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }

    // ✅ Cargar lista de perros del usuario en el Spinner
    private fun cargarPerrosUsuario() {
        val usuarioId = auth.currentUser?.uid ?: return

        // Se buscan los perros que tienen como dueño al usuario autenticado
        database.child("users").orderByChild("dueñoId").equalTo(usuarioId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    listaPerros.clear() // Limpiar lista antes de cargar
                    for (perro in snapshot.children) {
                        val nombre = perro.child("nombre").getValue(String::class.java) ?: "Sin Nombre"
                        val id = perro.key ?: continue
                        listaPerros.add(Pair(nombre, id))
                    }

                    if (listaPerros.isNotEmpty()) {
                        val adapter = ArrayAdapter(
                            this@MapsActivity,
                            android.R.layout.simple_spinner_item,
                            listaPerros.map { it.first } // Mostrar nombres en el Spinner
                        )
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        spinnerPerros.adapter = adapter
                        perroSeleccionadoId = listaPerros.first().second // Seleccionar el primero por defecto
                        mostrarZonaSegura()
                    } else {
                        Toast.makeText(this@MapsActivity, "No hay perros asociados a este usuario", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "Error al cargar perros: ${error.message}")
                }
            })
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

    private fun mostrarZonaSegura() {
        if (perroSeleccionadoId.isNullOrEmpty()) return

        database.child("users").child(perroSeleccionadoId!!)
            .child("zonaSegura").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val latitud = snapshot.child("latitud").getValue(Double::class.java)
                    val longitud = snapshot.child("longitud").getValue(Double::class.java)
                    val radio = snapshot.child("radio").getValue(Int::class.java)

                    zonaSeguraCircle?.remove()

                    if (latitud != null && longitud != null && radio != null) {
                        val posicion = LatLng(latitud, longitud)
                        zonaSeguraCircle = mMap.addCircle(
                            CircleOptions()
                                .center(posicion)
                                .radius(radio.toDouble())
                                .strokeColor(Color.RED)
                                .fillColor(Color.argb(50, 255, 0, 0))
                        )
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
