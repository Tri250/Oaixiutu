package com.alcedo.studio.domain.service

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure storage for AI provider API keys and credentials.
 * Ported from desktop ai_credential_store.cpp
 * Uses EncryptedSharedPreferences for secure storage.
 */
class AiCredentialStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "alcedo_ai_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun storeApiKey(provider: String, apiKey: String) {
        prefs.edit().putString("key_$provider", apiKey).apply()
    }

    fun getApiKey(provider: String): String? {
        return prefs.getString("key_$provider", null)
    }

    fun removeApiKey(provider: String) {
        prefs.edit().remove("key_$provider").apply()
    }

    fun hasApiKey(provider: String): Boolean {
        return prefs.contains("key_$provider")
    }

    fun listProviders(): List<String> {
        return prefs.all.keys
            .filter { it.startsWith("key_") }
            .map { it.removePrefix("key_") }
    }

    fun storeEndpoint(provider: String, endpoint: String) {
        prefs.edit().putString("endpoint_$provider", endpoint).apply()
    }

    fun getEndpoint(provider: String): String? {
        return prefs.getString("endpoint_$provider", null)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
