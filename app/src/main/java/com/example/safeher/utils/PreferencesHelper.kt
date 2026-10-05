package com.example.safeher.utils

import android.content.Context
import android.content.SharedPreferences

object PreferencesHelper {
    private const val PREFS_NAME = "SafeHerPrefs"
    private const val CONTACTS_KEY = "emergency_contacts"
    private const val CAMERA_ON_SOS_KEY = "camera_on_sos_enabled"
    private const val SHAKE_ENABLED_KEY = "shake_trigger_enabled"

    // ── Emergency Contacts ───────────────────────────────────────────────────

    fun saveContacts(context: Context, contacts: List<String>) {
        prefs(context).edit().putString(CONTACTS_KEY, contacts.joinToString(",")).apply()
    }

    fun getContacts(context: Context): List<String> {
        val saved = prefs(context).getString(CONTACTS_KEY, "") ?: ""
        if (saved.isBlank()) return emptyList()
        return saved.split(",").filter { it.isNotBlank() }
    }

    // ── Camera on SOS Toggle ─────────────────────────────────────────────────

    fun setCameraOnSosEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(CAMERA_ON_SOS_KEY, enabled).apply()
    }

    /** Default: true — camera capture is on by default */
    fun isCameraOnSosEnabled(context: Context): Boolean =
        prefs(context).getBoolean(CAMERA_ON_SOS_KEY, true)

    // ── Shake Trigger Toggle ─────────────────────────────────────────────────

    fun setShakeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(SHAKE_ENABLED_KEY, enabled).apply()
    }

    /** Default: true — shake trigger is on by default */
    fun isShakeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(SHAKE_ENABLED_KEY, true)

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
