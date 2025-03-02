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
 * Clase que gestiona la visualización y agrupación de marcadores de perros en el mapa.
 *
 * Esta clase se encarga de:
 * - Crear y personalizar marcadores para los perros
 * - Gestionar la agrupación (clustering) de marcadores cercanos
 * - Configurar la apariencia de los marcadores y grupos
 * - Gestionar eventos de interacción con los marcadores
 *
 * @property context Contexto de la aplicación
 * @property mMap Mapa de Google donde se mostrarán los marcadores
 * @property clusterManager Gestor de clusters proporcionado por Google Maps Utils
 *
 * @author Aplicación PawTracker
 * @since 1.0
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
     * Clase que define un elemento para ser mostrado en el mapa y agrupado.
     * Implementa ClusterItem para ser compatible con ClusterManager.
     *
     * @property position Posición geográfica del elemento
     * @property title Título del elemento (nombre del perro)
     * @property snippet Texto descriptivo adicional
     * @property dogId ID único del perro
     * @property dogImage Imagen del perro en formato Base64 (opcional)
     * @property ownerImage Imagen del dueño en formato Base64 (opcional)
     */
    class DogItem(
        private val position: LatLng,
        private val title: String,
        private val snippet: String,
        val dogId: String,
        var dogImage: String? = null,
        var ownerImage: String? = null
    ) : ClusterItem {
        override fun getPosition(): LatLng = position
        override fun getTitle(): String = title
        override fun getSnippet(): String = snippet
    }

    /**
     * Renderer personalizado para los marcadores de perros.
     * Personaliza la apariencia de los marcadores individuales y clusters.
     */
    private inner class DogClusterRenderer(
        context: Context,
        map: GoogleMap,
        clusterManager: ClusterManager<DogItem>
    ) : DefaultClusterRenderer<DogItem>(context, map, clusterManager) {

        override fun onBeforeClusterItemRendered(item: DogItem, markerOptions: MarkerOptions) {
            // Personalizar marcador individual
            try {
                if (!item.dogImage.isNullOrEmpty()) {
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