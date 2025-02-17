package com.example.perros

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class CrearPerrosActivity : AppCompatActivity() {

    private lateinit var ivPerro: ImageView
    private lateinit var btnAdjuntarImagen: Button

    private val PICK_IMAGE_REQUEST = 1
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.crear_perro)

        // Inicializar vistas
        ivPerro = findViewById(R.id.ivPerro)
        btnAdjuntarImagen = findViewById(R.id.btnAdjuntarImagen)
        val btnBack = findViewById<Button>(R.id.btnBack)

        // Configurar botón para regresar a MapsActivity
        btnBack.setOnClickListener {
            val intent = Intent(this, MapsActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Configurar botón para adjuntar imagen
        btnAdjuntarImagen.setOnClickListener {
            abrirGaleria()
        }
    }

    private fun abrirGaleria() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            imageUri = data.data
            ivPerro.setImageURI(imageUri)
        }
    }
}
