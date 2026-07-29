package com.alcedo.studio.privacy

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
 *
 * Consent is a hard gate: callers MUST check [PrivacyState.canCollectData] (or
 * call [requireConsent]) before collecting telemetry, sending crash reports, or
 * invoking cloud features. Revoking consent via [setConsent] with `false`
 * immediately disables cloud LLM and telemetry, stopping all outbound data
 * collection, and the change is reflected by the reactive [state] flow.
 */
@Singleton
class PrivacyManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    object Keys {
        val CONSENT_GIVEN = booleanPreferencesKey("consent_given")
        val CONSENT_TIMESTAMP = longPreferencesKey("consent_timestamp")
        val CLOUD_LLM_ALLOWED = booleanPreferencesKey("cloud_llm_allowed")
        val TELEMETRY_ALLOWED = booleanPreferencesKey("telemetry_allowed")
        val ON_DEVICE_AI_ALLOWED = booleanPreferencesKey("on_device_ai_allowed")
    }

    /** Flow of the current consent + toggle state. */
    val state: Flow<PrivacyState> = context.privacyDataStore.data.map { prefs ->
        PrivacyState(
            consentGiven = prefs[Keys.CONSENT_GIVEN] ?: false,
            consentTimestamp = prefs[Keys.CONSENT_TIMESTAMP]?.takeIf { it > 0 },
            cloudLlmAllowed = prefs[Keys.CLOUD_LLM_ALLOWED] ?: false,
            telemetryAllowed = prefs[Keys.TELEMETRY_ALLOWED] ?: false,
            onDeviceAiAllowed = prefs[Keys.ON_DEVICE_AI_ALLOWED] ?: true,
        )
    }

    /**
     * Record the user's consent decision and timestamp it. Revoking consent
     * ([given] = false) immediately disables cloud LLM and telemetry so all
     * outbound data collection stops, regardless of the previous per-feature
     * toggles.
     */
    suspend fun setConsent(given: Boolean) {
        context.privacyDataStore.edit { prefs ->
            prefs[Keys.CONSENT_GIVEN] = given
            prefs[Keys.CONSENT_TIMESTAMP] = System.currentTimeMillis()
            if (!given) {
                // Revoking consent stops all data collection immediately.
                prefs[Keys.CLOUD_LLM_ALLOWED] = false
                prefs[Keys.TELEMETRY_ALLOWED] = false
            }
        }
    }

    /**
     * Throw [ConsentRequiredException] unless the user has granted consent.
     * Call this before any data collection (telemetry, crash upload, cloud LLM).
     */
    suspend fun requireConsent() {
        if (!currentConsentGiven()) throw ConsentRequiredException()
    }

    /** True when the user may currently collect/share data (consent granted). */
    suspend fun canCollectData(): Boolean = currentConsentGiven()

    /** Toggle whether cloud LLM features may be used (consent still required). */
    suspend fun setCloudLlmAllowed(allowed: Boolean) {
        context.privacyDataStore.edit { prefs ->
            // Cloud LLM implies outbound data; never allow it without consent.
            prefs[Keys.CLOUD_LLM_ALLOWED] = allowed && (prefs[Keys.CONSENT_GIVEN] ?: false)
        }
    }

    /** Toggle telemetry (consent still required). */
    suspend fun setTelemetryAllowed(allowed: Boolean) {
        context.privacyDataStore.edit { prefs ->
            prefs[Keys.TELEMETRY_ALLOWED] = allowed && (prefs[Keys.CONSENT_GIVEN] ?: false)
        }
    }

    /** Toggle on-device AI. */
    suspend fun setOnDeviceAiAllowed(allowed: Boolean) {
        context.privacyDataStore.edit { it[Keys.ON_DEVICE_AI_ALLOWED] = allowed }
    }

    private suspend fun currentConsentGiven(): Boolean =
        context.privacyDataStore.data.first()[Keys.CONSENT_GIVEN] ?: false

    data class PrivacyState(
        val consentGiven: Boolean,
        val consentTimestamp: Long?,
        val cloudLlmAllowed: Boolean,
        val telemetryAllowed: Boolean,
        val onDeviceAiAllowed: Boolean,
    ) {
        /** Cloud LLM may be used only with consent + the cloud toggle. */
        val canUseCloudLlm: Boolean get() = consentGiven && cloudLlmAllowed
        /** Telemetry may be sent only with consent + the telemetry toggle. */
        val canCollectTelemetry: Boolean get() = consentGiven && telemetryAllowed
        /** On-device AI may run only with consent + the on-device toggle. */
        val canUseOnDeviceAi: Boolean get() = consentGiven && onDeviceAiAllowed
        /** Any data collection (telemetry, crash uploads, cloud LLM) requires consent. */
        val canCollectData: Boolean get() = consentGiven
    }
}

/** Thrown when an operation requires user consent that has not been granted. */
class ConsentRequiredException : IllegalStateException(
    "User consent is required before any data collection."
)
