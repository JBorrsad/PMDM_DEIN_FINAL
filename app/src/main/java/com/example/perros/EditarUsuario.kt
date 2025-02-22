package com.example.perros

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.yalantis.ucrop.UCrop
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.core.net.toUri

class EditarUsuario : AppCompatActivity() {

    private lateinit var btnGuardar: Button
    private lateinit var btnBack: ImageButton
    private lateinit var ivImagen: ImageView
    private lateinit var etNombreUsuario: EditText
    private lateinit var etNombre: EditText
    private lateinit var etApellidos: EditText
    private lateinit var switchEsPerro: SwitchCompat
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var usuarioId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.editar_usuario)

        ivImagen = findViewById(R.id.ivImagen)
        btnGuardar = findViewById(R.id.btnGuardarUsuario)
        btnBack = findViewById(R.id.btnBack)

        etNombreUsuario = findViewById(R.id.etNombreUsuario)
        etNombre = findViewById(R.id.etNombre)
        etApellidos = findViewById(R.id.etApellidos)
        switchEsPerro = findViewById(R.id.switchEsPerro)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        usuarioId = auth.currentUser?.uid

        etNombreUsuario.isEnabled = false // ✅ Bloquea la edición del nombre de usuario

        cargarDatosUsuario()
        cargarImagenDesdeFirebase()

        val btnAdjuntarImagen = findViewById<ImageButton>(R.id.btnAdjuntarImagen)
        btnAdjuntarImagen.setOnClickListener {
            abrirGaleriaOCamara()
        }

        btnGuardar.setOnClickListener {
            guardarCambios()
        }

        btnBack.setOnClickListener {
            redirigirAPerfil()
        }
    }

    private fun cargarDatosUsuario() {
        usuarioId?.let { id ->
            database.child("users").child(id)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        etNombreUsuario.setText(auth.currentUser?.email ?: "Correo desconocido")
                        etNombre.setText(
                            snapshot.child("nombre").getValue(String::class.java) ?: ""
                        )
                        etApellidos.setText(
                            snapshot.child("apellidos").getValue(String::class.java) ?: ""
                        )
                        switchEsPerro.isChecked =
                            snapshot.child("isPerro").getValue(Boolean::class.java) == true
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(
                            this@EditarUsuario,
                            "Error al cargar datos",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
        }
    }

    private fun abrirGaleriaOCamara() {
        val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        val chooser = Intent.createChooser(pickIntent, "Selecciona una imagen")
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takePictureIntent))

        imagePickerLauncher.launch(chooser)
    }

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val selectedImageUri: Uri? = result.data!!.data
                if (selectedImageUri != null) {
                    iniciarUCrop(selectedImageUri) // ✅ Enviar imagen a UCrop para editar
                } else {
                    Toast.makeText(this, "Error al seleccionar imagen", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private fun iniciarUCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(cacheDir, "cropped_image.jpg"))

        UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f) // 🔹 Recorte cuadrado
            .withMaxResultSize(800, 800) // 🔹 Tamaño máximo de la imagen editada
            .start(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK) {
            val resultUri = UCrop.getOutput(data!!)
            if (resultUri != null) {
                val bitmap = uriToBitmap(resultUri)
                bitmap?.let {
                    ivImagen.setImageBitmap(it) // ✅ Mostrar imagen editada en `ImageView`
                    guardarImagenRecortadaEnFirebase(it) // ✅ Subir imagen editada a Firebase
                }
            }
        } else if (requestCode == UCrop.REQUEST_CROP && resultCode == UCrop.RESULT_ERROR) {
            val cropError = UCrop.getError(data!!)
            Toast.makeText(
                this,
                "Error al recortar la imagen: ${cropError?.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getImageUriFromBitmap(bitmap: Bitmap): Uri {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "temp", null)
        return path.toUri()
    }

    private fun guardarImagenRecortadaEnFirebase(bitmap: Bitmap) {
        usuarioId?.let { id ->
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos) // ✅ PNG para mejor calidad
            val imageBytes = baos.toByteArray()
            val imageBase64 = Base64.encodeToString(imageBytes, Base64.DEFAULT)

            val updates = mapOf("imagenBase64" to imageBase64)

            database.child("users").child(id).updateChildren(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Imagen guardada correctamente", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al guardar la imagen", Toast.LENGTH_SHORT).show()
                }
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
                        ivImagen.setImageBitmap(bitmap)
                    }
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Error al cargar imagen", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun guardarCambios() {
        usuarioId?.let { id ->
            val updates = mapOf(
                "nombre" to etNombre.text.toString(),
                "apellidos" to etApellidos.text.toString(),
                "isPerro" to switchEsPerro.isChecked
            )

            database.child("users").child(id).updateChildren(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Datos actualizados", Toast.LENGTH_SHORT).show()
                    redirigirAPerfil()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al actualizar", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun redirigirAPerfil() {
        val intent = Intent(this, PerfilUsuario::class.java)
        startActivity(intent)
        finish()
    }
}
