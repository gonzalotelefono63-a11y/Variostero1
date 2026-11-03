package com.tuusuario.varioestereo

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var pressureSensor: Sensor? = null
    private lateinit var textView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        textView = TextView(this).apply {
            text = "Buscando sensor barométrico..."
            textSize = 22f
            setPadding(50, 50, 50, 50)
        }
        setContentView(textView)

        // Inicializar el administrador de sensores del S9
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        if (pressureSensor == null) {
            textView.text = "Error: Este dispositivo no detecta un barómetro."
        }
    }

    override fun onResume() {
        super.onResume()
        // Activar la escucha del sensor cuando la app está en primer plano
        pressureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        // Pausar la escucha para ahorrar batería cuando la app se minimiza
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_PRESSURE) {
            val pressure = event.values[0] // Presión en hPa (hectopascales)
            textView.text = "¡Vario Estéreo Activo!\n\nPresión barométrica:\n$pressure hPa"
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Sin acción necesaria por ahora
    }
}
