package com.example.safeher.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.safeher.utils.LocationHelper
import com.example.safeher.utils.PreferencesHelper

class JourneyService : Service() {

    private val CHANNEL_ID = "JourneyServiceChannel"
    private val handler = Handler(Looper.getMainLooper())
    private val FIVE_MINUTES = 5 * 60 * 1000L

    // This runnable sends the periodic 5-minute SMS update
    private val periodicSmsRunnable = object : Runnable {
        override fun run() {
            sendPeriodicUpdate()
            handler.postDelayed(this, FIVE_MINUTES)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Journey tracking is active...")
        
        // Start as foreground service to keep it alive
        startForeground(1, notification)

        // Start the 5-minute periodic SMS loop
        handler.post(periodicSmsRunnable)

        // TODO: In the future, this is where we will start a LocationCallback 
        // to check every 1 minute if the user has deviated from the OSRM route.

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the timers when the journey ends
        handler.removeCallbacks(periodicSmsRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun sendPeriodicUpdate() {
        Log.d("JourneyService", "Sending 5-minute periodic SMS update...")
        val contacts = PreferencesHelper.getContacts(this)
        if (contacts.isNotEmpty()) {
            LocationHelper.sendSosWithLocation(
                context = this,
                phoneNumbers = contacts,
                baseMessage = "[JOURNEY UPDATE] I am currently on my way. This is my live location:"
            )
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeHer Journey Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Journey Tracking Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
