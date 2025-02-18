package com.example.perros

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class PerfilUsuario : AppCompatActivity() {

    private lateinit var tvNombreUsuario: TextView
    private lateinit var tvNombre: TextView
    private lateinit var tvApellidos: TextView
    private lateinit var tvEsPerro: TextView
    private lateinit var btnEditar: Button
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var usuarioId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.perfil_usuario)

        tvNombreUsuario = findViewById(R.id.tvNombreUsuarioValor)
        tvNombre = findViewById(R.id.tvNombreValor)
        tvApellidos = findViewById(R.id.tvApellidosValor)
        tvEsPerro = findViewById(R.id.tvEsPerroValor)
        btnEditar = findViewById(R.id.btnEditar)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        usuarioId = auth.currentUser?.uid

        if (usuarioId != null) {
            cargarPerfil()
        } else {
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show()
        }

        btnEditar.setOnClickListener {
            val intent = Intent(this, EditarUsuario::class.java)
            startActivity(intent)
        }
    }

    private fun cargarPerfil() {
        usuarioId?.let { id ->
            Log.d("PerfilUsuario", "Cargando datos para usuario ID: $id")

            // Obtener el email del usuario autenticado
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
}
