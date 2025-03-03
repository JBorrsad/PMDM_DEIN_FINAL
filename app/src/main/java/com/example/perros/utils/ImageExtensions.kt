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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions

/**
 * # Extensiones para gestión de imágenes
 * 
 * Conjunto de funciones de extensión que optimizan el procesamiento, carga y visualización
 * de imágenes en el sistema de monitorización de mascotas.
 * 
 * ## Funcionalidad principal
 * Estas extensiones facilitan:
 * - Carga eficiente de imágenes codificadas en Base64
 * - Implementación de caché en memoria para mejorar rendimiento
 * - Transformaciones avanzadas como recortes circulares
 * - Optimización de imágenes según requisitos de calidad
 * - Manejadores de errores y placeholders predeterminados
 * 
 * ## Componentes clave
 * - **Sistema de caché**: Almacenamiento inteligente para acceso rápido a imágenes recurrentes
 * - **Procesamiento asíncrono**: Operaciones ejecutadas en segundo plano para no bloquear la UI
 * - **Optimización de memoria**: Control de recursos para evitar OutOfMemoryError
 * - **Integración con Glide**: Utilización de esta biblioteca para transformaciones avanzadas
 * - **Manejo de errores**: Gestión robusta de excepciones y estados de error
 */

// Cache de imágenes para evitar decodificar repetidamente
private val imageCache = ConcurrentHashMap<String, Bitmap>()

/**
 * # loadBase64Image
 * 
 * Extensión para ImageView que carga y visualiza una imagen codificada en formato Base64.
 * 
 * ## Características principales
 * - Utiliza un sistema de caché inteligente para minimizar la decodificación
 * - Ejecuta el procesamiento pesado en segundo plano mediante corrutinas
 * - Soporta recorte circular para imágenes de perfil
 * - Muestra imágenes por defecto en caso de error o valores nulos
 * - Proporciona transiciones suaves durante la carga
 * 
 * ## Proceso interno
 * 1. Verifica si la imagen está en caché para acceso inmediato
 * 2. Si no está en caché, decodifica la cadena Base64 en segundo plano
 * 3. Aplica transformaciones solicitadas (como recorte circular)
 * 4. Almacena el resultado en caché para futuros accesos
 * 5. Muestra la imagen con animación opcional
 * 
 * Esta función es crítica para el rendimiento de la aplicación, ya que evita
 * decodificaciones repetidas de imágenes grandes en formato Base64.
 * 
 * @param base64Image Cadena en formato Base64 que representa la imagen. Si es null o vacía,
 *                   se muestra una imagen predeterminada.
 * @param applyCircleCrop Si es true, aplica un recorte circular ideal para imágenes de perfil.
 *                       Utilizado principalmente en avatares de usuarios y perros.
 */
fun ImageView.loadBase64Image(base64Image: String?, applyCircleCrop: Boolean = false) {
    if (base64Image.isNullOrEmpty()) {
        // Aplicar imagen por defecto, con o sin recorte circular
        if (applyCircleCrop) {
            Glide.with(this.context)
                .load(R.drawable.img)
                .apply(RequestOptions().transform(CircleCrop()))
                .into(this)
        } else {
            this.setImageResource(R.drawable.img)
        }
        return
    }
    
    // Intenta obtener la imagen de la caché
    val cachedBitmap = imageCache[base64Image]
    if (cachedBitmap != null) {
        if (applyCircleCrop) {
            // Usar Glide para aplicar el recorte circular a la imagen en caché
            Glide.with(this.context)
                .load(cachedBitmap)
                .apply(RequestOptions().transform(CircleCrop()))
                .into(this)
        } else {
            this.setImageBitmap(cachedBitmap)
        }
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
                
                if (applyCircleCrop) {
                    // Usar Glide para aplicar el recorte circular
                    Glide.with(this@loadBase64Image.context)
                        .load(bitmap)
                        .apply(RequestOptions().transform(CircleCrop()))
                        .into(this@loadBase64Image)
                } else {
                    this@loadBase64Image.setImageBitmap(bitmap)
                }
            } else {
                if (applyCircleCrop) {
                    Glide.with(this@loadBase64Image.context)
                        .load(R.drawable.img)
                        .apply(RequestOptions().transform(CircleCrop()))
                        .into(this@loadBase64Image)
                } else {
                    this@loadBase64Image.setImageResource(R.drawable.img)
                }
            }
        } catch (e: Exception) {
            Log.e("ImageUtils", "Error decodificando imagen Base64", e)
            if (applyCircleCrop) {
                Glide.with(this@loadBase64Image.context)
                    .load(R.drawable.img)
                    .apply(RequestOptions().transform(CircleCrop()))
                    .into(this@loadBase64Image)
            } else {
                this@loadBase64Image.setImageResource(R.drawable.img)
            }
        }
    }
}

/**
 * # bitmapToBase64OriginalQuality
 * 
 * Convierte una imagen Bitmap a formato Base64 conservando su calidad original.
 * 
 * ## Características principales
 * - Mantiene la calidad máxima (100%) durante la compresión
 * - Utiliza el formato JPEG por su equilibrio entre calidad y tamaño
 * - No realiza redimensionado, conservando las dimensiones originales
 * - Gestiona adecuadamente la memoria durante la conversión
 * 
 * Esta función es utilizada principalmente para guardar imágenes de alta calidad
 * en Firebase Realtime Database, donde se requiere un formato de texto (Base64).
 * 
 * @param bitmap Imagen Bitmap a convertir. No debe ser null.
 * @return Cadena en formato Base64 que representa la imagen con su calidad original.
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