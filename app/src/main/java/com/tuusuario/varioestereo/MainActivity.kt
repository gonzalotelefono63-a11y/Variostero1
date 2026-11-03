package com.tuusuario.varioestereo

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Creamos un texto simple para que la pantalla no esté vacía
        val textView = TextView(this).apply {
            text = "¡Vario Estéreo en Marcha!"
            textSize = 24f
            setPadding(50, 50, 50, 50)
        }
        setContentView(textView)
    }
}
