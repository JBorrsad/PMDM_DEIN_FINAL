package com.example.perros

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * # LoginActivity
 *
 * ## Funcionalidad principal
 *
 * Actividad de transición que sirve como confirmación visual tras un registro exitoso en la plataforma.
 * Proporciona un punto de interrupción en el flujo de navegación, permitiendo al usuario reconocer
 * que el proceso de registro ha concluido satisfactoriamente antes de ser redirigido a la funcionalidad
 * principal de la aplicación.
 *
 * ## Características técnicas implementadas
 *
 * - **Interfaz minimalista**: Implementa una vista simplificada con un único elemento textual centrado
 *   que maximiza la claridad del mensaje sin distracciones visuales.
 * - **Creación programática de UI**: Genera la interfaz completamente mediante código, sin utilizar
 *   archivos XML de layout, demostrando un enfoque alternativo al diseño de interfaces.
 * - **Internacionalización**: Utiliza recursos de strings para facilitar la traducción del mensaje
 *   a múltiples idiomas.
 * - **Componente de transición**: Actúa como puente visual entre el proceso de registro y la
 *   funcionalidad principal de la aplicación.
 *
 * ## Flujo de navegación
 *
 * ```
 * RegisterActivity → LoginActivity → MainActivity/MapsActivity
 * ```
 *
 * @property textView Componente principal que muestra el mensaje de bienvenida al usuario
 */
class LoginActivity : AppCompatActivity() {

    /**
     * Configura la vista de bienvenida con un mensaje centrado.
     *
     * Crea y configura un TextView con:
     * - Mensaje de bienvenida
     * - Tamaño de texto grande (24sp)
     * - Padding para centrar el contenido
     *
     * @param savedInstanceState Estado guardado de la actividad
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this)
        textView.setText(R.string.bienvenido)
        textView.textSize = 24f
        textView.setPadding(50, 200, 50, 50)

        setContentView(textView)
    }
}
