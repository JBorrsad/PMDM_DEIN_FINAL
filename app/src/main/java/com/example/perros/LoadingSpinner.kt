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
 * # LoadingSpinner
 * 
 * Componente visual personalizado que proporciona un indicador de carga animado
 * para el sistema de monitorización de mascotas.
 * 
 * ## Funcionalidad principal
 * Este componente visual proporciona feedback visual durante procesos asíncronos, permitiendo:
 * - Indicar al usuario que se está ejecutando un proceso en segundo plano
 * - Bloquear parcialmente la interfaz durante operaciones críticas
 * - Mejorar la percepción de respuesta de la aplicación
 * - Unificar la experiencia de carga en toda la aplicación
 * - Proporcionar un elemento visualmente atractivo durante tiempos de espera
 * 
 * ## Características técnicas implementadas:
 * - **Animación fluida**: Rotación continua con duración personalizada de 600ms
 * - **Fondo semitransparente**: Overlay con opacidad parcial para enfoque visual
 * - **Elevación Z**: Posicionamiento por encima de otros elementos de la UI (z=20)
 * - **Gestión automática**: Control del ciclo de vida vinculado a la visibilidad del componente
 * - **Compatibilidad con XML**: Soporte para instanciación desde layouts o código
 * - **Gravedad ajustada**: Centrado automático en el contenedor padre
 * 
 * ## Ciclo de vida de la animación:
 * ```
 * Constructor → onAttachedToWindow → startAnimation → [visible al usuario] 
 *                                                     ↓
 * [destrucción]  ← onDetachedFromWindow ← stopAnimation
 * ```
 * 
 * Este spinner se integra perfectamente con el lenguaje visual de la aplicación,
 * proporcionando una experiencia cohesiva incluso durante operaciones de carga.
 *
 * @property spinnerImage ImageView central que muestra el ícono giratorio
 * @property rotateAnimation Animación de rotación aplicada al spinner
 */
class LoadingSpinner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val spinnerImage: ImageView
    private val rotateAnimation: Animation

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