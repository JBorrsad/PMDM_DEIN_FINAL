package com.example.perros

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.LruCache
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.decode.DataSource
import coil.disk.DiskCache
import coil.load
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.CircleCropTransformation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.min
import android.view.View
import android.util.Log

// Enumeración que define los niveles de calidad de imagen
enum class ImageQuality(val quality: Int, val maxDimension: Int) {
    HIGH(95, 1200),
    MEDIUM(85, 800),
    LOW(70, 600)
}

// Clase singleton para gestionar el ImageLoader de Coil
object CoilImageCache {
    private var imageLoader: ImageLoader? = null
    private val diskCacheFolderName = "perros_images_cache"
    
    // Tamaño máximo para la caché en disco (150MB)
    private const val DISK_CACHE_SIZE = 150 * 1024 * 1024L
    
    // Registro de imágenes en caché (para evitar operaciones duplicadas)
    private val base64HashRegistry = ConcurrentHashMap<String, Boolean>()
    
    fun getImageLoader(context: Context): ImageLoader {
        if (imageLoader == null) {
            val diskCacheFolder = File(context.cacheDir, diskCacheFolderName)
            if (!diskCacheFolder.exists()) {
                diskCacheFolder.mkdirs()
            }
            
            imageLoader = ImageLoader.Builder(context)
                .memoryCache {
                    MemoryCache.Builder(context)
                        .maxSizePercent(0.40) // Usar 40% de la memoria disponible
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(diskCacheFolder)
                        .maxSizeBytes(DISK_CACHE_SIZE)
                        .build()
                }
                // Configuración avanzada para mejor rendimiento
                .respectCacheHeaders(false) // Ignorar cabeceras HTTP para la caché
                .crossfade(true)
                .crossfade(50) // Transición más rápida (50ms)
                .build()
        }
        return imageLoader!!
    }
    
    // Función para generar un identificador único para la caché basado en el contenido Base64
    fun generateCacheKey(base64String: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(base64String.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    // Comprobar si una imagen ya está en la caché
    fun isInCache(context: Context, cacheKey: String): Boolean {
        return base64HashRegistry.containsKey(cacheKey) || 
               getImageLoader(context).diskCache?.openSnapshot(cacheKey) != null
    }
    
    // Registrar una clave en el registro de caché
    fun registerInCache(cacheKey: String) {
        base64HashRegistry[cacheKey] = true
    }
    
    // Limpiar caché en memoria (las imágenes seguirán en disco)
    fun clearMemoryCache(context: Context) {
        getImageLoader(context).memoryCache?.clear()
    }
    
    // Limpiar toda la caché (memoria y disco)
    fun clearAllCache(context: Context) {
        getImageLoader(context).memoryCache?.clear()
        getImageLoader(context).diskCache?.clear()
        base64HashRegistry.clear()
    }
}

// Caché para almacenar imágenes decodificadas (mantenemos esta para compatibilidad)
private val memoryCache = object : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 8).toInt()
) {
    override fun sizeOf(key: String, bitmap: Bitmap): Int {
        return bitmap.byteCount
    }
}

// Executor para procesamiento en segundo plano
private val executor = Executors.newCachedThreadPool()
private val mainHandler = Handler(Looper.getMainLooper())

/**
 * Función de extensión para ImageView que carga una imagen desde una cadena Base64.
 * Esta función implementa una caché en memoria y disco para evitar la decodificación 
 * repetida de la misma imagen, incluso entre sesiones de la aplicación.
 *
 * @param base64Image Cadena Base64 que representa la imagen
 * @param errorDrawable Drawable opcional para mostrar en caso de error
 * @param applyCircleCrop Si se debe aplicar un recorte circular a la imagen
 */
