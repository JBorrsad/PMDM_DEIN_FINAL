package com.example.perros

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

// Cache de imágenes para evitar decodificar repetidamente
private val imageCache = ConcurrentHashMap<String, Bitmap>()

/**
 * Extensión para cargar una imagen en formato Base64 en un ImageView.
 * Si la imagen ya está en caché, la usa directamente.
 * 
 * @param base64Image String en formato Base64 que representa la imagen
 */
fun ImageView.loadBase64Image(base64Image: String?) {
    if (base64Image.isNullOrEmpty()) {
        this.setImageResource(R.drawable.img) // Imagen por defecto
        return
    }
    
    // Intenta obtener la imagen de la caché
    val cachedBitmap = imageCache[base64Image]
    if (cachedBitmap != null) {
        this.setImageBitmap(cachedBitmap)
        return
    }
    
    // Si no está en caché, decodifica en segundo plano
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val bitmap = withContext(Dispatchers.IO) {
                val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            }
            
            // Guardar en caché para uso futuro
            if (bitmap != null) {
                imageCache[base64Image] = bitmap
                this@loadBase64Image.setImageBitmap(bitmap)
            } else {
                this@loadBase64Image.setImageResource(R.drawable.img)
            }
        } catch (e: Exception) {
            Log.e("ImageUtils", "Error decodificando imagen Base64", e)
            this@loadBase64Image.setImageResource(R.drawable.img)
        }
    }
}

/**
 * Convierte un Bitmap a formato Base64 manteniendo la calidad original.
 * 
 * @param bitmap El bitmap a convertir
 * @return String representación en Base64 del bitmap
 */
fun bitmapToBase64OriginalQuality(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
    val byteArray = outputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.DEFAULT)
}

// NOTA: Las siguientes funciones están comentadas para evitar conflictos con
// las implementaciones en ImageUtils.kt

/**
 * Precarga imágenes en memoria para uso futuro (DESHABILITADA).
 * Ver implementación en ImageUtils.kt.
 */
// fun preloadImages(context: Context, imageBase64List: List<String>) {
//     // Implementación deshabilitada para evitar conflictos
// }

/**
 * Precarga datos del usuario para uso futuro (DESHABILITADA).
 * Ver implementación en ImageUtils.kt.
 */
// fun preloadUserData(userId: String, callback: () -> Unit) {
//     // Implementación deshabilitada para evitar conflictos
// } 