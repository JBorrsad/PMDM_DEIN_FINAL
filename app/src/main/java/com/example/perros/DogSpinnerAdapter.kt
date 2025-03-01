package com.example.perros

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.google.android.material.imageview.ShapeableImageView
import coil.load
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Adaptador personalizado para mostrar una lista de [DogItem] en un Spinner.
 *
 * Este adaptador infla un layout personalizado que contiene:
 * - Una imagen circular del perro ([ShapeableImageView])
 * - El nombre del perro ([TextView])
 *
 * Layout utilizado:
 * ```xml
 * spinner_item_with_image.xml:
 *   ├── ShapeableImageView (ivPerroSpinner)
 *   │     ├── layout_width: 40dp
 *   │     └── layout_height: 40dp
 *   └── TextView (tvNombrePerro)
 *         └── style: normal text
 * ```
 *
 * @property context Contexto de la aplicación
 * @property dogs Lista de [DogItem] que contiene los datos de los perros
 */
class DogSpinnerAdapter(
    context: Context,
    private val dogs: List<DogItem>
) : ArrayAdapter<DogItem>(context, 0, dogs) {

    init {
        // Precargar las imágenes al inicializar el adaptador
        preloadDogImages()
    }

    /**
     * Precarga las imágenes de los perros en la caché
     * para mejorar la velocidad de carga del spinner.
     */
    private fun preloadDogImages() {
        // Extraer las imágenes Base64 de todos los perros
        val base64Images = dogs.mapNotNull { it.imageBase64 }
        
        // Si hay imágenes, precargarlas en la caché
        if (base64Images.isNotEmpty()) {
            preloadImages(context, base64Images)
        }
    }

    /**
     * Devuelve la vista para el elemento seleccionado en el Spinner.
     * Esta vista se muestra cuando el Spinner está cerrado.
     *
     * @param position Posición del elemento en la lista
     * @param convertView Vista reciclada que puede ser reutilizada
     * @param parent ViewGroup contenedor del Spinner
     * @return Vista configurada para el elemento seleccionado
     */
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }

    /**
     * Devuelve la vista para cada elemento en el desplegable del Spinner.
     * Estas vistas se muestran cuando el Spinner está abierto.
     *
     * @param position Posición del elemento en la lista
     * @param convertView Vista reciclada que puede ser reutilizada
     * @param parent ViewGroup contenedor del desplegable
     * @return Vista configurada para el elemento en el desplegable
     */
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }

    /**
     * Crea y configura la vista para un elemento del Spinner.
     *
     * Este método:
     * 1. Infla el layout si no hay vista reciclada
     * 2. Configura la imagen del perro:
     *    - Usa la imagen cacheada si existe
     *    - Decodifica la imagen Base64 si es necesario
     *    - Usa imagen por defecto si no hay imagen
     * 3. Establece el nombre del perro
     *
     * @param position Posición del elemento a mostrar
     * @param recycledView Vista reciclada que puede ser reutilizada
     * @param parent ViewGroup contenedor
     * @return Vista completamente configurada
     */
    private fun createItemView(position: Int, recycledView: View?, parent: ViewGroup): View {
        val dog = getItem(position)
        val view = recycledView ?: LayoutInflater.from(context)
            .inflate(R.layout.spinner_item_with_image, parent, false)

        val imageView = view.findViewById<ShapeableImageView>(R.id.ivPerroSpinner)
        val textView = view.findViewById<TextView>(R.id.tvNombrePerro)

        dog?.let {
            textView.text = it.nombre
            
            // Verificar que la imagen Base64 existe antes de intentar cargarla
            if (!it.imageBase64.isNullOrEmpty()) {
                try {
                    // Comprobar si ya está en caché para cargarla más rápido
                    if (isImageInCache(context, it.imageBase64)) {
                        // Usar la versión mejorada con caché persistente
                        imageView.loadBase64Image(
                            base64Image = it.imageBase64,
                            errorDrawable = ContextCompat.getDrawable(context, R.drawable.img),
                            applyCircleCrop = true
                        )
                    } else {
                        // Si no está en caché, cargar normalmente
                        // (también se guardará en caché para el futuro)
                        imageView.loadBase64Image(
                            base64Image = it.imageBase64,
                            errorDrawable = ContextCompat.getDrawable(context, R.drawable.img),
                            applyCircleCrop = true
                        )
                    }
                } catch (e: Exception) {
                    // Manejo mejorado de errores
                    imageView.loadSafely(R.drawable.img, applyCircleCrop = true)
                    e.printStackTrace()
                }
            } else {
                // Si no hay imagen, mostrar imagen por defecto
                imageView.loadSafely(R.drawable.img, applyCircleCrop = true)
            }
        } ?: run {
            // Si dog es nulo, asegurarnos de mostrar una imagen por defecto
            textView.text = "Seleccionar perro"
            imageView.loadSafely(R.drawable.img, applyCircleCrop = true)
        }

        return view
    }
}
