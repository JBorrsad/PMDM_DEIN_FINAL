package com.example.perros

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class EditarUsuario : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.editar_usuario)

        val btnBack = findViewById<Button>(R.id.btnBack)


        // Configurar el listener para el botón
        btnBack.setOnClickListener {
            // Regresar a MapsActivity
            val intent = Intent(this, PerfilUsuario::class.java)
            startActivity(intent)
            finish()  // Opcional, para asegurarte de que la actividad actual se termine
        }

    }
}