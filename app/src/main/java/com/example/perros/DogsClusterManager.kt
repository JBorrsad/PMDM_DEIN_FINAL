package com.example.perros

import android.content.Context
import android.graphics.*
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

/**
 * # DogsClusterManager
 * 
 * Gestor especializado para la visualización y agrupación de marcadores de perros en el mapa
 * del sistema de monitorización de mascotas.
 * 
 * ## Funcionalidad principal
 * Esta clase gestiona la representación visual de perros en el mapa, proporcionando:
 * - Marcadores personalizados con imágenes de perfil de los perros
 * - Agrupación inteligente de marcadores cercanos para evitar sobrecarga visual
 * - Gestión de eventos de interacción con marcadores (clics individuales y en grupos)
 * - Optimización de rendimiento con reciclaje de marcadores
 * - Transiciones suaves al navegar por el mapa
 * - Carga dinámica de datos desde la caché de aplicación
 * 
 * ## Características técnicas implementadas:
 * - **Google Maps Clustering**: Uso avanzado de ClusterManager para agrupación automática
 * - **Marcadores personalizados**: Visualización de imágenes de perfil en forma circular
 * - **Procesamiento de imágenes**: Transformaciones circulares y efectos de sombra
 * - **Manejo de eventos**: Sistema de listeners para interacción con el usuario
 * - **Integración con DatosPrecargados**: Carga eficiente desde la caché de la aplicación
 * - **Optimización de dibujo**: Renderización eficiente con Canvas personalizado
 * - **Efectos visuales**: Sombras, bordes y formas personalizadas para mejor UX
 * 
 * ## Flujo de creación de marcadores:
 * ```
 * 1. addDogMarker(perroId, position)
 * 2. Recuperación de datos desde DatosPrecargados
 * 3. Creación de objeto DogItem con información del perro
 * 4. Procesamiento de imagen para marcador personalizado
 * 5. Adición al ClusterManager y actualización del mapa
 * ```
 * 
 * La personalización avanzada de marcadores mejora significativamente la experiencia
 * de usuario, facilitando la identificación visual rápida de cada mascota en el mapa.
 * 
 * @property context Contexto de la aplicación para acceso a recursos y servicios
 * @property mMap Instancia de GoogleMap donde se mostrarán los marcadores
 * @property clusterManager Gestor de clustering proporcionado por Google Maps Utils
 * 
 * @see DogItem Clase interna que representa un elemento en el mapa
 * @see DogClusterRenderer Renderizador personalizado para marcadores y clusters
 */
