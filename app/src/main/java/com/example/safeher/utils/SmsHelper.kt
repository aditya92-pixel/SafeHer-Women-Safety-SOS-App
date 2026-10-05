package com.example.safeher.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.content.ContextCompat

object SmsHelper {

    private fun showToast(context: Context, text: String, length: Int = Toast.LENGTH_SHORT) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, text, length).show()
        }
    }

    /**
     * Sends an SMS message to a list of emergency contacts.
     * Checks for permissions first.
     */
    fun sendEmergencySms(context: Context, phoneNumbers: List<String>, message: String) {
        // 1. Check if we have the SEND_SMS permission
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            showToast(context, "SMS Permission is missing! Cannot send SOS.", Toast.LENGTH_LONG)
            return
        }

        val validNumbers = phoneNumbers.map { it.trim() }.filter { it.isNotBlank() }

        if (validNumbers.isEmpty()) {
            showToast(context, "No emergency contacts configured.", Toast.LENGTH_SHORT)
            return
        }

        try {
            // 2. Get the default SmsManager
            val smsManager: SmsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // 3. Send SMS to each contact
            for (phoneNumber in validNumbers) {
                // If the message is long, we need to divide it into parts
                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                }
            }
            
            showToast(context, "Emergency SMS Sent to ${validNumbers.size} contact(s)!", Toast.LENGTH_SHORT)

        } catch (e: Exception) {
            e.printStackTrace()
            showToast(context, "Failed to send SMS: ${e.message}", Toast.LENGTH_LONG)
        }
    }
}
