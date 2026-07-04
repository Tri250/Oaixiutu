package com.alcedo.studio.crash

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashHandler : Thread.UncaughtExceptionHandler {
    private const val TAG = "AlcedoCrash"
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var appContext: Context? = null
    private var crashCallback: ((Throwable) -> Unit)? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
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

            // Save crash report to file
            saveCrashReport(throwable)

            // Notify callback
            crashCallback?.invoke(throwable)
        } catch (e: Exception) {
            Log.e(TAG, "Error in crash handler", e)
        } finally {
            // Chain to default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashReport(throwable: Throwable) {
        val context = appContext ?: return
        val crashDir = File(context.filesDir, "crash_reports")
        if (!crashDir.exists()) crashDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val crashFile = File(crashDir, "crash_${timestamp}.log")

        // Convert stack trace to string, then sanitize PII
        val stringWriter = StringWriter()
        PrintWriter(stringWriter).use { pw ->
            throwable.printStackTrace(pw)
        }
        val sanitizedTrace = sanitizeStackTrace(stringWriter.toString())

        FileWriter(crashFile, true).use { writer ->
            PrintWriter(writer).use { pw ->
                pw.println("=== Alcedo Crash Report ===")
                pw.println("Time: ${Date()}")
                pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                pw.println("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                pw.println("Thread: ${Thread.currentThread().name}")
                pw.println()
                pw.println(sanitizedTrace)
                pw.println()

                // Also log recent native crashes if available
                val tombstoneDir = File("/data/tombstones")
                if (tombstoneDir.exists()) {
                    pw.println("=== Recent Tombstones ===")
                    tombstoneDir.listFiles()
                        ?.sortedByDescending { it.lastModified() }
                        ?.take(1)
                        ?.forEach { pw.println("(See: ${it.name})") }
                }
            }
        }
    }

    private fun sanitizeStackTrace(trace: String): String {
        var sanitized = trace
        // Remove file paths that might contain usernames
        sanitized = sanitized.replace(Regex("/data/data/[^/]+/"), "/data/data/[REDACTED]/")
        sanitized = sanitized.replace(Regex("/storage/emulated/\\d+/"), "/storage/[REDACTED]/")
        sanitized = sanitized.replace(Regex("/sdcard/"), "/[REDACTED]/")
        // Remove potential API keys or tokens
        sanitized = sanitized.replace(Regex("(api[_-]?key|token|secret|password|credential)\\s*[=:]\\s*\\S+", RegexOption.IGNORE_CASE), "$1=[REDACTED]")
        // Remove email addresses
        sanitized = sanitized.replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), "[EMAIL_REDACTED]")
        // Remove IP addresses
        sanitized = sanitized.replace(Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b"), "[IP_REDACTED]")
        return sanitized
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
