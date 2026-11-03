package com.tuusuario.varioestereo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlin.math.pow
import kotlin.math.sin

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    private lateinit var sensorManager: SensorManager
    private var pressureSensor: Sensor? = null
    private lateinit var locationManager: LocationManager
    private lateinit var textView: TextView

    // Variables de vuelo
    private var baselinePressure: Float = 1013.25f
    private var lastAltitude: Float = 0f
    private var lastTime: Long = 0
    private var verticalSpeed: Float = 0f
    private var groundSpeed: Float = 0f
    private var heading: Float = 0f

    // Motor de Audio Estéreo
    private var audioTrack: AudioTrack? = null
    private val sampleRate = 8000
    private var lastBeepTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        textView = TextView(this).apply {
            text = "Iniciando Vario Estéreo (GPS + Audio Estéreo)..."
            textSize = 18f
            setPadding(40, 40, 40, 40)
        }
        setContentView(textView)

        // Sensores y Ubicación
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (pressureSensor == null) {
            textView.text = "Error: Este dispositivo no detecta un barómetro."
        }

        setupStereoAudio()
        checkLocationPermission()
    }

    private fun setupStereoAudio() {
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
        
        audioTrack?.play()
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        } else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this)
        } catch (e: SecurityException) {
            e.printStackTrace()
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
        try {
            locationManager.removeUpdates(this)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
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

            // Generar audio estéreo (por defecto paneo centrado o sesgado según dinámica)
            handleStereoVarioAudio(verticalSpeed, currentTime)

            textView.text = """
                ✈️ VARIO ESTÉREO (GPS + AUDIO)
                
                Presión: %.2f hPa
                Altitud: %.1f m
                Vz: %.2f m/s
                Velocidad GPS: %.1f km/h
                Rumbo: %.0f°
            """.trimIndent().format(pressure, altitude, verticalSpeed, groundSpeed * 3.6f, heading)
        }
    }

    private fun handleStereoVarioAudio(vz: Float, currentTime: Long) {
        if (vz > 0.1f) {
            val interval = (400f / (vz + 1f)).toLong().coerceIn(50, 400)
            
            if (currentTime - lastBeepTime > interval) {
                val freq = (400 + (vz * 200)).toInt().coerceIn(400, 1200)
                
                // Ejemplo de paneo estéreo: si quisieras inclinar el sonido hacia la izquierda o derecha 
                // (por ahora balance 1.0 para ambos oídos, listo para integrar giros)
                playStereoTone(freq, 100, leftVolume = 1.0f, rightVolume = 1.0f)
                lastBeepTime = currentTime
            }
        }
    }

    private fun playStereoTone(freq: Int, durationMs: Int, leftVolume: Float, rightVolume: Float) {
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ByteArray(numSamples * 4) // 4 bytes por frame en estéreo 16-bit (2 para Izq, 2 para Der)
        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * i / (sampleRate / freq)
            val baseSample = (sin(angle) * 32767).toInt()
            
            val leftSample = (baseSample * leftVolume).toInt().toShort()
            val rightSample = (baseSample * rightVolume).toInt().toShort()

            // Canal Izquierdo
            buffer[4 * i] = (leftSample.toInt() and 0x00ff).toByte()
            buffer[4 * i + 1] = ((leftSample.toInt() and 0xff00) ushr 8).toByte()
            // Canal Derecho
            buffer[4 * i + 2] = (rightSample.toInt() and 0x00ff).toByte()
            buffer[4 * i + 3] = ((rightSample.toInt() and 0xff00) ushr 8).toByte()
        }
        audioTrack?.write(buffer, 0, buffer.size)
    }

    override fun onLocationChanged(location: Location) {
        groundSpeed = location.speed
        if (location.hasBearing()) {
            heading = location.bearing
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
