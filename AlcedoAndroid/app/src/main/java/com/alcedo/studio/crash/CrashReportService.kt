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
 * The service is started at app launch via [CrashReportService.start] and runs
 * indefinitely (foreground service on API 26+, where the manifest declares
 * `FOREGROUND_SERVICE_DATA_SYNC`). The actual crash file is consumed by the
 * settings / diagnostics screen on next launch.
 */
class CrashReportService : Service() {

    override fun onCreate() {
        super.onCreate()
        installHandler()
        startForegroundIfNeeded()
        Log.i(TAG, "CrashReportService ready in pid=${Process.myPid()} process=\"${getProcessNameCompat()}\"")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The service is sticky: even if killed, restart it so crashes keep
        // being captured. No intent payload is expected.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Write the current crash log to disk and return its path. */
    private fun installHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
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
        file.writeText(sw.toString())
        Log.w(TAG, "Crash report written to ${file.absolutePath}")
    }

    private fun getProcessNameCompat(): String =
        // Application.getProcessName() is the public API on API 28+ (minSdk = 29).
        runCatching { android.app.Application.getProcessName() }.getOrNull() ?: "unknown"

    companion object {
        private const val TAG = "CrashReportService"
        private const val CHANNEL_ID = "alcedo_crash"
        private const val NOTIFICATION_ID = 0xCA01

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
