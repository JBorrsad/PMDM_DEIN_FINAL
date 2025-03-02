package com.example.perros

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Actividad de bienvenida después del registro exitoso.
 *
 * Esta actividad simple muestra un mensaje de bienvenida al usuario
 * después de completar el proceso de registro. Sirve como pantalla
 * de transición antes de redirigir al usuario a la pantalla principal.
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
