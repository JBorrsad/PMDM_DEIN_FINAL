package com.example.perros

import android.content.Context
import android.util.AttributeSet
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView

class LoadingSpinner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        inflate(context, R.layout.loading_spinner, this)
        
        // Iniciar la animación de rotación
        val imageView = findViewById<ImageView>(R.id.spinner_image)
        val rotateAnim = AnimationUtils.loadAnimation(context, R.anim.rotate)
        imageView.startAnimation(rotateAnim)
    }
} 