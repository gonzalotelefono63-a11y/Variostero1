package com.tuusuario.varioestereo

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlin.math.sin

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var barometer: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private lateinit var txtAltitud: TextView
    private lateinit var txtVario: TextView
    private lateinit var btnIniciar: Button

    private var isFlying = false
    private var basePressure = 0f
    private var currentPressure = 1013.25f
    
    // Audio Track para sonido Estéreo
    private lateinit var audioTrack: AudioTrack
    private val sampleRate = 16000
    private var audioBuffer = ByteArray(sampleRate) // 1 segundo de buffer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtAltitud = findViewById(R.id.txtAltitud)
        txtVario = findViewById(R.id.txtVario)
        btnIniciar = findViewById(R.id.btnIniciar)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        setupAudio()

        btnIniciar.setOnClickListener {
            isFlying = !isFlying
            if (isFlying) {
                basePressure = currentPressure
                btnIniciar.text = "Detener Vuelo"
            } else {
                btnIniciar.text = "Iniciar Vuelo"
            }
        }
    }

    private fun setupAudio() {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
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
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack.play()
    }

    override fun onResume() {
        super.onResume()
        barometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_PRESSURE -> {
                currentPressure = event.values[0]
                // Cálculo simple de altitud AGL respecto al despegue
                if (isFlying && basePressure > 0) {
                    val altAbsolute = 44330 * (1.0 - Math.pow((currentPressure / 1013.25).toDouble(), 0.1903)).toFloat()
                    val altBase = 44330 * (1.0 - Math.pow((basePressure / 1013.25).toDouble(), 0.1903)).toFloat()
                    val agl = altAbsolute - altBase
                    txtAltitud.text = "AGL: ${agl.toInt()} m"
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // Aquí se procesaría la IMU para el vario instantáneo y estéreo
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
