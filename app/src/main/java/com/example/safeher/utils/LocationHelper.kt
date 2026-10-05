package com.example.safeher.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

object LocationHelper {

    /**
     * Gets the current location, formats the SOS message, and sends the SMS.
     */
    @SuppressLint("MissingPermission") // We check permissions manually before calling this
    fun sendSosWithLocation(
        context: Context,
        phoneNumbers: List<String>,
        baseMessage: String = "[EMERGENCY SOS] I need help!"
    ) {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // If no location permissions, just send the SMS without location
        if (!hasFineLocation && !hasCoarseLocation) {
            val finalMessage = "$baseMessage\n\n(GPS permission was denied, unable to share location)"
            SmsHelper.sendEmergencySms(context, phoneNumbers, finalMessage)
            return
        }

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, "Acquiring GPS Location...", Toast.LENGTH_SHORT).show()
        }

        val fusedLocationClient: FusedLocationProviderClient = 
            LocationServices.getFusedLocationProviderClient(context)

        // Try to get high-accuracy current location
        val cancellationTokenSource = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location: Location? ->
            if (location != null) {
                // We got the location! Create the Google Maps link
                val mapsLink = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
                val finalMessage = "$baseMessage\n\nMy current location is:\n$mapsLink"
                
                // Send the SMS
                SmsHelper.sendEmergencySms(context, phoneNumbers, finalMessage)
            } else {
                // Location is null (GPS might be off)
                val finalMessage = "$baseMessage\n\n(Unable to acquire GPS signal. Last known location unavailable)"
                SmsHelper.sendEmergencySms(context, phoneNumbers, finalMessage)
            }
        }.addOnFailureListener { e ->
            e.printStackTrace()
            val finalMessage = "$baseMessage\n\n(Error acquiring GPS location)"
            SmsHelper.sendEmergencySms(context, phoneNumbers, finalMessage)
        }
    }
}
