package com.alcedo.studio.privacy

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.alcedo.studio.data.local.SleeveDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages user privacy consent and data lifecycle.
 * Provides GDPR/CCPA compliance features:
 * - Consent management
 * - Data export (right to portability)
 * - Data deletion (right to be forgotten)
 * - Data retention policies
 *
 * Analytics and crash reporting are only started after explicit user consent.
 * Withdrawing consent immediately purges all collected data for that category.
 */
object PrivacyManager {
    private const val TAG = "PrivacyManager"
    private const val PREFS_NAME = "alcedo_privacy"
    private const val KEY_CONSENT_ANALYTICS = "consent_analytics"
    private const val KEY_CONSENT_CRASH_REPORTS = "consent_crash_reports"
    private const val KEY_CONSENT_AI_PROCESSING = "consent_ai_processing"
    private const val KEY_CONSENT_VERSION = "consent_version"
    private const val KEY_FIRST_LAUNCH = "first_launch"
    private const val CURRENT_CONSENT_VERSION = 2

    private lateinit var prefs: SharedPreferences

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ── Consent Management ──

    enum class ConsentType {
        ANALYTICS,       // Anonymous usage analytics
        CRASH_REPORTS,   // Crash report submission
        AI_PROCESSING    // On-device AI processing (CLIP, etc.)
    }

    data class ConsentStatus(
        val analytics: Boolean,
        val crashReports: Boolean,
        val aiProcessing: Boolean,
        val needsUpdate: Boolean
    )

    fun getConsentStatus(): ConsentStatus {
        val version = prefs.getInt(KEY_CONSENT_VERSION, 0)
        return ConsentStatus(
            analytics = prefs.getBoolean(KEY_CONSENT_ANALYTICS, false),
            crashReports = prefs.getBoolean(KEY_CONSENT_CRASH_REPORTS, false),
            aiProcessing = prefs.getBoolean(KEY_CONSENT_AI_PROCESSING, true), // Default: allowed
            needsUpdate = version < CURRENT_CONSENT_VERSION
        )
    }

    /**
     * Returns true only when the user has explicitly granted analytics consent.
     * Use this to gate all analytics SDK initialization and event reporting.
     */
    fun isAnalyticsConsented(): Boolean = prefs.getBoolean(KEY_CONSENT_ANALYTICS, false)

    /**
     * Returns true only when the user has explicitly granted crash reporting consent.
     * Use this to gate Crashlytics and similar SDK initialization.
     */
    fun isCrashReportingConsented(): Boolean = prefs.getBoolean(KEY_CONSENT_CRASH_REPORTS, false)

    fun grantConsent(type: ConsentType, context: Context) {
        val key = when (type) {
            ConsentType.ANALYTICS -> KEY_CONSENT_ANALYTICS
            ConsentType.CRASH_REPORTS -> KEY_CONSENT_CRASH_REPORTS
            ConsentType.AI_PROCESSING -> KEY_CONSENT_AI_PROCESSING
        }
        prefs.edit()
            .putBoolean(key, true)
            .putInt(KEY_CONSENT_VERSION, CURRENT_CONSENT_VERSION)
            .apply()
    }

    /**
     * Revoke consent for the given type and immediately purge all
     * collected data associated with that consent category.
     */
    fun revokeConsent(type: ConsentType, context: Context) {
        val key = when (type) {
            ConsentType.ANALYTICS -> KEY_CONSENT_ANALYTICS
            ConsentType.CRASH_REPORTS -> KEY_CONSENT_CRASH_REPORTS
            ConsentType.AI_PROCESSING -> KEY_CONSENT_AI_PROCESSING
        }
        prefs.edit()
            .putBoolean(key, false)
            .putInt(KEY_CONSENT_VERSION, CURRENT_CONSENT_VERSION)
            .apply()

        // Purge data associated with the revoked consent type
        purgeDataForConsentType(type, context)
    }

    fun grantAllConsent(context: Context) {
        prefs.edit()
            .putBoolean(KEY_CONSENT_ANALYTICS, true)
            .putBoolean(KEY_CONSENT_CRASH_REPORTS, true)
            .putBoolean(KEY_CONSENT_AI_PROCESSING, true)
            .putInt(KEY_CONSENT_VERSION, CURRENT_CONSENT_VERSION)
            .apply()
    }

    fun revokeAllConsent(context: Context) {
        prefs.edit()
            .putBoolean(KEY_CONSENT_ANALYTICS, false)
            .putBoolean(KEY_CONSENT_CRASH_REPORTS, false)
            .putBoolean(KEY_CONSENT_AI_PROCESSING, false)
            .putInt(KEY_CONSENT_VERSION, CURRENT_CONSENT_VERSION)
            .apply()

        // Purge all data collected under each consent type
        ConsentType.entries.forEach { purgeDataForConsentType(it, context) }
    }