fun ImageView.loadBase64Image(
    base64Image: String?, 
    errorDrawable: Drawable? = null,
    applyCircleCrop: Boolean = false
) {
    // Si la cadena Base64 es nula o vacía, mostrar el drawable de error y salir
    if (base64Image.isNullOrEmpty()) {
        val defaultDrawable = errorDrawable ?: ContextCompat.getDrawable(context, R.drawable.img)!!
        this.loadSafely(defaultDrawable, applyCircleCrop)
        return
    }

    // Generar clave única para caché basada en el contenido
    val cacheKey = CoilImageCache.generateCacheKey(base64Image)
    
    // Verificar primero la caché en memoria para una carga ultra rápida
    synchronized(memoryCache) {
        val cachedBitmap = memoryCache.get(cacheKey)
        if (cachedBitmap != null) {
            // Si la imagen está en la caché de memoria, usarla inmediatamente sin animación
            this.setImageBitmap(cachedBitmap)
            return
        }
    }
    
    // Usar corrutinas para el manejo asíncrono
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // Verificar si la imagen ya está en caché o cargarla
            withContext(Dispatchers.Main) {
                val imageLoader = CoilImageCache.getImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(if (CoilImageCache.isInCache(context, cacheKey)) cacheKey else base64Image)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .placeholder(this@loadBase64Image.drawable) // Usar la imagen actual como placeholder
                    .memoryCachePolicy(CachePolicy.ENABLED) // Forzar uso de caché en memoria
                    .diskCachePolicy(CachePolicy.ENABLED)   // Forzar uso de caché en disco
                    // Eliminar crossfade si proviene de caché para una carga instantánea
                    .crossfade(if (CoilImageCache.isInCache(context, cacheKey)) 0 else 50)
                    .listener(
                        onStart = { 
                            // No mostrar indicadores de carga para evitar parpadeos
                        },
                        onSuccess = { _, result ->
                            // Registrar en el sistema de caché de Coil
                            CoilImageCache.registerInCache(cacheKey)
                            
                            // Guardar en caché de memoria legada también
                            val drawable = result.drawable
                            if (drawable != null) {
                                try {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        // Convertir drawable a bitmap de forma segura
                                        val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable) {
                                            drawable.bitmap
                                        } else {
                                            // Para otros tipos de drawables
                                            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
                                            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
                                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                            val canvas = android.graphics.Canvas(bitmap)
                                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                                            drawable.draw(canvas)
                                            bitmap
                                        }
                                        
                                        synchronized(memoryCache) {
                                            memoryCache.put(cacheKey, bitmap)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("ImageUtils", "Error al guardar en caché de memoria", e)
                                }
                            }
                        }
                    )
                    .target(this@loadBase64Image)
                    .apply {
                        if (applyCircleCrop) {
                            transformations(CircleCropTransformation())
                        }
                    }
                    .build()
                
                imageLoader.enqueue(request)
                
                // Si la imagen no está en caché, decodificar y guardar en memoria
                if (!CoilImageCache.isInCache(context, cacheKey)) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            
                            if (bitmap != null) {
                                // Guardar en caché de memoria legada
                                synchronized(memoryCache) {
                                    memoryCache.put(cacheKey, bitmap)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ImageUtils", "Error al decodificar imagen base64", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ImageUtils", "Error al cargar imagen base64", e)
            withContext(Dispatchers.Main) {
                val defaultDrawable = errorDrawable ?: ContextCompat.getDrawable(context, R.drawable.img)!!
                this@loadBase64Image.loadSafely(defaultDrawable, applyCircleCrop)
            }
        }
    }
}

/**
 * Encuentra la vista raíz para añadir el spinner.
 * Esto es útil cuando se trabaja con elementos en un spinner dropdown
 * o listas donde el contenedor padre podría ser temporal.
 */
private fun findRootViewGroup(view: View): ViewGroup? {
    var parent = view.parent as? ViewGroup
    var lastParent: ViewGroup? = null
    
    while (parent != null) {
        lastParent = parent
        parent = parent.parent as? ViewGroup
    }
    
    return lastParent
}

