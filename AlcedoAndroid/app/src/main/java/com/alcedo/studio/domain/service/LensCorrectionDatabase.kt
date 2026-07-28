package com.alcedo.studio.domain.service

import com.alcedo.studio.data.local.LensProfileEntity
import com.alcedo.studio.data.local.SleeveDatabase
import com.alcedo.studio.ndk.AlcedoNativeBridge
import com.alcedo.studio.ndk.NdkSafeCall
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lens correction database. Caches lens-correction profiles (distortion, CA,
 * vignetting) sourced from the native lens-calibration tables
 * (core/edit/operators/geometry/lens_calib_op.cpp) and the local Room store.
 */
@Singleton
class LensCorrectionDatabase @Inject constructor(
    private val database: SleeveDatabase,
) {

    /** Resolve a profile JSON for [lensId], checking the local cache first. */
    suspend fun profile(lensId: String): String? = withContext(ThreadPool.database) {
        val cached = readCached(lensId)
        if (cached != null) return@withContext cached
        val native = NdkSafeCall.callOrNull { AlcedoNativeBridge.nativeLensCorrectionProfile(lensId) }
        if (native != null) {
            cacheProfile(lensId, native)
        }
        native
    }

    /** Persist a profile JSON for offline use. */
    suspend fun cacheProfile(lensId: String, json: String) = withContext(ThreadPool.database) {
        val cv = android.content.ContentValues().apply {
            put("id", lensId)
            put("lensId", lensId)
            put("displayName", extractDisplayName(json, lensId))
            put("maker", extractMaker(json))
            put("profileJson", json)
            put("isCalibrated", 1)
        }
        database.openHelper.writableDatabase.insert(
            "lens_profiles", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, cv,
        )
    }

    /** All cached lens ids. */
    suspend fun listLensIds(): List<String> = withContext(ThreadPool.database) {
        val results = mutableListOf<String>()
        database.openHelper.writableDatabase.query("SELECT lensId FROM lens_profiles").use { c ->
            while (c.moveToNext()) results.add(c.getString(0))
        }
        results
    }

    private suspend fun readCached(lensId: String): String? = withContext(ThreadPool.database) {
        database.openHelper.writableDatabase
            .query("SELECT profileJson FROM lens_profiles WHERE lensId = ? LIMIT 1", arrayOf(lensId))
            .use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    private fun extractDisplayName(json: String, fallback: String): String =
        runCatching { JSONObject(json).optString("display_name", fallback) }.getOrDefault(fallback)

    private fun extractMaker(json: String): String =
        runCatching { JSONObject(json).optString("maker", "Unknown") }.getOrDefault("Unknown")
}
