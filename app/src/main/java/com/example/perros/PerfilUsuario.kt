package com.example.perros


import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class PerfilUsuario : AppCompatActivity() {

    private lateinit var ivPerfil: ImageView
    private lateinit var tvNombreUsuario: TextView
    private lateinit var tvCorreoUsuario: TextView
    private lateinit var btnEditarPerfil: Button

    // Referencia a la base de datos de Firebase
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.perfil_usuario) // El layout es perfil_usuario.xml

        val btnBack = findViewById<Button>(R.id.btnBack)
        val editar = findViewById<Button>(R.id.editar)


        // Configurar el listener para el botón
        btnBack.setOnClickListener {
            // Regresar a MapsActivity
            val intent = Intent(this, MapsActivity::class.java)
            startActivity(intent)
            finish()  // Opcional, para asegurarte de que la actividad actual se termine
        }
        // Configurar el listener para el botón
        editar.setOnClickListener {
            // Regresar a MapsActivity
            val intent = Intent(this, EditarUsuario::class.java)
            startActivity(intent)
            finish()  // Opcional, para asegurarte de que la actividad actual se termine
        }

    }
}
