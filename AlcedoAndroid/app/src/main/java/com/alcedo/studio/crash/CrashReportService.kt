package com.alcedo.studio.crash

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash report collector service. Runs in a dedicated `:crash` process (declared
 * in the manifest) so a JVM crash in the main process can still be recorded.
 * Installs a global [Thread.UncaughtExceptionHandler] that writes a stack trace
 * to the app's crash log directory, then re-throws to the previous handler so
 * the OS shows its native dialog.
 *
 * Privacy: crash capture is gated by [enabled] (set from the user's consent /
 * telemetry decision via [setEnabled] at app startup). When disabled, no crash
 * report is written. Reports are scrubbed of secrets (Authorization headers,
 * API keys, bearer tokens) before being written, size-limited to
 * [MAX_REPORT_BYTES], and the on-disk log directory is pruned to
 * [MAX_REPORT_FILES] / [MAX_TOTAL_BYTES].
 *
 * The actual crash file is consumed by the settings / diagnostics screen on
 * next launch.
 */
class CrashReportService : Service() {

    override fun onCreate() {
        super.onCreate()
        installHandler()
        startForegroundIfNeeded()
        Log.i(TAG, "CrashReportService ready in pid=${Process.myPid()} process=\"${getProcessNameCompat()}\" enabled=$enabled")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The service is sticky: even if killed, restart it so crashes keep
        // being captured. No intent payload is expected.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Install the global uncaught-exception handler, chained after [previous]. */
    private fun installHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Never let the handler itself throw and mask the original crash.
            runCatching { writeCrashReport(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun startForegroundIfNeeded() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("Crash reporter")
            .setDescription("Captures crash diagnostics in the background.")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Alcedo")
            .setContentText("Crash reporter running")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun writeCrashReport(thread: Thread, throwable: Throwable) {
        // Privacy gate: only capture when the user has consented (the app sets
        // this from PrivacyManager at startup). If consent was never given, the
        // crash is allowed to propagate without being recorded.
        if (!enabled) {
            Log.w(TAG, "crash capture disabled (no consent); not writing report")
            return
        }
        val dir = File(filesDir, "crashlogs").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash_$timestamp.txt")
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            pw.println("Alcedo crash report")
            pw.println("====================")
            pw.println("Time: ${Date()}")
            pw.println("Thread: ${thread.name} (id=${thread.id})")
            pw.println("Process: ${getProcessNameCompat()}")
            pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            pw.println("Android: ${Build.VERSION.RELEASE} (sdk=${Build.VERSION.SDK_INT})")
            pw.println("Abi: ${Build.SUPPORTED_ABIS.joinToString(",")}")
            pw.println()
            pw.println("Stack trace:")
            throwable.printStackTrace(pw)
        }
        // Scrub secrets, then enforce a per-report size cap so a runaway stack
        // trace can't fill the disk or exfiltrate credentials embedded in it.
        val raw = scrub(sw.toString())
        val capped = if (raw.length > MAX_REPORT_CHARS) {
            raw.substring(0, MAX_REPORT_CHARS) + "\n...[truncated]\n"
        } else {
            raw
        }
        file.writeText(capped)
        pruneOldReports(dir)
        Log.w(TAG, "Crash report written to ${file.absolutePath}")
    }

    /** Remove secrets (API keys, bearer tokens, auth headers) from [text]. */
    private fun scrub(text: String): String {
        var s = REDACT_HEADER.replace(text) { "${it.groupValues[1]}=<redacted>" }
        s = REDACT_BEARER.replace(s, "Bearer <redacted>")
        s = REDACT_OPENAI.replace(s, "sk-<redacted>")
        return s
    }

    /** Keep at most [MAX_REPORT_FILES] / [MAX_TOTAL_BYTES] of crash logs. */
    private fun pruneOldReports(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        var kept = 0
        var totalBytes = 0L
        files.forEach { f ->
            totalBytes += f.length()
            kept++
            if (kept > MAX_REPORT_FILES || totalBytes > MAX_TOTAL_BYTES) {
                runCatching { f.delete() }
            }
        }
    }

    private fun getProcessNameCompat(): String =
        // Application.getProcessName() is the public API on API 28+ (minSdk = 29).
        runCatching { android.app.Application.getProcessName() }.getOrNull() ?: "unknown"

    companion object {
        private const val TAG = "CrashReportService"
        private const val CHANNEL_ID = "alcedo_crash"
        private const val NOTIFICATION_ID = 0xCA01

        /** Per-report size cap (~256 KB). */
        private const val MAX_REPORT_BYTES = 256L * 1024L
        private const val MAX_REPORT_CHARS = 256 * 1024
        /** Maximum number of crash files retained on disk. */
        private const val MAX_REPORT_FILES = 10
        /** Maximum total bytes of crash files retained on disk (~2 MB). */
        private const val MAX_TOTAL_BYTES = 2L * 1024L * 1024L

        private val REDACT_HEADER =
            Regex("(?i)\\b(authorization|api[\\-_ ]?key|x-api-key|secret|access[\\-_ ]?token|refresh[\\-_ ]?token)\\b\\s*[:=]\\s*\\S+")
        private val REDACT_BEARER = Regex("(?i)\\bBearer\\s+[A-Za-z0-9_\\-.=]+")
        private val REDACT_OPENAI = Regex("sk-[A-Za-z0-9_\\-]{16,}")

        /**
         * Whether crash capture is enabled. Set from the user's consent/telemetry
         * decision at app startup (must be set in the same process that installs
         * the uncaught-exception handler). Defaults to false: no crash data is
         * collected until the user opts in.
         */
        @Volatile
        var enabled: Boolean = false

        /** Update the consent-gated crash-capture flag (call from app startup). */
        fun setEnabled(consentAndTelemetryAllowed: Boolean) {
            enabled = consentAndTelemetryAllowed
        }

        /** All crash report files on disk, oldest first. */
        fun reportFiles(filesDir: File): List<File> {
            val dir = File(filesDir, "crashlogs")
            return dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
                ?.sortedBy { it.lastModified() }
                ?: emptyList()
        }

        /** Delete all stored crash reports (called from the diagnostics screen). */
        fun clearReports(filesDir: File): Int {
            val files = reportFiles(filesDir)
            files.forEach { it.delete() }
            return files.size
        }
    }
}
