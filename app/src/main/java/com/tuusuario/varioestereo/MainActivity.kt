package com.tuusuario.varioestereo

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.pow

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var pressureSensor: Sensor? = null
    private lateinit var textView: TextView

    // Variables de cálculo de vuelo
    private var baselinePressure: Float = 1013.25f // Presión estándar a nivel del mar
    private var lastAltitude: Float = 0f
    private var lastTime: Long = 0
    private var verticalSpeed: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        textView = TextView(this).apply {
            text = "Calibrando altitud y Vz..."
            textSize = 20f
            setPadding(50, 50, 50, 50)
        }
        setContentView(textView)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        if (pressureSensor == null) {
            textView.text = "Error: Este dispositivo no detecta un barómetro."
        }
    }

    override fun onResume() {
        super.onResume()
        pressureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_PRESSURE) {
            val pressure = event.values[0] // Presión actual en hPa

            // Cálculo de altitud barométrica en metros
            val altitude = 44330f * (1.0f - (pressure / baselinePressure).pow(0.1903f))

            // Cálculo de la velocidad vertical (Vz) en m/s
            val currentTime = System.currentTimeMillis()
            if (lastTime != 0L) {
                val dt = (currentTime - lastTime) / 1000.0f // Intervalo en segundos
                if (dt > 0) {
                    val instantaneousVz = (altitude - lastAltitude) / dt
                    // Filtro de suavizado exponencial para mitigar el ruido del sensor
                    verticalSpeed = (0.2f * instantaneousVz) + (0.8f * verticalSpeed)
                }
            }

            lastAltitude = altitude
            lastTime = currentTime

            // Actualizar la interfaz con los datos de vuelo en tiempo real
            textView.text = """
                ✈️ VARIO ESTÉREO - VUELO
                
                Presión: %.2f hPa
                Altitud: %.1f m
                Velocidad Vertical (Vz): %.2f m/s
            """.trimIndent().format(pressure, altitude, verticalSpeed)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
