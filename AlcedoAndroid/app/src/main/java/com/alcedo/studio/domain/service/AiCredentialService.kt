package com.alcedo.studio.domain.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.alcedo.studio.data.model.AiCredential
import com.alcedo.studio.data.model.AiProviderProfile
import com.alcedo.studio.data.model.AiProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AI credential management service.
 *
 * Stores API keys for OpenAI, Anthropic, and Doubao (火山方舟) providers
 * using EncryptedSharedPreferences. Never logs API keys.
 */
class AiCredentialService(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "alcedo_ai_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val json = Json { ignoreUnknownKeys = true }

    // ── Default Provider Profiles ──

    val defaultProfiles: List<AiProviderProfile> = listOf(
        AiProviderProfile(
            providerId = "openai",
            providerName = "OpenAI",
            providerType = AiProviderType.OPENAI,
            defaultBaseUrl = "https://api.openai.com/v1",
            defaultModel = "gpt-4o",
            maxTokens = 4096
        ),
        AiProviderProfile(
            providerId = "anthropic",
            providerName = "Anthropic",
            providerType = AiProviderType.ANTHROPIC,
            defaultBaseUrl = "https://api.anthropic.com/v1",
            defaultModel = "claude-3-5-sonnet-20241022",
            maxTokens = 4096
        ),
        AiProviderProfile(
            providerId = "doubao",
            providerName = "火山方舟 (Doubao)",
            providerType = AiProviderType.DOUBAO,
            defaultBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
            defaultModel = "doubao-vision-pro-32k",
            maxTokens = 4096
        )
    )

    // ── Credential CRUD ──

    suspend fun saveCredential(credential: AiCredential) = withContext(Dispatchers.IO) {
        val key = credentialKey(credential.providerId)
        val jsonStr = json.encodeToString(credential)
        encryptedPrefs.edit().putString(key, jsonStr).apply()
    }

    suspend fun getCredential(providerId: String): AiCredential? = withContext(Dispatchers.IO) {
        val key = credentialKey(providerId)
        val jsonStr = encryptedPrefs.getString(key, null) ?: return@withContext null
        try {
            json.decodeFromString<AiCredential>(jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAllCredentials(): List<AiCredential> = withContext(Dispatchers.IO) {
        defaultProfiles.mapNotNull { profile ->
            getCredential(profile.providerId)
        }
    }

    suspend fun deleteCredential(providerId: String) = withContext(Dispatchers.IO) {
        val key = credentialKey(providerId)
        encryptedPrefs.edit().remove(key).apply()
    }

    suspend fun setActiveCredential(providerId: String) = withContext(Dispatchers.IO) {
        // Deactivate all, then activate the selected one
        val all = getAllCredentials()
        for (cred in all) {
            saveCredential(cred.copy(isActive = cred.providerId == providerId))
        }
    }

    suspend fun getActiveCredential(): AiCredential? = withContext(Dispatchers.IO) {
        getAllCredentials().find { it.isActive }
    }

    // ── API Key helpers (never log) ──

    suspend fun getApiKey(providerId: String): String? {
        val cred = getCredential(providerId) ?: return null
        return cred.apiKey
    }

    suspend fun getBaseUrl(providerId: String): String? {
        val cred = getCredential(providerId) ?: return null
        return cred.apiBaseUrl.ifEmpty {
            defaultProfiles.find { it.providerId == providerId }?.defaultBaseUrl
        }
    }

    /**
     * Validate that an API key has the expected format (does NOT log the key).
     */
    fun validateKeyFormat(providerType: AiProviderType, apiKey: String): Boolean {
        if (apiKey.isBlank()) return false
        return when (providerType) {
            AiProviderType.OPENAI -> apiKey.startsWith("sk-") && apiKey.length >= 40
            AiProviderType.ANTHROPIC -> apiKey.startsWith("sk-ant-") && apiKey.length >= 50
            AiProviderType.DOUBAO -> apiKey.length >= 32
            AiProviderType.CUSTOM -> apiKey.length >= 16
        }
    }

    // ── Internal ──

    private fun credentialKey(providerId: String): String {
        return "credential_$providerId"
    }

    companion object {
        /**
         * Mask an API key for safe display (never log the full key).
         */
        fun maskApiKey(key: String): String {
            if (key.length <= 8) return "****"
            return key.take(4) + "****" + key.takeLast(4)
        }
    }
}