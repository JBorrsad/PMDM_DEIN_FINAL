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
     *    - Decodifica la imagen Base64 si existe
     *    - Usa imagen por defecto si no hay imagen o hay error
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
            if (!it.imageBase64.isNullOrEmpty()) {
                try {
                    val imageBytes = Base64.decode(it.imageBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    imageView.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    // En caso de error, se muestra una imagen por defecto.
                    imageView.setImageResource(R.drawable.img)
                }
            } else {
                // Si no hay imagen codificada, se muestra la imagen por defecto.
                imageView.setImageResource(R.drawable.img)
            }
        }

        return view
    }
}
