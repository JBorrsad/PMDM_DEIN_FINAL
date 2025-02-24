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
import android.util.Log

class DogSpinnerAdapter(
    context: Context,
    private val dogs: List<DogItem>
) : ArrayAdapter<DogItem>(context, 0, dogs) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }

    private fun createItemView(position: Int, recycledView: View?, parent: ViewGroup): View {
        val dog = getItem(position)
        val view = recycledView ?: LayoutInflater.from(context)
            .inflate(R.layout.spinner_item_with_image, parent, false)

        val imageView = view.findViewById<ShapeableImageView>(R.id.ivPerroSpinner)
        val textView = view.findViewById<TextView>(R.id.tvNombrePerro)

        if (dog != null) {
            textView.text = dog.nombre
            try {
                if (!dog.imageBase64.isNullOrEmpty()) {
                    val imageBytes = Base64.decode(dog.imageBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    } else {
                        imageView.setImageResource(R.drawable.img)
                        Log.e("DogSpinnerAdapter", "Error decodificando bitmap para ${dog.nombre}")
                    }
                } else {
                    imageView.setImageResource(R.drawable.img)
                    Log.d("DogSpinnerAdapter", "No hay imagen para ${dog.nombre}")
                }
            } catch (e: Exception) {
                imageView.setImageResource(R.drawable.img)
                Log.e("DogSpinnerAdapter", "Error cargando imagen para ${dog.nombre}: ${e.message}")
            }
        }

        return view
    }
}
