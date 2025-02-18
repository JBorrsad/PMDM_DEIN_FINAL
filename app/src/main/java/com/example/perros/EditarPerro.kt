package com.example.perros

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class EditarPerro : AppCompatActivity() {

    private lateinit var btnGuardar: Button
    private lateinit var etNombre: EditText
    private lateinit var etRaza: EditText
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var usuarioId: String? = null
    private var perroId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.editar_perro)

        btnGuardar = findViewById(R.id.btnSubmit)
        etNombre = findViewById(R.id.etNombre)
        etRaza = findViewById(R.id.etRaza)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        usuarioId = auth.currentUser?.uid
        perroId = intent.getStringExtra("perroId") // Recibimos el ID del usuario-perro

        cargarDatosPerro()

        btnGuardar.setOnClickListener {
            guardarCambios()
        }
    }

    private fun cargarDatosPerro() {
        if (perroId == null) {
            Toast.makeText(this, "Error al cargar el perro", Toast.LENGTH_SHORT).show()
            return
        }

        database.child("users").child(perroId!!) // Ahora estamos accediendo al usuario que es un perro
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    etNombre.setText(snapshot.child("nombre").getValue(String::class.java) ?: "")
                    etRaza.setText(snapshot.child("raza").getValue(String::class.java) ?: "")
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@EditarPerro, "Error al cargar datos", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun guardarCambios() {
        if (perroId == null) {
            Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = mapOf(
            "nombre" to etNombre.text.toString(),
            "raza" to etRaza.text.toString()
        )

        database.child("users").child(perroId!!) // Guardamos los datos en el usuario-perro
            .updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Datos del perro actualizados", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al actualizar", Toast.LENGTH_SHORT).show()
            }
    }
}
