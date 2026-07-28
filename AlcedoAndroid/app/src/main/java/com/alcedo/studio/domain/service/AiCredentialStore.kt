package com.alcedo.studio.domain.service

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted credential store for AI providers (API keys). Backed by
 * EncryptedSharedPreferences (AES-GCM 256, AES-SIV-CMAC256) using the Android
 * Keystore master key. Keys never touch plain SharedPreferences or logs.
 */
@Singleton
class AiCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.onFailure { Log.e(TAG, "EncryptedSharedPreferences unavailable", it) }.getOrNull()
    }

    /** Store an API key for [providerId]. */
    fun setApiKey(providerId: String, apiKey: String) {
        prefs?.edit()?.putString(key(providerId), apiKey)?.apply()
    }

    /** Retrieve the API key for [providerId], or null. */
    fun getApiKey(providerId: String): String? = prefs?.getString(key(providerId), null)

    /** True when a key is stored for [providerId]. */
    fun hasApiKey(providerId: String): Boolean = prefs?.contains(key(providerId)) ?: false

    /** Remove the stored key for [providerId]. */
    fun removeApiKey(providerId: String) {
        prefs?.edit()?.remove(key(providerId))?.apply()
    }

    /** All provider ids with a stored key. */
    fun configuredProviders(): Set<String> =
        prefs?.all?.keys?.map { it.removePrefix(PREFIX) }?.toSet() ?: emptySet()

    /** Whether the encrypted store initialised successfully. */
    val isAvailable: Boolean get() = prefs != null

    private fun key(providerId: String): String = "$PREFIX$providerId"

    companion object {
        private const val TAG = "AiCredentialStore"
        private const val FILE_NAME = "alcedo_ai_creds"
        private const val PREFIX = "key_"
    }
}