/**
 * Función de extensión para cargar recursos seguros en un ImageView.
 * 
 * @param drawable El drawable a mostrar
 * @param applyCircleCrop Si se debe aplicar un recorte circular
 */
fun ImageView.loadSafely(drawable: Drawable, applyCircleCrop: Boolean = false) {
    val imageLoader = CoilImageCache.getImageLoader(context)
    val request = ImageRequest.Builder(context)
        .data(drawable)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .target(this)
        .apply {
            if (applyCircleCrop) {
                transformations(CircleCropTransformation())
            }
        }
        .crossfade(true)
        .crossfade(50)  // Usar 50ms para coherencia con el resto del código
        .build()
    
    imageLoader.enqueue(request)
}

/**
 * Función de extensión para cargar recursos seguros en un ImageView.
 * 
 * @param resourceId El ID del recurso drawable a mostrar
 * @param applyCircleCrop Si se debe aplicar un recorte circular
 */
fun ImageView.loadSafely(resourceId: Int, applyCircleCrop: Boolean = false) {
    val imageLoader = CoilImageCache.getImageLoader(context)
    val request = ImageRequest.Builder(context)
        .data(resourceId)
        .target(this)
        .apply {
            if (applyCircleCrop) {
                transformations(CircleCropTransformation())
            }
        }
        .crossfade(true)
        .crossfade(50)  // Usar 50ms para coherencia
        .build()
    
    imageLoader.enqueue(request)
}

/**
 * Función de extensión para cargar bitmaps seguros en un ImageView.
 * 
 * @param bitmap El bitmap a mostrar
 * @param applyCircleCrop Si se debe aplicar un recorte circular
 */
fun ImageView.loadSafely(bitmap: Bitmap, applyCircleCrop: Boolean = false) {
    val imageLoader = CoilImageCache.getImageLoader(context)
    val request = ImageRequest.Builder(context)
        .data(bitmap)
        .target(this)
        .apply {
            if (applyCircleCrop) {
                transformations(CircleCropTransformation())
            }
        }
        .placeholder(R.drawable.img)
        .crossfade(true)
        .crossfade(50)  // Usar 50ms para coherencia
        .build()
    
    imageLoader.enqueue(request)
}

/**
 * Optimiza una imagen y la convierte a Base64 según el nivel de calidad especificado.
 *
 * @param bitmap Bitmap de la imagen original
 * @param quality Nivel de calidad (HIGH, MEDIUM, LOW)
 * @return Cadena Base64 de la imagen optimizada
 */
fun optimizeImageWithQuality(bitmap: Bitmap?, quality: ImageQuality = ImageQuality.MEDIUM): String {
    bitmap ?: return ""
    
    val (jpegQuality, maxDim) = when (quality) {
        ImageQuality.HIGH -> Pair(quality.quality, quality.maxDimension)
        ImageQuality.MEDIUM -> Pair(quality.quality, quality.maxDimension)
        ImageQuality.LOW -> Pair(quality.quality, quality.maxDimension)
    }
    
    var scaledBitmap = bitmap
    
    // Redimensionar si es necesario
    if (bitmap.width > maxDim || bitmap.height > maxDim) {
        val scale = min(
            maxDim.toFloat() / bitmap.width,
            maxDim.toFloat() / bitmap.height
        )
        
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        
        scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    val outputStream = ByteArrayOutputStream()
    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, outputStream)
    
    // Liberar memoria si se creó un nuevo bitmap
    if (scaledBitmap != bitmap) {
        scaledBitmap.recycle()
    }
    
    val byteArray = outputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.DEFAULT)
}

/**
 * Optimiza una imagen para mejorar rendimiento,
 * con valores predeterminados.
 *
 * @param bitmap Bitmap de la imagen original
 * @param maxWidth Ancho máximo (por defecto 1200px)
 * @param maxHeight Alto máximo (por defecto 1200px)
 * @param jpegQuality Calidad de compresión JPEG (por defecto 95%)
 * @return Cadena Base64 de la imagen optimizada
 */
