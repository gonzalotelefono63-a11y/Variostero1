package com.tuusuario.varioestereo

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.pow
import kotlin.math.sin

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var pressureSensor: Sensor? = null
    private lateinit var textView: TextView

    // Variables de cálculo de vuelo
    private var baselinePressure: Float = 1013.25f
    private var lastAltitude: Float = 0f
    private var lastTime: Long = 0
    private var verticalSpeed: Float = 0f

    // Motor de Audio (Bips del Vario)
    private var audioTrack: AudioTrack? = null
    private val sampleRate = 8000
    private var lastBeepTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        textView = TextView(this).apply {
            text = "Iniciando Vario Estéreo con Audio..."
            textSize = 20f
            setPadding(50, 50, 50, 50)
        }
        setContentView(textView)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        if (pressureSensor == null) {
            textView.text = "Error: Este dispositivo no detecta un barómetro."
        }

        setupAudio()
    }

    private fun setupAudio() {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        
        audioTrack?.play()
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
        audioTrack?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioTrack?.release()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_PRESSURE) {
            val pressure = event.values[0]

            val altitude = 44330f * (1.0f - (pressure / baselinePressure).pow(0.1903f))

            val currentTime = System.currentTimeMillis()
            if (lastTime != 0L) {
                val dt = (currentTime - lastTime) / 1000.0f
                if (dt > 0) {
                    val instantaneousVz = (altitude - lastAltitude) / dt
                    verticalSpeed = (0.2f * instantaneousVz) + (0.8f * verticalSpeed)
                }
            }

            lastAltitude = altitude
            lastTime = currentTime

            // Lógica de Sonido (Bips de Vuelo)
            handleVarioAudio(verticalSpeed, currentTime)

            textView.text = """
                ✈️ VARIO ESTÉREO - AUDIO ACTIVO
                
                Presión: %.2f hPa
                Altitud: %.1f m
                Velocidad Vertical (Vz): %.2f m/s
            """.trimIndent().format(pressure, altitude, verticalSpeed)
        }
    }

    private fun handleVarioAudio(vz: Float, currentTime: Long) {
        // Umbral de ascenso para emitir bips (ej: subiendo a más de 0.1 m/s)
        if (vz > 0.1f) {
            // A mayor velocidad, menor intervalo entre bips (bips más rápidos)
            val interval = (400f / (vz + 1f)).toLong().coerceIn(50, 400)
            
            if (currentTime - lastBeepTime > interval) {
                // Frecuencia del tono sube según la velocidad vertical (de 400Hz a 1200Hz)
                val freq = (400 + (vz * 200)).toInt().coerceIn(400, 1200)
                playTone(freq, 100) // Tono y duración en ms
                lastBeepTime = currentTime
            }
        }
    }

    private fun playTone(freq: Int, durationMs: Int) {
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * i / (sampleRate / freq)
            val sample = (sin(angle) * 32767).toInt().toShort()
            buffer[2 * i] = (sample.toInt() and 0x00ff).toByte()
            buffer[2 * i + 1] = ((sample.toInt() and 0xff00) ushr 8).toByte()
        }
        audioTrack?.write(buffer, 0, buffer.size)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
