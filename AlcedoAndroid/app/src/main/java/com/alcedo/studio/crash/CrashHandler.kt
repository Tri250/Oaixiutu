package com.alcedo.studio.crash

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Global uncaught-exception handler.
 *
 * Acts as the entry point that the JVM/ART invokes when a thread dies with an
 * uncaught exception. The actual persistence, metadata capture and (opt-in)
 * upload work is delegated to [CrashReportService] so this class stays focused
 * on installing the handler and chaining to the platform default.
 *
 * Legacy public API (used elsewhere in the app) is preserved:
 *  - [getCrashReports], [clearCrashReports], [hasRecentCrash] now operate on
 *    whatever files exist in the crash directory, covering both the new JSON
 *    reports produced by [CrashReportService] and any older `.log` files.
 */
object CrashHandler : Thread.UncaughtExceptionHandler {
    private const val TAG = "AlcedoCrash"
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var appContext: Context? = null
    private var crashCallback: ((Throwable) -> Unit)? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        // Make sure the report service is bootstrapped first so it can accept
        // the crash when we delegate below.
        try {
            CrashReportService.initialize(context)
        } catch (e: Throwable) {
            Log.e(TAG, "CrashReportService.initialize failed", e)
        }
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    fun setCrashCallback(callback: (Throwable) -> Unit) {
        crashCallback = callback
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // Log the crash
            Log.e(TAG, "Uncaught exception in thread: ${thread.name}", throwable)

            // Delegate persistent storage + (optional) upload to the service.
            CrashReportService.reportCrash(thread, throwable)

            // Also keep a lightweight plain-text trace for legacy diagnostics
            // tools that scan the crash directory. This does not duplicate the
            // structured JSON report but makes the crash immediately visible.
            writeLegacyTrace(thread, throwable)

            // Notify callback — must not throw, or it will mask the real crash
            try {
                crashCallback?.invoke(throwable)
            } catch (cbEx: Exception) {
                Log.e(TAG, "Crash callback threw exception, masking real crash is prevented", cbEx)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in crash handler", e)
        } finally {
            // Chain to default handler so the process exits normally.
            // If handler was never initialized, re-throw to ensure proper crash behavior
            if (defaultHandler != null) {
                defaultHandler!!.uncaughtException(thread, throwable)
            } else {
                // No default handler available; ensure the process does not hang
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    /**
     * Writes a minimal plain-text marker so that legacy tooling which only
     * scans for `.log` files continues to detect crashes. The authoritative,
     * rich report lives in the JSON file produced by [CrashReportService].
     */
    private fun writeLegacyTrace(thread: Thread, throwable: Throwable) {
        val context = appContext ?: return
        try {
            val crashDir = File(context.filesDir, "crash_reports")
            if (!crashDir.exists()) crashDir.mkdirs()
            val timestamp = java.text.SimpleDateFormat(
                "yyyyMMdd_HHmmss", java.util.Locale.US
            ).format(java.util.Date())
            val legacyFile = File(crashDir, "crash_${timestamp}.log")
            val stringWriter = StringWriter()
            PrintWriter(stringWriter).use { pw -> throwable.printStackTrace(pw) }
            val sanitized = CrashReportService.sanitizeStackTrace(stringWriter.toString())
            legacyFile.writeText(
                buildString {
                    appendLine("=== Alcedo Crash Report (legacy trace) ===")
                    appendLine("Time: ${java.util.Date()}")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    appendLine(sanitized)
                }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to write legacy trace", e)
        }
    }

    fun getCrashReports(): List<File> {
        val context = appContext ?: return emptyList()
        val crashDir = File(context.filesDir, "crash_reports")
        if (!crashDir.exists()) return emptyList()
        return crashDir.listFiles()?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
    }

    fun clearCrashReports() {
        val context = appContext ?: return
        val crashDir = File(context.filesDir, "crash_reports")
        crashDir.listFiles()?.forEach { it.delete() }
    }

    fun hasRecentCrash(): Boolean {
        val reports = getCrashReports()
        if (reports.isEmpty()) return false
        val recent = reports.first()
        val age = System.currentTimeMillis() - recent.lastModified()
        return age < 60_000 // within last minute
    }
}