class DogsClusterManager(
    private val context: Context,
    private val mMap: GoogleMap,
    private val clusterManager: ClusterManager<DogItem>
) {

    init {
        // Configurar el renderer personalizado
        clusterManager.renderer = DogClusterRenderer(context, mMap, clusterManager)

        // Configurar listeners
        mMap.setOnCameraIdleListener(clusterManager)
        mMap.setOnMarkerClickListener(clusterManager)

        // Configurar listener para clics en items individuales
        clusterManager.setOnClusterItemClickListener { item ->
            // Manejo del clic en marcador individual
            Log.d("DogsClusterManager", "Clic en perro: ${item.title} (ID: ${item.dogId})")
            true
        }

        // Configurar listener para clics en clusters
        clusterManager.setOnClusterClickListener { cluster ->
            Log.d("DogsClusterManager", "Clic en cluster con ${cluster.size} elementos")
            false // falso para permitir el comportamiento por defecto (zoom)
        }
    }

    /**
     * Añade un marcador de perro al mapa.
     *
     * @param perroId ID del perro
     * @param position Posición geográfica donde mostrar el marcador
     */
    fun addDogMarker(perroId: String, position: LatLng) {
        try {
            // Obtener datos del perro
            val perroSnapshot = DatosPrecargados.obtenerPerro(perroId)

            if (perroSnapshot != null && perroSnapshot.exists()) {
                val nombre = perroSnapshot.child("nombre").getValue(String::class.java) ?: "Perro"
                val imagenBase64 = perroSnapshot.child("imagenBase64").getValue(String::class.java)
                val duenioId = perroSnapshot.child("dueñoId").getValue(String::class.java)

                var imagenDuenio: String? = null

                // Obtener imagen del dueño
                if (duenioId != null) {
                    val duenioData = DatosPrecargados.obtenerUsuario(duenioId)
                    if (duenioData != null) {
                        imagenDuenio = duenioData.child("imagenBase64").getValue(String::class.java)
                    }
                }

                // Crear item para cluster
                val dogItem = DogItem(
                    position = position,
                    title = nombre,
                    snippet = "Perro",
                    dogId = perroId,
                    dogImage = imagenBase64,
                    ownerImage = imagenDuenio
                )

                // Añadir al cluster
                clusterManager.addItem(dogItem)
                clusterManager.cluster()

                Log.d("DogsClusterManager", "Marcador añadido para perro: $nombre")
            } else {
                Log.e("DogsClusterManager", "No se encontraron datos del perro $perroId")
            }
        } catch (e: Exception) {
            Log.e("DogsClusterManager", "Error al añadir marcador de perro", e)
        }
    }

    /**
     * Limpia todos los marcadores existentes.
     */
    fun limpiarMarcadores() {
        clusterManager.clearItems()
    }

    /**
     * DogItem
     *
     * Funcionalidad principal
     *
     * Clase que encapsula la información necesaria para representar un perro 
     * como un elemento dentro del mapa de Google Maps, incluyendo su posición
     * geográfica, datos de identificación y recursos visuales asociados.
     *
     * Características técnicas implementadas
     *
     * - Implementación de ClusterItem para integración con el sistema de clustering
     * - Almacenamiento optimizado de recursos gráficos para marcadores
     * - Soporte para información extendida como título y snippet para InfoWindow
     * - Referencias eficientes a recursos de imagen para perfiles de perros y dueños
     *
     * @property position Coordenada geográfica (latitud/longitud) del perro
     * @property title Nombre del perro mostrado como título principal
     * @property snippet Información adicional mostrada en el InfoWindow
     * @property dogId Identificador único del perro en la base de datos
     * @property dogImage Imagen de perfil del perro en formato String (Base64)
     * @property ownerImage Imagen de perfil del dueño en formato String (Base64)
     */
    class DogItem(
        private val position: LatLng,
        private val title: String?,
        private val snippet: String?,
        val dogId: String,
        val dogImage: String?,
        val ownerImage: String?
    ) : ClusterItem {
        override fun getPosition(): LatLng = position
        override fun getTitle(): String = title ?: ""
        override fun getSnippet(): String = snippet ?: ""
    }

    /**
     * DogClusterRenderer
     *
     * Funcionalidad principal
     *
     * Renderizador personalizado para visualizar perros y clusters de perros
     * en el mapa, aplicando transformaciones visuales avanzadas a los marcadores
     * para mejorar la identificación y experiencia de usuario.
     *
     * Características técnicas implementadas
     *
     * - Transformación de imágenes: Procesamiento de imágenes para renderizado circular
     * - Manejo de estados de error: Visualización de marcadores por defecto cuando no hay imagen
     * - Personalización avanzada: Configuración detallada de la apariencia de marcadores 
     * - Optimización gráfica: Gestión eficiente de la memoria durante las transformaciones
     * - Compatibilidad con clustering: Integración con Google Maps Utils para agrupación
     *
     * @property context Contexto de la aplicación para acceso a recursos
     * @property map Instancia de GoogleMap donde se renderizarán los elementos
     * @property clusterManager Gestor de clustering que coordina la agrupación
     */
    private inner class DogClusterRenderer(
        context: Context,
        map: GoogleMap,
        clusterManager: ClusterManager<DogItem>
    ) : DefaultClusterRenderer<DogItem>(context, map, clusterManager) {

        override fun onBeforeClusterItemRendered(item: DogItem, markerOptions: MarkerOptions) {
            // Personalizar marcador individual
            try {
                if (item.dogImage != null && item.dogImage.isNotEmpty()) {
                    try {
                        val imageBytes = Base64.decode(item.dogImage, Base64.DEFAULT)
                        val decodedImage =
                            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        // Usar el marcador personalizado con la imagen del perro
                        val customMarker = createCustomDogMarker(context, decodedImage)
                        markerOptions.icon(customMarker).title(item.title)
                    } catch (e: Exception) {
                        Log.e("DogsClusterManager", "Error al decodificar imagen del perro", e)
                        markerOptions.icon(
                            BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_AZURE
                            )
                        )
                            .title(item.title)
                    }
                } else {
                    // Si no hay imagen, usar marcador por defecto
                    markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                        .title(item.title)
                }
            } catch (e: Exception) {
                Log.e("DogsClusterManager", "Error al crear marcador personalizado", e)
                markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    .title(item.title)
            }
        }

        override fun shouldRenderAsCluster(cluster: Cluster<DogItem>): Boolean {
            // Agrupar solo si hay más de un elemento
            return cluster.size > 1
        }
    }

    /**
     * Crea un marcador personalizado para representar al perro en el mapa.
     *
     * Características del marcador:
     * - Forma circular con la imagen del perro
     * - Efecto de sombra para mejor visibilidad
     * - Tamaño optimizado para la visualización en el mapa
     *
     * @param context Contexto de la aplicación
     * @param bitmap Imagen del perro a usar en el marcador
     * @return BitmapDescriptor con el icono personalizado
     */
    private fun createCustomDogMarker(context: Context, bitmap: Bitmap): BitmapDescriptor {
        val markerSize = 150
        val shadowSize = 30

        val bmp = Bitmap.createBitmap(
            markerSize + shadowSize * 2,
            markerSize + shadowSize * 2,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)

        // Sombra
        val shadowPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            maskFilter =
                BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
            alpha = 100
        }
        val shadowRadius = (markerSize / 2) + shadowSize
        canvas.drawCircle(
            shadowRadius.toFloat(),
            shadowRadius.toFloat(),
            (markerSize / 2).toFloat(),
            shadowPaint
        )

        // Dibuja la forma del marcador
        val markerDrawable = ContextCompat.getDrawable(context, R.drawable.custom_marker1)!!
        markerDrawable.setBounds(
            shadowSize,
            shadowSize,
            markerSize + shadowSize,
            markerSize + shadowSize
        )
        markerDrawable.draw(canvas)

        // Imagen circular
        val resizedBitmap = bitmap.scale(100, 100)
        val circularBitmap = getCircularBitmap(resizedBitmap, 100)
        val paint = Paint().apply { isAntiAlias = true }

        val imageOffsetX = shadowSize + ((markerSize - 100) / 2)
        val imageOffsetY = shadowSize + ((markerSize - 100) / 2) - 10

        canvas.drawBitmap(circularBitmap, imageOffsetX.toFloat(), imageOffsetY.toFloat(), paint)

        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    /**
     * Convierte un bitmap cuadrado en un bitmap circular.
     *
     * @param bitmap Bitmap de entrada
     * @param size Tamaño del bitmap resultante
     * @return Bitmap circular
     */
    private fun getCircularBitmap(bitmap: Bitmap, size: Int): Bitmap {
        val output = createBitmap(size, size)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)
        val rectF = RectF(rect)

        canvas.drawOval(rectF, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, null, rect, paint)

        return output
    }
} 