package com.alcedo.studio.privacy

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.privacyDataStore by preferencesDataStore(name = "alcedo_privacy")

/**
 * Privacy management. Tracks the user's first-run consent (required in some
 * jurisdictions before any data processing) and per-feature toggles (cloud LLM,
 * telemetry, on-device AI). Persisted in DataStore so it survives reinstalls
 * within backup/restore.
 */
@Singleton
class PrivacyManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    object Keys {
        val CONSENT_GIVEN = booleanPreferencesKey("consent_given")
        val CONSENT_TIMESTAMP = booleanPreferencesKey("consent_timestamp_set")
        val CLOUD_LLM_ALLOWED = booleanPreferencesKey("cloud_llm_allowed")
        val TELEMETRY_ALLOWED = booleanPreferencesKey("telemetry_allowed")
        val ON_DEVICE_AI_ALLOWED = booleanPreferencesKey("on_device_ai_allowed")
    }

    /** Flow of the current consent + toggle state. */
    val state: Flow<PrivacyState> = context.privacyDataStore.data.map { prefs ->
        PrivacyState(
            consentGiven = prefs[Keys.CONSENT_GIVEN] ?: false,
            cloudLlmAllowed = prefs[Keys.CLOUD_LLM_ALLOWED] ?: false,
            telemetryAllowed = prefs[Keys.TELEMETRY_ALLOWED] ?: false,
            onDeviceAiAllowed = prefs[Keys.ON_DEVICE_AI_ALLOWED] ?: true,
        )
    }

    /** Record the user's consent decision. */
    suspend fun setConsent(given: Boolean) {
        context.privacyDataStore.edit { it[Keys.CONSENT_GIVEN] = given }
    }

    /** Toggle whether cloud LLM features may be used. */
    suspend fun setCloudLlmAllowed(allowed: Boolean) {
        context.privacyDataStore.edit { it[Keys.CLOUD_LLM_ALLOWED] = allowed }
    }

    /** Toggle telemetry. */
    suspend fun setTelemetryAllowed(allowed: Boolean) {
        context.privacyDataStore.edit { it[Keys.TELEMETRY_ALLOWED] = allowed }
    }

    /** Toggle on-device AI. */
    suspend fun setOnDeviceAiAllowed(allowed: Boolean) {
        context.privacyDataStore.edit { it[Keys.ON_DEVICE_AI_ALLOWED] = allowed }
    }

    data class PrivacyState(
        val consentGiven: Boolean,
        val cloudLlmAllowed: Boolean,
        val telemetryAllowed: Boolean,
        val onDeviceAiAllowed: Boolean,
    ) {
        /** Cloud LLM may be used only with consent + the cloud toggle. */
        val canUseCloudLlm: Boolean get() = consentGiven && cloudLlmAllowed
    }
}