    /**
     * Purge all data collected under the given consent type.
     * Called immediately upon consent withdrawal to honour the right to be forgotten.
     */
    private fun purgeDataForConsentType(type: ConsentType, context: Context) {
        when (type) {
            ConsentType.ANALYTICS -> {
                // Clear analytics event queues and cached data
                val analyticsDir = File(context.filesDir, "analytics")
                analyticsDir.deleteRecursively()
                // Also clear any analytics cache in shared prefs (non-consent prefs)
                val analyticsPrefs = context.getSharedPreferences("alcedo_analytics", Context.MODE_PRIVATE)
                analyticsPrefs.edit().clear().apply()
                if (com.alcedo.studio.BuildConfig.DEBUG) {
                    Log.d(TAG, "Purged analytics data after consent withdrawal")
                }
            }
            ConsentType.CRASH_REPORTS -> {
                // Clear stored crash reports
                val crashDir = File(context.filesDir, "crash_reports")
                crashDir.deleteRecursively()
                if (com.alcedo.studio.BuildConfig.DEBUG) {
                    Log.d(TAG, "Purged crash report data after consent withdrawal")
                }
            }
            ConsentType.AI_PROCESSING -> {
                // Clear AI model cache and generated data
                val modelsDir = File(context.filesDir, "ai_models")
                modelsDir.deleteRecursively()
                val aiCacheDir = File(context.cacheDir, "ai_cache")
                aiCacheDir.deleteRecursively()
                if (com.alcedo.studio.BuildConfig.DEBUG) {
                    Log.d(TAG, "Purged AI processing data after consent withdrawal")
                }
            }
        }
    }

    fun isFirstLaunch(): Boolean = !prefs.contains(KEY_FIRST_LAUNCH)

    fun markFirstLaunchComplete() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    fun needsConsentDialog(): Boolean {
        return isFirstLaunch() || getConsentStatus().needsUpdate
    }

    // ── Data Export (Right to Portability) ──

    suspend fun exportUserData(context: Context, outputDir: File): File = withContext(Dispatchers.IO) {
        val exportDir = File(outputDir, "alcedo_data_export")
        exportDir.mkdirs()

        // Export database as JSON
        val db = SleeveDatabase.getInstance(context)

        // Export images metadata
        val imagesFile = File(exportDir, "images.json")
        try {
            val images = db.imageDao().getImagesPaginated(limit = 10000, offset = 0)
            val imagesArray = JSONArray()
            for (img in images) {
                val obj = JSONObject().apply {
                    put("fileId", img.fileId)
                    put("width", img.width)
                    put("height", img.height)
                    put("format", img.format)
                    put("colorSpace", img.colorSpace)
                    put("bitDepth", img.bitDepth)
                    put("isHdr", img.isHdr)
                    put("rating", img.rating)
                    // NOTE: filePath excluded for privacy
                }
                imagesArray.put(obj)
            }
            imagesFile.writeText(imagesArray.toString(2))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to export images metadata", e)
            imagesFile.writeText("[]")
        }

        // Export consent preferences
        val consentFile = File(exportDir, "consent_preferences.json")
        val status = getConsentStatus()
        consentFile.writeText(
            """{"analytics":${status.analytics},"crashReports":${status.crashReports},"aiProcessing":${status.aiProcessing}}"""
        )

        // Export settings summary
        val settingsFile = File(exportDir, "settings.json")
        settingsFile.writeText("{}")

        exportDir
    }

    // ── Data Deletion (Right to be Forgotten) ──

    suspend fun deleteAllUserData(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            // Clear database
            val db = SleeveDatabase.getInstance(context)
            db.clearAllTables()

            // Clear shared preferences (except consent state)
            val prefsDir = File(context.filesDir.parentFile, "shared_prefs")
            prefsDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("alcedo_") && file.name != "$PREFS_NAME.xml") {
                    file.delete()
                }
            }

            // Clear cache
            context.cacheDir.deleteRecursively()

            // Clear external cache
            context.externalCacheDir?.deleteRecursively()

            // Clear thumbnails
            val thumbDir = File(context.filesDir, "thumbnails")
            thumbDir.deleteRecursively()

            // Clear crash reports
            val crashDir = File(context.filesDir, "crash_reports")
            crashDir.deleteRecursively()

            // Clear temp files
            val tempDir = File(context.cacheDir, "temp")
            tempDir.deleteRecursively()

            // Clear AI models
            val modelsDir = File(context.filesDir, "ai_models")
            modelsDir.deleteRecursively()

            // Clear analytics data
            val analyticsDir = File(context.filesDir, "analytics")
            analyticsDir.deleteRecursively()

            // Revoke all consent and purge associated data
            revokeAllConsent(context)

            if (com.alcedo.studio.BuildConfig.DEBUG) {
                Log.i(TAG, "All user data deleted successfully")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete user data", e)
            false
        }
    }

    // ── Data Retention Policy ──

    fun applyRetentionPolicy(context: Context) {
        val now = System.currentTimeMillis()
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000

        // Delete crash reports older than 30 days (only if consent was given)
        if (isCrashReportingConsented()) {
            val crashDir = File(context.filesDir, "crash_reports")
            crashDir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > thirtyDaysMs) {
                    file.delete()
                }
            }
        }

        // Delete temp files older than 7 days
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
        val tempDir = context.cacheDir
        tempDir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > sevenDaysMs) {
                file.delete()
            }
        }
    }
}
