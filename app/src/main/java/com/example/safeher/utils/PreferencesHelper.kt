package com.example.safeher.utils

import android.content.Context
import android.content.SharedPreferences

object PreferencesHelper {
    private const val PREFS_NAME = "SafeHerPrefs"
    private const val CONTACTS_KEY = "emergency_contacts"

    fun saveContacts(context: Context, contacts: List<String>) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(CONTACTS_KEY, contacts.joinToString(",")).apply()
    }

    fun getContacts(context: Context): List<String> {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(CONTACTS_KEY, "")
        if (saved.isNullOrEmpty()) return emptyList()
        return saved.split(",").filter { it.isNotBlank() }
    }
}