fun optimizeImageToBase64(
    bitmap: Bitmap?,
    maxWidth: Int = 1200,
    maxHeight: Int = 1200,
    jpegQuality: Int = 95
): String {
    bitmap ?: return ""
    
    var scaledBitmap = bitmap
    
    // Redimensionar si es necesario
    if (bitmap.width > maxWidth || bitmap.height > maxHeight) {
        val scale = min(
            maxWidth.toFloat() / bitmap.width,
            maxHeight.toFloat() / bitmap.height
        )
        
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        
        scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    val outputStream = ByteArrayOutputStream()
    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, outputStream)
    
    // Liberar memoria si se creó un nuevo bitmap
    if (scaledBitmap != bitmap) {
        scaledBitmap.recycle()
    }
    
    val byteArray = outputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.DEFAULT)
}

/**
 * Convierte un bitmap a Base64 conservando su calidad original.
 * Utiliza el formato PNG para mantener toda la calidad de la imagen.
 *
 * @param bitmap Bitmap de la imagen original
 * @return Cadena Base64 de la imagen con calidad original
 */
fun bitmapToBase64OriginalQuality(bitmap: Bitmap?): String {
    bitmap ?: return ""
    
    val outputStream = ByteArrayOutputStream()
    // Usar PNG para máxima calidad (sin pérdida)
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    
    val byteArray = outputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.DEFAULT)
}

/**
 * Limpia la caché de imágenes.
 * Útil para liberar memoria cuando la aplicación
 * no necesita las imágenes cacheadas.
 */
fun clearImageCache() {
    synchronized(memoryCache) {
        memoryCache.evictAll()
    }
}

/**
 * Limpia toda la caché de imágenes (memoria y disco).
 * Útil para limpiar almacenamiento o solucionar problemas.
 * 
 * @param context Contexto de la aplicación
 */
fun clearAllImageCache(context: Context) {
    CoilImageCache.clearAllCache(context)
    synchronized(memoryCache) {
        memoryCache.evictAll()
    }
}

/**
 * Verifica si una imagen Base64 ya está en la caché del sistema.
 * Útil para comprobar antes de intentar cargar una imagen.
 * 
 * @param context Contexto de la aplicación
 * @param base64Image Imagen codificada en Base64
 * @return true si la imagen ya está en caché, false en caso contrario
 */
fun isImageInCache(context: Context, base64Image: String?): Boolean {
    if (base64Image.isNullOrEmpty()) return false
    val cacheKey = CoilImageCache.generateCacheKey(base64Image)
    return CoilImageCache.isInCache(context, cacheKey)
}

/**
 * Precarga una lista de imágenes Base64 en la caché para uso futuro.
 * Ideal para llamar cuando la aplicación inicia o durante tiempos de inactividad.
 * 
 * @param context Contexto de la aplicación
 * @param base64Images Lista de imágenes Base64 para precargar
 */
