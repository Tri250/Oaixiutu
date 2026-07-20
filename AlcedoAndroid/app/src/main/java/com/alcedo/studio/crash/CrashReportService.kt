package com.alcedo.studio.crash

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import android.util.Log
import com.alcedo.studio.BuildConfig
import com.alcedo.studio.privacy.PrivacyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Real crash reporting service.
 *
 * Responsibilities:
 *  - Captures uncaught exception metadata and persists it to
 *    `context.filesDir/crash_reports/` as structured JSON files.
 *  - Keeps at most [MAX_REPORTS] reports on disk, deleting the oldest.
 *  - Attaches metadata: app version, device model, Android version,
 *    timestamp, sanitized stack trace, memory state, breadcrumbs and events.
 *  - Uploads reports to a configurable endpoint via OkHttp, but only after
 *    the user has granted [PrivacyManager.ConsentType.CRASH_REPORTS].
 *  - Rate-limits uploads to at most one batch every [UPLOAD_MIN_INTERVAL_MS]
 *    so we never spam the endpoint.
 *
 * Thread-safe. Designed to be called from the crash handler thread as well
 * as normal app code.
 */
object CrashReportService {
    private const val TAG = "CrashReportService"

    private const val MAX_REPORTS = 10
    private const val UPLOAD_MIN_INTERVAL_MS = 5L * 60 * 1000 // 5 minutes
    private const val CRASH_DIR_NAME = "crash_reports"
    private const val FILE_SUFFIX = ".json"

    // Default endpoint. Can be overridden via [setUploadEndpoint].
    // Intentionally a placeholder that the developer replaces with their
    // own crash ingestion server.
    private const val DEFAULT_ENDPOINT =
        "https://crash-reports.alcedo.studio/api/v1/crash_reports"

    private lateinit var appContext: Context
    private lateinit var prefs: android.content.SharedPreferences

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uploadMutex = Mutex()

    // Breadcrumbs and events are kept in-memory until the next crash flushes
    // them into the persisted report. The collections are guarded by their
    // own lock because they are written from arbitrary threads.
    private val breadcrumbs = LinkedHashMap<String, String>()
    private val events = ArrayList<String>()
    private val breadcrumbLock = Any()

    private var uploadEndpoint: String = DEFAULT_ENDPOINT
    private var lastUploadTimeMs: Long = 0L

    // Visible for testing / settings UI.
    private const val PREFS_NAME = "alcedo_crash"
    private const val KEY_UPLOAD_ENABLED = "upload_enabled"
    private const val KEY_ENDPOINT = "upload_endpoint"
    private const val KEY_LAST_UPLOAD = "last_upload_ms"

