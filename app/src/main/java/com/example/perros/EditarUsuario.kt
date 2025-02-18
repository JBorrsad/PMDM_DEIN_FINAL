package com.example.perros

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class EditarUsuario : AppCompatActivity() {

    private lateinit var btnGuardar: Button
    private lateinit var etNombreUsuario: EditText
    private lateinit var etNombre: EditText
    private lateinit var etApellidos: EditText
    private lateinit var switchEsPerro: Switch
    private lateinit var spinnerDueño: Spinner
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var usuarioId: String? = null
    private var esPerro = false
    private var dueñoSeleccionado: String? = null
    private val dueñosIds = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.editar_usuario)

        btnGuardar = findViewById(R.id.btnGuardarUsuario)
        etNombreUsuario = findViewById(R.id.etNombreUsuario)
        etNombre = findViewById(R.id.etNombre)
        etApellidos = findViewById(R.id.etApellidos)
        switchEsPerro = findViewById(R.id.switchEsPerro)
        spinnerDueño = findViewById(R.id.spinnerDueño)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        usuarioId = auth.currentUser?.uid

        // ✅ Bloquear edición del nombre de usuario
        etNombreUsuario.isEnabled = false

        cargarDatosUsuario()
        cargarUsuariosDueños()

        switchEsPerro.setOnCheckedChangeListener { _, isChecked ->
            esPerro = isChecked
            spinnerDueño.visibility = if (esPerro) View.VISIBLE else View.GONE
        }

        btnGuardar.setOnClickListener {
            guardarCambios()
        }
    }

    private fun cargarDatosUsuario() {
        usuarioId?.let { id ->
            database.child("users").child(id).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // ✅ Obtener el correo del usuario autenticado
                    etNombreUsuario.setText(auth.currentUser?.email ?: "Correo desconocido")

                    // ✅ Obtener otros datos desde Firebase
                    etNombre.setText(snapshot.child("nombre").getValue(String::class.java) ?: "")
                    etApellidos.setText(snapshot.child("apellidos").getValue(String::class.java) ?: "")
                    esPerro = snapshot.child("isPerro").getValue(Boolean::class.java) ?: false
                    switchEsPerro.isChecked = esPerro

                    // ✅ Si es perro, mostrar el dueño seleccionado
                    if (esPerro) {
                        val dueñoId = snapshot.child("dueñoId").getValue(String::class.java)
                        spinnerDueño.visibility = View.VISIBLE
                        dueñoSeleccionado = dueñoId
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun cargarUsuariosDueños() {
        database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val dueños = mutableListOf<String>()
                dueñosIds.clear()

                for (usuario in snapshot.children) {
                    val isPerro = usuario.child("isPerro").getValue(Boolean::class.java) ?: false
                    if (!isPerro) {
                        dueños.add(usuario.child("nombre").getValue(String::class.java) ?: "Desconocido")
                        dueñosIds.add(usuario.key!!)
                    }
                }

                val adapter = ArrayAdapter(this@EditarUsuario, android.R.layout.simple_spinner_dropdown_item, dueños)
                spinnerDueño.adapter = adapter

                // ✅ Si el usuario ya es un perro, preseleccionar su dueño
                dueñoSeleccionado?.let { id ->
                    val index = dueñosIds.indexOf(id)
                    if (index != -1) {
                        spinnerDueño.setSelection(index)
                    }
                }

                spinnerDueño.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        dueñoSeleccionado = dueñosIds[position]
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun guardarCambios() {
        usuarioId?.let { id ->
            val updates = mapOf(
                "nombreUsuario" to (auth.currentUser?.email ?: ""),
                "nombre" to etNombre.text.toString(),
                "apellidos" to etApellidos.text.toString(),
                "isPerro" to esPerro,
                "dueñoId" to if (esPerro) dueñoSeleccionado else null
            )

            database.child("users").child(id).updateChildren(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Usuario actualizado", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al actualizar", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
