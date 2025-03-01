package com.example.perros

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Componente de interfaz de usuario que muestra un spinner de carga animado.
 * 
 * Este spinner es un ImageView con una animación de rotación continua.
 * Se utiliza para indicar al usuario que se está llevando a cabo un proceso
 * en segundo plano (como la carga de una imagen).
 */
class LoadingSpinner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var spinnerImage: ImageView
    private lateinit var rotateAnimation: Animation

    init {
        // Configurar el fondo semitransparente - mucho más visible
        setBackgroundResource(R.drawable.loading_spinner_background)
        
        // Asegurar que ocupa todo el espacio disponible
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        
        // Centrar el contenido
        this.foregroundGravity = Gravity.CENTER
        
        // Establecer la elevación para que aparezca por encima de otros elementos
        elevation = 20f
        
        // Inflar el layout que contiene el ImageView
        inflate(context, R.layout.loading_spinner, this)
        
        // Obtener referencia al ImageView
        spinnerImage = findViewById(R.id.spinner_image)
        
        // Cargar la animación de rotación desde el recurso XML
        rotateAnimation = AnimationUtils.loadAnimation(context, R.anim.rotate)
        rotateAnimation.duration = 600 // Acelerar un poco la animación
        
        // Asegurar que el spinner es visible
        visibility = View.VISIBLE
        
        // Iniciar la animación
        startAnimation()
    }
    
    /**
     * Inicia la animación de rotación del spinner.
     */
    fun startAnimation() {
        spinnerImage.startAnimation(rotateAnimation)
    }
    
    /**
     * Detiene la animación de rotación del spinner.
     */
    fun stopAnimation() {
        spinnerImage.clearAnimation()
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Asegurar que la animación se reinicie cuando la vista se adjunte a la ventana
        visibility = View.VISIBLE
        startAnimation()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Detener la animación cuando la vista se separe de la ventana
        stopAnimation()
    }
} 