    fun initialize(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        uploadEndpoint = prefs.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT
        lastUploadTimeMs = prefs.getLong(KEY_LAST_UPLOAD, 0L)
        enforceMaxReports()
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Records a throwable as a crash report on disk. Intended to be called by
     * [CrashHandler] on the crashing thread. Must not throw.
     */
    fun reportCrash(thread: Thread, throwable: Throwable) {
        // 修复: 如果 CrashReportService 尚未初始化，直接返回而非崩溃
        if (!::appContext.isInitialized) {
            Log.e(TAG, "CrashReportService not initialized, cannot report crash")
            return
        }
        try {
            val report = buildReport(thread, throwable)
            persistReport(report)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to persist crash report", e)
        }
    }

    /**
     * Appends a free-form event line to the in-memory event log. These are
     * attached to the next crash report to aid debugging.
     */
    fun logEvent(event: String) {
        if (!::appContext.isInitialized) return
        synchronized(breadcrumbLock) {
            events.add("${nowIso()} | event | ${event.take(500)}")
            if (events.size > MAX_BREADCRUMBS) {
                events.removeAt(0)
            }
        }
    }

    /**
     * Records a key/value breadcrumb (e.g. "screen" -> "EditorScreen").
     * Latest value wins for a given key.
     */
    fun logBreadcrumb(key: String, value: String) {
        if (!::appContext.isInitialized) return
        synchronized(breadcrumbLock) {
            breadcrumbs[key] = value.take(300)
            // Bound the map size.
            if (breadcrumbs.size > MAX_BREADCRUMBS) {
                val firstKey = breadcrumbs.keys.iterator().next()
                breadcrumbs.remove(firstKey)
            }
        }
    }

    /**
     * Flushes pending crash reports to the server, subject to consent and the
     * rate limit. Safe to call from the UI thread; the actual work happens on
     * an IO dispatcher.
     */
    fun flushReports() {
        if (!::appContext.isInitialized) return
        scope.launch {
            try {
                uploadPendingReports(force = false)
            } catch (e: Throwable) {
                Log.e(TAG, "flushReports failed", e)
            }
        }
    }

    /**
     * Returns the number of crash reports currently persisted on disk.
     */
    fun getPendingReportCount(): Int {
        if (!::appContext.isInitialized) return 0
        return crashDir().listFiles { _, name -> name.endsWith(FILE_SUFFIX) }
            ?.size ?: 0
    }

    /**
     * Enables or disables automatic uploads. When disabled, reports remain on
     * disk but are never transmitted. Also reflects the user's consent state
     * managed by [PrivacyManager].
     */
    fun setUploadEnabled(enabled: Boolean) {
        if (!::appContext.isInitialized) return
        prefs.edit().putBoolean(KEY_UPLOAD_ENABLED, enabled).apply()
        if (enabled) {
            // Respect the rate limit; flush opportunistically.
            flushReports()
        }
    }

    fun isUploadEnabled(): Boolean {
        if (!::appContext.isInitialized) return false
        return prefs.getBoolean(KEY_UPLOAD_ENABLED, false) &&
            PrivacyManager.getConsentStatus().crashReports
    }

    /**
     * Overrides the crash report ingestion endpoint.
     */
    fun setUploadEndpoint(endpoint: String) {
        if (!::appContext.isInitialized) return
        uploadEndpoint = endpoint
        prefs.edit().putString(KEY_ENDPOINT, endpoint).apply()
    }

    /** Returns the directory used for crash reports, creating it if needed. */
    internal fun crashDir(): File {
        val dir = File(appContext.filesDir, CRASH_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // ── Internal: building & persisting reports ─────────────────────────

    private const val MAX_BREADCRUMBS = 64

    private fun buildReport(thread: Thread, throwable: Throwable): JSONObject {
        val stringWriter = StringWriter()
        PrintWriter(stringWriter).use { pw -> throwable.printStackTrace(pw) }
        val sanitizedTrace = sanitizeStackTrace(stringWriter.toString())

        return JSONObject().apply {
            put("schema", "alcedo.crash.v1")
            put("app_version", BuildConfig.VERSION_NAME ?: "unknown")
            put("app_version_code", BuildConfig.VERSION_CODE)
            put("build_type", BuildConfig.BUILD_TYPE ?: "unknown")
            put("package_name", appContext.packageName ?: "unknown")
            put("timestamp", nowIso())
            put("timestamp_ms", System.currentTimeMillis())
            put("device_manufacturer", Build.MANUFACTURER ?: "")
            put("device_model", Build.MODEL ?: "")
            put("device_product", Build.PRODUCT ?: "")
            put("android_version", Build.VERSION.RELEASE ?: "")
            put("android_sdk", Build.VERSION.SDK_INT)
            put("abi", Build.SUPPORTED_ABIS?.firstOrNull() ?: "")
            put("thread_name", thread.name ?: "")
            put("thread_id", thread.id)
            put("process_id", Process.myPid())
            put("is_debug", BuildConfig.DEBUG)
            put("stack_trace", sanitizedTrace)
            put("exception_class", throwable.javaClass?.name ?: "Unknown")
            put("exception_message", throwable.message ?: "")
            put("memory", buildMemoryState())
            put("breadcrumbs", buildBreadcrumbsJson())
            put("events", buildEventsJson())
        }
    }

    private fun buildMemoryState(): JSONObject {
        val runtime = Runtime.getRuntime()
        val nativeHeap = Debug.getNativeHeapSize()
        val nativeHeapFree = Debug.getNativeHeapFreeSize()
        return JSONObject().apply {
            put("java_max_mb", runtime.maxMemory() / MB)
            put("java_total_mb", runtime.totalMemory() / MB)
            put("java_free_mb", runtime.freeMemory() / MB)
            put("java_used_mb", (runtime.totalMemory() - runtime.freeMemory()) / MB)
            put("native_heap_size_mb", nativeHeap / MB)
            put("native_heap_free_mb", nativeHeapFree / MB)
            put("native_heap_used_mb", (nativeHeap - nativeHeapFree) / MB)
        }
    }

    private fun buildBreadcrumbsJson(): JSONObject {
        val snapshot: Map<String, String> = synchronized(breadcrumbLock) {
            LinkedHashMap(breadcrumbs)
        }
        return JSONObject(snapshot)
    }

    private fun buildEventsJson(): JSONArray {
        val snapshot: List<String> = synchronized(breadcrumbLock) {
            ArrayList(events)
        }
        val arr = JSONArray()
        snapshot.forEach { arr.put(it) }
        return arr
    }

    private fun persistReport(report: JSONObject) {
        val dir = crashDir()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(dir, "crash_${timestamp}${FILE_SUFFIX}")
        file.writeText(report.toString(2))
        enforceMaxReports()
        // Best-effort upload attempt.
        flushReports()
    }

    /**
     * Deletes the oldest reports when the on-disk count exceeds [MAX_REPORTS].
     */
    private fun enforceMaxReports() {
        val dir = crashDir()
        val files = dir.listFiles { _, name -> name.endsWith(FILE_SUFFIX) }
            ?.toMutableList() ?: return
        if (files.size <= MAX_REPORTS) return
        files.sortBy { it.lastModified() }
        val toDelete = files.size - MAX_REPORTS
        for (i in 0 until toDelete) {
            files[i].delete()
        }
    }

    // ── Internal: upload ────────────────────────────────────────────────

    private suspend fun uploadPendingReports(force: Boolean) = uploadMutex.withLock {
        if (!isUploadEnabled()) return@withLock

        val now = System.currentTimeMillis()
        if (!force && now - lastUploadTimeMs < UPLOAD_MIN_INTERVAL_MS) {
            Log.d(TAG, "Upload skipped: rate limited")
            return@withLock
        }

        val dir = crashDir()
        val files = dir.listFiles { _, name -> name.endsWith(FILE_SUFFIX) }
            ?.sortedBy { it.lastModified() } ?: return@withLock
        if (files.isEmpty()) return@withLock

        var successCount = 0
        for (file in files) {
            val ok = uploadOne(file)
            if (ok) {
                file.delete()
                successCount++
            } else {
                // Stop on first failure; the network is likely unavailable.
                break
            }
        }

        if (successCount > 0) {
            lastUploadTimeMs = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_UPLOAD, lastUploadTimeMs).apply()
            Log.i(TAG, "Uploaded $successCount crash report(s)")
        }
    }

    private fun uploadOne(file: File): Boolean {
        return try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "report",
                    file.name,
                    file.readBytes()
                        .toRequestBody("application/json".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url(uploadEndpoint)
                .post(body)
                .addHeader("User-Agent", "AlcedoStudio/${BuildConfig.VERSION_NAME}")
                .addHeader("X-Alcedo-Client", "android")
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Upload failed for ${file.name}", e)
            false
        }
    }

    // ── Internal: PII sanitization ──────────────────────────────────────

    /**
     * Removes obvious PII from a stack trace before persisting or uploading.
     * Mirrors the redaction policy used by the legacy [CrashHandler].
     */
    internal fun sanitizeStackTrace(trace: String): String {
        var sanitized = trace
        sanitized = sanitized.replace(Regex("/data/data/[^/]+/"), "/data/data/[REDACTED]/")
        sanitized = sanitized.replace(Regex("/storage/emulated/\\d+/"), "/storage/[REDACTED]/")
        sanitized = sanitized.replace(Regex("/sdcard/"), "/[REDACTED]/")
        sanitized = sanitized.replace(
            Regex("(api[_-]?key|token|secret|password|credential)\\s*[=:]\\s*\\S+", RegexOption.IGNORE_CASE),
            "$1=[REDACTED]"
        )
        sanitized = sanitized.replace(
            Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
            "[EMAIL_REDACTED]"
        )
        sanitized = sanitized.replace(Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b"), "[IP_REDACTED]")
        return sanitized
    }

    private fun nowIso(): String {
        val tz = java.util.TimeZone.getTimeZone("UTC")
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        df.timeZone = tz
        return df.format(Date())
    }

    private const val MB: Long = 1024L * 1024L
}
