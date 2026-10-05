package com.example.safeher.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.safeher.utils.CameraHelper
import com.example.safeher.utils.LocationHelper
import com.example.safeher.utils.PreferencesHelper

class SafetyService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    
    private var lastUpdate: Long = 0
    private var last_x = 0f
    private var last_y = 0f
    private var last_z = 0f
    private val SHAKE_THRESHOLD = 3000 // Adjust as needed
    
    private var volumeKeyPressCount = 0
    private var lastVolumeKeyPressTime: Long = 0
    
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_LOW) {
                context?.let {
                    val contacts = PreferencesHelper.getContacts(it)
                    LocationHelper.sendSosWithLocation(it, contacts, "[BATTERY LOW SOS] My phone is about to die!")
                }
            }
        }
    }
    
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                handleVolumeKey()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            val notification = NotificationCompat.Builder(this, "SafetyServiceChannel")
                .setContentTitle("SafeHer Shield Active")
                .setContentText("Listening for Shake and Volume triggers")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
                
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
            
        // Initialize Shake Detection
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        
        // Initialize Receivers
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_LOW), RECEIVER_EXPORTED)
            registerReceiver(volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_LOW))
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        }
    }
    
    private fun handleVolumeKey() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastVolumeKeyPressTime > 3000) {
            volumeKeyPressCount = 0
        }
        volumeKeyPressCount++
        lastVolumeKeyPressTime = currentTime
        
        if (volumeKeyPressCount >= 4) { // 4 presses to avoid accidental triggers
            triggerSOS("[SILENT SOS] Volume key trigger activated!")
            volumeKeyPressCount = 0
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(volumeReceiver) } catch (_: Exception) {}
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val curTime = System.currentTimeMillis()
        if ((curTime - lastUpdate) > 100) {
            val diffTime = curTime - lastUpdate
            if (diffTime <= 0) return
            lastUpdate = curTime

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val deltaX = x - last_x
            val deltaY = y - last_y
            val deltaZ = z - last_z
            val speed = Math.sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()) / diffTime * 10000
            if (speed > SHAKE_THRESHOLD && PreferencesHelper.isShakeEnabled(this)) {
                triggerSOS("[SHAKE SOS] Shake trigger activated!")
            }

            last_x = x
            last_y = y
            last_z = z
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    
    private var lastSosTime: Long = 0
    private fun triggerSOS(messagePrefix: String) {
        val curTime = System.currentTimeMillis()
        if (curTime - lastSosTime > 30000) { // 30-second cooldown
            lastSosTime = curTime
            val contacts = PreferencesHelper.getContacts(this)
            LocationHelper.sendSosWithLocation(this, contacts, messagePrefix)

            // 📸 Silently capture photos from both cameras if toggle is ON
            CameraHelper.capturePhotosOnSos(this)

            // Vibrate to confirm SOS was sent
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "SafetyServiceChannel",
                "Safety Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
