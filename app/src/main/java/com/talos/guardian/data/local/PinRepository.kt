package com.talos.guardian.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PinRepository(context: Context) {

    private val sharedPreferences: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            "secure_pin_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isPinSet(): Boolean {
        return sharedPreferences.contains(KEY_PIN)
    }

    fun setPin(pin: String) {
        sharedPreferences.edit().putString(KEY_PIN, pin).apply()
    }

    fun validatePin(inputPin: String): Boolean {
        val storedPin = sharedPreferences.getString(KEY_PIN, null)
        return storedPin == inputPin
    }

    fun clearPin() {
        sharedPreferences.edit().remove(KEY_PIN).apply()
    }

    companion object {
        private const val KEY_PIN = "app_lock_pin"
    }
}
