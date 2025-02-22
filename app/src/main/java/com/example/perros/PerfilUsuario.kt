package com.example.perros

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class PerfilUsuario : AppCompatActivity() {

    private lateinit var ivFoto: ImageView
    private lateinit var tvNombreUsuario: TextView
    private lateinit var tvNombre: TextView
    private lateinit var tvApellidos: TextView
    private lateinit var tvEsPerro: TextView
    private lateinit var btnEditar: Button
    private lateinit var btnBack: Button
    private lateinit var btnCerrarSesion: Button

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var usuarioId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.perfil_usuario)

        ivFoto = findViewById(R.id.ivFoto)
        tvNombreUsuario = findViewById(R.id.tvNombreUsuarioValor)
        tvNombre = findViewById(R.id.tvNombreValor)
        tvApellidos = findViewById(R.id.tvApellidosValor)
        tvEsPerro = findViewById(R.id.tvEsPerroValor)
        btnEditar = findViewById(R.id.btnEditar)
        btnBack = findViewById(R.id.btnBack)
        btnCerrarSesion = findViewById(R.id.btnCerrarSesion)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        usuarioId = auth.currentUser?.uid

        if (usuarioId != null) {
            cargarPerfil()
            cargarImagenDesdeFirebase()
        } else {
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show()
        }

        btnBack.setOnClickListener {
            val intent = Intent(this, MapsActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnCerrarSesion.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnEditar.setOnClickListener {
            val intent = Intent(this@PerfilUsuario, EditarUsuario::class.java)
            startActivity(intent)
        }
    }

    private fun cargarPerfil() {
        usuarioId?.let { id ->
            Log.d("PerfilUsuario", "Cargando datos para usuario ID: $id")

            val email = auth.currentUser?.email
            tvNombreUsuario.text = email ?: "Desconocido"

            database.child("users").child(id).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        tvNombre.text = snapshot.child("nombre").getValue(String::class.java) ?: "Desconocido"
                        tvApellidos.text = snapshot.child("apellidos").getValue(String::class.java) ?: "Desconocido"
                        val esPerro = snapshot.child("isPerro").getValue(Boolean::class.java) ?: false
                        tvEsPerro.text = if (esPerro) "Sí" else "No"
                    } else {
                        Log.e("PerfilUsuario", "No se encontraron datos del usuario en Firebase.")
                        Toast.makeText(this@PerfilUsuario, "No se encontraron datos del usuario", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("PerfilUsuario", "Error al cargar datos: ${error.message}")
                    Toast.makeText(this@PerfilUsuario, "Error al cargar datos", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun cargarImagenDesdeFirebase() {
        usuarioId?.let { id ->
            val databaseRef = database.child("users").child(id).child("imagenBase64")

            databaseRef.get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val imageBase64 = snapshot.getValue(String::class.java)
                    if (!imageBase64.isNullOrEmpty()) {
                        val imageBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        ivFoto.setImageBitmap(bitmap) // ✅ Mostrar la imagen en el perfil
                    } else {
                        mostrarImagenPorDefecto()
                    }
                } else {
                    mostrarImagenPorDefecto()
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Error al cargar imagen", Toast.LENGTH_SHORT).show()
                mostrarImagenPorDefecto()
            }
        }
    }

    private fun mostrarImagenPorDefecto() {
        ivFoto.setImageResource(R.drawable.img) // ✅ Asegúrate de tener `img.png` en `res/drawable`
    }
}
