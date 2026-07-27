package com.alcedo.studio.crash

import android.content.Context
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean

object CrashHandler : Thread.UncaughtExceptionHandler {
    private const val TAG = "AlcedoCrash"
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var appContext: Context? = null
    private var crashCallback: ((Throwable) -> Unit)? = null
    private val isHandlingCrash = AtomicBoolean(false)
    private const val CRASH_LOOP_THRESHOLD_MS = 30_000L
    private const val MAX_CRASH_LOOP_COUNT = 3
    private const val PREFS_CRASH_LOOP = "alcedo_crash_loop"
    private const val KEY_LAST_CRASH_TIME = "last_crash_time"
    private const val KEY_CRASH_COUNT = "crash_count"

    fun initialize(context: Context) {
        appContext = context.applicationContext
        try {
            CrashReportService.initialize(context)
        } catch (e: Throwable) {
            Log.e(TAG, "CrashReportService.initialize failed", e)
        }
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (Thread.getDefaultUncaughtExceptionHandler() !== this) {
            Thread.setDefaultUncaughtExceptionHandler(this)
        }
        installMainLooperSafetyNet()
        detectAndClearCrashLoop(context)
    }

    private fun installMainLooperSafetyNet() {
        try {
            val handler = android.os.Handler(Looper.getMainLooper())
            handler.post {
                while (true) {
                    try {
                        Looper.loop()
                        break
                    } catch (e: Throwable) {
                        Log.e(TAG, "Main looper exception caught by safety net, preventing crash loop", e)
                        try {
                            CrashReportService.reportCrash(Thread.currentThread(), e)
                            CrashReportService.logEvent("main_looper_safety_net:${e.javaClass.simpleName}")
                        } catch (_: Throwable) {}
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to install main looper safety net", e)
        }
    }

    private fun detectAndClearCrashLoop(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_CRASH_LOOP, Context.MODE_PRIVATE)
            val lastTime = prefs.getLong(KEY_LAST_CRASH_TIME, 0L)
            var count = prefs.getInt(KEY_CRASH_COUNT, 0)
            val now = System.currentTimeMillis()
            count = if (now - lastTime < CRASH_LOOP_THRESHOLD_MS) count + 1 else 1
            prefs.edit()
                .putLong(KEY_LAST_CRASH_TIME, now)
                .putInt(KEY_CRASH_COUNT, count)
                .apply()
            if (count >= MAX_CRASH_LOOP_COUNT) {
                Log.w(TAG, "Crash loop detected ($count crashes in window) — performing emergency cleanup")
                performEmergencyCleanup(context)
                prefs.edit().remove(KEY_CRASH_COUNT).apply()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Crash loop detection failed", e)
        }
    }

    private fun performEmergencyCleanup(context: Context) {
        try {
            context.cacheDir.listFiles()?.forEach {
                if (it.name.startsWith("alcedo_")) runCatching { it.deleteRecursively() }
            }
            val dbDir = File(context.filesDir.parentFile, "databases")
            if (dbDir.exists()) {
                dbDir.listFiles { _, name -> name.contains("sleeve", ignoreCase = true) }
                    ?.forEach { runCatching { it.delete() } }
            }
            runCatching { CrashReportService.logEvent("emergency_cleanup_executed") }
        } catch (e: Throwable) {
            Log.e(TAG, "Emergency cleanup failed", e)
        }
    }

    fun setCrashCallback(callback: (Throwable) -> Unit) {
        crashCallback = callback
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (isHandlingCrash.getAndSet(true)) {
            Log.wtf(TAG, "Re-entrant crash detected, avoiding infinite loop", throwable)
            killProcess()
            return
        }
        try {
            Log.e(TAG, "Uncaught exception in thread: ${thread.name}", throwable)
            runCatching { CrashReportService.reportCrash(thread, throwable) }
            runCatching { writeLegacyTrace(thread, throwable) }
            try {
                crashCallback?.invoke(throwable)
            } catch (cbEx: Exception) {
                Log.e(TAG, "Crash callback threw exception, masking real crash is prevented", cbEx)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in crash handler", e)
        } finally {
            val handler = defaultHandler
            if (handler != null && handler !== this) {
                try {
                    handler.uncaughtException(thread, throwable)
                    return
                } catch (e: Throwable) {
                    Log.e(TAG, "Default handler failed, forcing exit", e)
                }
            }
            killProcess()
        }
    }

    private fun killProcess() {
        try {
            android.os.Process.killProcess(android.os.Process.myPid())
        } catch (_: Throwable) {}
        Runtime.getRuntime().halt(1)
    }

    private fun writeLegacyTrace(thread: Thread, throwable: Throwable) {
        val context = appContext ?: return
        try {
            val crashDir = File(context.filesDir, "crash_reports")
            if (!crashDir.exists()) crashDir.mkdirs()
            val timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", java.util.Locale.US))
            val legacyFile = File(crashDir, "crash_${timestamp}.log")
            val stringWriter = StringWriter()
            PrintWriter(stringWriter).use { pw -> throwable.printStackTrace(pw) }
            val sanitized = runCatching { CrashReportService.sanitizeStackTrace(stringWriter.toString()) }
                .getOrElse { stringWriter.toString() }
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
        crashDir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    fun hasRecentCrash(): Boolean {
        val reports = getCrashReports()
        if (reports.isEmpty()) return false
        val recent = reports.first()
        val age = System.currentTimeMillis() - recent.lastModified()
        return age < 60_000
    }

    fun isCrashLoopDetected(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_CRASH_LOOP, Context.MODE_PRIVATE)
            val lastTime = prefs.getLong(KEY_LAST_CRASH_TIME, 0L)
            val count = prefs.getInt(KEY_CRASH_COUNT, 0)
            System.currentTimeMillis() - lastTime < CRASH_LOOP_THRESHOLD_MS && count >= MAX_CRASH_LOOP_COUNT - 1
        } catch (_: Throwable) {
            false
        }
    }
}