fun preloadImages(context: Context, base64Images: List<String?>) {
    CoroutineScope(Dispatchers.IO).launch {
        base64Images.forEach { base64Image ->
            if (!base64Image.isNullOrEmpty() && !isImageInCache(context, base64Image)) {
                try {
                    val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    
                    if (bitmap != null) {
                        val cacheKey = CoilImageCache.generateCacheKey(base64Image)
                        
                        // Guardar en la caché de memoria legada
                        synchronized(memoryCache) {
                            memoryCache.put(cacheKey, bitmap)
                        }
                        
                        // Guardar en la caché de Coil
                        withContext(Dispatchers.Main) {
                            val imageLoader = CoilImageCache.getImageLoader(context)
                            val request = ImageRequest.Builder(context)
                                .data(bitmap)
                                .memoryCacheKey(cacheKey)
                                .diskCacheKey(cacheKey)
                                .build()
                            
                            val result = imageLoader.execute(request)
                            if (result is SuccessResult) {
                                CoilImageCache.registerInCache(cacheKey)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

/**
 * Precarga todas las imágenes de un usuario y sus perros asociados.
 * 
 * @param context Contexto de la aplicación
 * @param userId ID del usuario actual
 */
fun precargarImagenesUsuario(context: Context, userId: String) {
    val database = com.google.firebase.database.FirebaseDatabase.getInstance().reference
    
    // Log para depuración
    Log.d("ImageUtils", "Iniciando precarga de imágenes para el usuario: $userId")
    
    // Precargar imágenes relacionadas con el usuario
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // 1. Cargar imagen del usuario con prioridad alta
            database.child("users").child(userId).child("imagenBase64")
                .get().addOnSuccessListener { snapshot ->
                    val imageBase64 = snapshot.getValue(String::class.java)
                    if (!imageBase64.isNullOrEmpty()) {
                        Log.d("ImageUtils", "Precargando imagen de perfil del usuario")
                        preloadImages(context, listOf(imageBase64))
                        
                        // Asegurarnos de que la imagen esté en memoria inmediatamente
                        val cacheKey = CoilImageCache.generateCacheKey(imageBase64)
                        if (!CoilImageCache.isInCache(context, cacheKey)) {
                            try {
                                val imageBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                if (bitmap != null) {
                                    // Forzar guardado en caché
                                    synchronized(memoryCache) {
                                        memoryCache.put(cacheKey, bitmap)
                                    }
                                    CoilImageCache.registerInCache(cacheKey)
                                }
                            } catch (e: Exception) {
                                Log.e("ImageUtils", "Error decodificando imagen de usuario", e)
                            }
                        }
                    }
                }
            
            // 2. Obtener lista de perros asociados al usuario con precarga agresiva
            database.child("users").child(userId).child("perros")
                .get().addOnSuccessListener { snapshot ->
                    val perros = mutableListOf<String>()
                    snapshot.children.forEach { perroSnapshot ->
                        val perroId = perroSnapshot.key
                        if (!perroId.isNullOrEmpty()) {
                            perros.add(perroId)
                        }
                    }
                    
                    Log.d("ImageUtils", "Se encontraron ${perros.size} perros para precargar imágenes")
                    
                    // 3. Para cada perro, precargar su imagen con alta prioridad
                    perros.forEach { perroId ->
                        database.child("perros").child(perroId).child("imagenBase64")
                            .get().addOnSuccessListener { imgSnapshot ->
                                val perroImg = imgSnapshot.getValue(String::class.java)
                                if (!perroImg.isNullOrEmpty()) {
                                    Log.d("ImageUtils", "Precargando imagen del perro $perroId")
                                    preloadImages(context, listOf(perroImg))
                                    
                                    // Asegurarnos de que la imagen esté disponible inmediatamente
                                    val cacheKey = CoilImageCache.generateCacheKey(perroImg)
                                    if (!CoilImageCache.isInCache(context, cacheKey)) {
                                        try {
                                            val imageBytes = Base64.decode(perroImg, Base64.DEFAULT)
                                            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                            if (bitmap != null) {
                                                // Forzar guardado en caché
                                                synchronized(memoryCache) {
                                                    memoryCache.put(cacheKey, bitmap)
                                                }
                                                CoilImageCache.registerInCache(cacheKey)
                                            }
                                        } catch (e: Exception) {
                                            Log.e("ImageUtils", "Error decodificando imagen de perro", e)
                                        }
                                    }
                                }
                            }
                    }
                    
                    // Notificar que la precarga ha terminado
                    Log.d("ImageUtils", "Precarga de imágenes completada para usuario $userId")
                }
        } catch (e: Exception) {
            Log.e("ImageUtils", "Error en precarga de imágenes", e)
        }
    }
} 