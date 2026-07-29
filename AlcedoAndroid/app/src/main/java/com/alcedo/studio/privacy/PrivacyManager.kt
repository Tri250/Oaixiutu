package com.alcedo.studio.privacy

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.privacyDataStore by preferencesDataStore(name = "alcedo_privacy")
private val Context.appSettingsDataStore by preferencesDataStore(name = "alcedo_settings")

/**
 * Privacy management. Tracks the user's first-run consent (required in some
 * jurisdictions before any data processing) and per-feature toggles (cloud LLM,
 * telemetry, on-device AI). Persisted in DataStore so it survives reinstalls
 * within backup/restore.
 *
 * Also persists general application settings ([appSettings]) — theme, default
 * view, GPU backend, AI configuration and the crash-report / analytics toggles
 * — so the settings screen can read and write them reactively.
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

    object AppKeys {
        val THEME = stringPreferencesKey("theme")
        val DEFAULT_VIEW = stringPreferencesKey("default_view")
        val GPU_BACKEND = stringPreferencesKey("gpu_backend")
        val AI_STRICTNESS = floatPreferencesKey("ai_strictness")
        val CRASH_REPORT_ENABLED = booleanPreferencesKey("crash_report_enabled")
        val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_ENDPOINT = stringPreferencesKey("ai_endpoint")
        val AI_MODEL = stringPreferencesKey("ai_model")
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

    /** Flow of the persisted application settings (appearance / behaviour / AI). */
    val appSettings: Flow<AppSettings> = context.appSettingsDataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[AppKeys.THEME] ?: "",
            defaultView = prefs[AppKeys.DEFAULT_VIEW] ?: "",
            gpuBackend = prefs[AppKeys.GPU_BACKEND] ?: "",
            aiStrictness = prefs[AppKeys.AI_STRICTNESS] ?: 0.5f,
            crashReportEnabled = prefs[AppKeys.CRASH_REPORT_ENABLED] ?: true,
            analyticsEnabled = prefs[AppKeys.ANALYTICS_ENABLED] ?: false,
            aiApiKey = prefs[AppKeys.AI_API_KEY] ?: "",
            aiEndpoint = prefs[AppKeys.AI_ENDPOINT] ?: "",
            aiModel = prefs[AppKeys.AI_MODEL] ?: "",
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

    // ---- App settings (appearance / behaviour / AI) ----------------------

    suspend fun setTheme(theme: String) {
        context.appSettingsDataStore.edit { it[AppKeys.THEME] = theme }
    }

    suspend fun setDefaultView(view: String) {
        context.appSettingsDataStore.edit { it[AppKeys.DEFAULT_VIEW] = view }
    }

    suspend fun setGpuBackend(backend: String) {
        context.appSettingsDataStore.edit { it[AppKeys.GPU_BACKEND] = backend }
    }

    suspend fun setAiStrictness(strictness: Float) {
        context.appSettingsDataStore.edit { it[AppKeys.AI_STRICTNESS] = strictness }
    }

    suspend fun setCrashReportEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[AppKeys.CRASH_REPORT_ENABLED] = enabled }
    }

    suspend fun setAnalyticsEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[AppKeys.ANALYTICS_ENABLED] = enabled }
    }

    suspend fun setAiApiKey(key: String) {
        context.appSettingsDataStore.edit { it[AppKeys.AI_API_KEY] = key }
    }

    suspend fun setAiEndpoint(endpoint: String) {
        context.appSettingsDataStore.edit { it[AppKeys.AI_ENDPOINT] = endpoint }
    }

    suspend fun setAiModel(model: String) {
        context.appSettingsDataStore.edit { it[AppKeys.AI_MODEL] = model }
    }

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

    /**
     * Persisted application settings surfaced to the settings screen. All fields
     * default so that [AppSettings] can be constructed with no arguments for an
     * initial UI state.
     */
    data class AppSettings(
        val theme: String = "",
        val defaultView: String = "",
        val gpuBackend: String = "",
        val aiStrictness: Float = 0.5f,
        val crashReportEnabled: Boolean = true,
        val analyticsEnabled: Boolean = false,
        val aiApiKey: String = "",
        val aiEndpoint: String = "",
        val aiModel: String = "",
    )
}

/** Thrown when an operation requires user consent that has not been granted. */
class ConsentRequiredException : IllegalStateException(
    "User consent is required before any data collection."
)
