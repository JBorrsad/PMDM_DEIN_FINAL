package com.example.perros

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this)
        textView.text = "¡Bienvenido a la app!"
        textView.textSize = 24f
        textView.setPadding(50, 200, 50, 50)

        setContentView(textView)
    }
}
