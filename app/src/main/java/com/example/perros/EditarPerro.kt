package com.example.perros

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import android.widget.Button
import android.widget.ImageView
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditarPerro : AppCompatActivity() {

    private lateinit var btnAdjuntarImagen: Button
    private lateinit var ivImagen: ImageView

    // Launcher para tomar foto
    private lateinit var takePhotoLauncher: ActivityResultLauncher<Uri>
    // Launcher para elegir imagen de la galería
    private lateinit var pickImageLauncher: ActivityResultLauncher<String>

    // URI donde se guardará la foto tomada
    private var photoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.editar_perro)

        btnAdjuntarImagen = findViewById(R.id.btnAdjuntarImagen)
        ivImagen = findViewById(R.id.ivImagen)

        // Configura el launcher para tomar foto
        takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                photoUri?.let {
                    ivImagen.setImageURI(it)
                }
            } else {
                showError("No se pudo tomar la foto.")
            }
        }

        // Configura el launcher para elegir imagen desde la galería
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                ivImagen.setImageURI(it)
            } ?: showError("No se seleccionó ninguna imagen.")
        }

        // Al pulsar el botón, mostramos el diálogo para elegir la acción
        btnAdjuntarImagen.setOnClickListener {
            showImageSourceDialog()
        }
    }

    /**
     * Muestra un diálogo con las opciones "Tomar foto" y "Elegir de la galería".
     */
    private fun showImageSourceDialog() {
        val options = arrayOf("Tomar foto", "Elegir de la galería")
        AlertDialog.Builder(this)
            .setTitle("Selecciona una opción")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takePhoto()  // Opción "Tomar foto"
                    1 -> pickImage()  // Opción "Elegir de la galería"
                }
            }
            .show()
    }

    /**
     * Lanza el intent para capturar una foto utilizando la cámara.
     */
    private fun takePhoto() {
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            showError("Error al crear archivo para la foto.")
            null
        }
        photoFile?.let {
            photoUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                it
            )
            //takePhotoLauncher.launch(photoUri)
        }
    }

    /**
     * Lanza el intent para elegir una imagen desde la galería.
     */
    private fun pickImage() {
        pickImageLauncher.launch("image/*")
    }

    /**
     * Crea un archivo temporal en el directorio Pictures para almacenar la foto.
     */
    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", // prefijo
            ".jpg",               // sufijo
            storageDir            // directorio
        )
    }

    /**
     * Muestra un mensaje de error utilizando un AlertDialog.
     */
    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
