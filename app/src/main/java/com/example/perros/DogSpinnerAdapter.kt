package com.example.perros

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.google.android.material.imageview.ShapeableImageView
import androidx.core.content.ContextCompat

/**
 * Adaptador personalizado para mostrar una lista de perros en un Spinner.
 *
 * Este adaptador infla un layout personalizado que contiene:
 * - Una imagen circular del perro ([ShapeableImageView])
 * - El nombre del perro ([TextView])
 *
 * @property context Contexto de la aplicación
 * @property dogs Lista de Triple<nombre, id, bitmap> que contiene los datos de los perros
 */
class DogSpinnerAdapter(
    context: Context,
    private val dogs: List<Triple<String, String, Bitmap?>>
) : ArrayAdapter<Triple<String, String, Bitmap?>>(context, 0, dogs) {

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
     *    - Usa la bitmap directamente si existe
     *    - Usa imagen por defecto si no hay bitmap
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

        dog?.let { (nombre, _, bitmap) ->
            // Establecer el nombre del perro
            textView.text = nombre
            
            // Establecer la imagen del perro si existe
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
            } else {
                // Si no hay imagen, mostrar imagen por defecto
                imageView.setImageResource(R.drawable.img)
            }
        } ?: run {
            // Si dog es nulo, asegurarnos de mostrar una imagen por defecto
            textView.text = "Seleccionar perro"
            imageView.setImageResource(R.drawable.img)
        }

        return view
    }
}
