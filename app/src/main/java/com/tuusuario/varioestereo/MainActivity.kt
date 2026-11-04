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
import android.os.PowerManager
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.graphics.Color
import android.view.Gravity
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    private lateinit var sensorManager: SensorManager
    private var pressureSensor: Sensor? = null
    private lateinit var locationManager: LocationManager
    
    // Interfaz Visual (Dashboard)
    private lateinit var txtVz: TextView
    private lateinit var txtAltitude: TextView
    private lateinit var txtSpeed: TextView
    private lateinit var txtHeading: TextView

    private var wakeLock: PowerManager.WakeLock? = null

    // Variables de vuelo y filtrado avanzado
    private var baselinePressure: Float = 1013.25f
    private var lastAltitude: Float = 0f
    private var lastTime: Long = 0
    private @Volatile var verticalSpeed: Float = 0f
    private var groundSpeed: Float = 0f
    private var heading: Float = 0f

    // Motor de Audio Estéreo Continuo
    private var audioTrack: AudioTrack? = null
    private val sampleRate = 8000
    private var audioThread: Thread? = null
    private @Volatile var isAudioRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VarioEstereo::FlightWakeLock")
        wakeLock?.acquire()

        setupDashboardUI()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        setupStereoAudio()
        checkLocationPermission()
    }

    private fun setupDashboardUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(30, 30, 30, 30)
        }

        txtVz = TextView(this).apply {
            text = "0.00 m/s"
            textSize = 52f
            setTextColor(Color.GREEN)
            gravity = Gravity.CENTER
        }

        txtAltitude = TextView(this).apply {
            text = "Alt: 0 m"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        txtSpeed = TextView(this).apply {
            text = "GPS: 0 km/h"
            textSize = 24f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 10)
        }

        txtHeading = TextView(this).apply {
            text = "Rumbo: 0°"
            textSize = 24f
            setTextColor(Color.CYAN)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 20)
        }

        layout.addView(txtVz)
        layout.addView(txtAltitude)
        layout.addView(txtSpeed)
        layout.addView(txtHeading)

        setContentView(layout)
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
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        
        audioTrack?.play()
        startAudioLoop()
    }

    private fun startAudioLoop() {
        isAudioRunning = true
        audioThread = Thread {
            val chunkDurationMs = 50
            val numSamples = (sampleRate * chunkDurationMs) / 1000
            val buffer = ByteArray(numSamples * 4)
            var phase = 0.0

            while (isAudioRunning) {
                val vz = verticalSpeed
                
                // Umbral ajustado: subida activa a partir de +0.4 m/s, descenso fuerte si es menor a -1.0 m/s
                val isAscending = vz > 0.4f
                val isHardDescending = vz < -1.0f

                if (isAscending || isHardDescending) {
                    val freq = if (isAscending) {
                        (400 + (vz * 200)).toInt().coerceIn(400, 1200)
                    } else {
                        (200 - (abs(vz) * 20)).toInt().coerceIn(120, 200)
                    }

                    val leftVol = 1.0f
                    val rightVol = 1.0f

                    for (i in 0 until numSamples) {
                        val angle = 2.0 * Math.PI * freq * i / sampleRate + phase
                        val baseSample = (sin(angle) * 15000).toInt()
                        
                        val leftSample = (baseSample * leftVol).toInt().toShort()
                        val rightSample = (baseSample * rightVol).toInt().toShort()

                        buffer[4 * i] = (leftSample.toInt() and 0x00ff).toByte()
                        buffer[4 * i + 1] = ((leftSample.toInt() and 0xff00) ushr 8).toByte()
                        buffer[4 * i + 2] = (rightSample.toInt() and 0x00ff).toByte()
                        buffer[4 * i + 3] = ((rightSample.toInt() and 0xff00) ushr 8).toByte()
                    }
                    phase = (phase + 2.0 * Math.PI * freq * numSamples / sampleRate) % (2.0 * Math.PI)
                    audioTrack?.write(buffer, 0, buffer.size)
                } else {
                    buffer.fill(0)
                    audioTrack?.write(buffer, 0, buffer.size)
                    try {
                        Thread.sleep(50)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        }
        audioThread?.start()
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
    }

    override fun onDestroy() {
        super.onDestroy()
        isAudioRunning = false
        audioThread?.join(500)
        audioTrack?.stop()
        audioTrack?.release()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
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
                    verticalSpeed = (0.15f * instantaneousVz) + (0.85f * verticalSpeed)
                }
            }

            lastAltitude = altitude
            lastTime = currentTime

            txtVz.text = "%.2f m/s".format(verticalSpeed)
            txtVz.setTextColor(if (verticalSpeed >= 0) Color.GREEN else Color.RED)
            txtAltitude.text = "Alt: %.1f m".format(altitude)
            txtSpeed.text = "GPS: %.1f km/h".format(groundSpeed * 3.6f)
            txtHeading.text = "Rumbo: %.0f°".format(heading)
        }
    }

    override fun onLocationChanged(location: Location) {
        groundSpeed = location.speed
        if (location.hasBearing()) {
            heading = location.bearing
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
