package com.alcedo.studio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.alcedo.studio.MainActivity
import com.alcedo.studio.i18n.Strings

/**
 * Helper for showing task progress in the notification bar.
 * Supports concurrent tasks with individual progress tracking.
 * All notification text is localized via the i18n system.
 */
object TaskNotificationHelper {

    private const val CHANNEL_ID = "alcedo_task_progress"

    // Notification IDs for different task types
    private const val NOTIFICATION_ID_EXPORT = 2001
    private const val NOTIFICATION_ID_AI_RATING = 2002
    private const val NOTIFICATION_ID_AI_TAGGING = 2003
    private const val NOTIFICATION_ID_IMPORT = 2004
    private const val NOTIFICATION_ID_MODEL_DOWNLOAD = 2005

    @Volatile
    private var channelCreated = false
    private val channelLock = Any()

    private fun ensureChannel(context: Context) {
        if (channelCreated) return
        synchronized(channelLock) {
            if (channelCreated) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    Strings.current.notificationChannelName,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = Strings.current.notificationChannelDesc
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
            channelCreated = true
        }
    }

    private fun buildNotification(
        context: Context,
        title: String,
        message: String,
        progress: Int,
        max: Int,
        indeterminate: Boolean = false
    ): Notification {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle(title)
            .setContentText(message)
            .setProgress(max, progress, indeterminate)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()
    }

    // ── Export notifications ──

    fun notifyExportProgress(context: Context, current: Int, total: Int, fileName: String = "") {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val msg = if (fileName.isNotBlank()) {
            String.format(Strings.current.notificationExporting, fileName)
        } else {
            String.format(Strings.current.notificationExportProgress, current, total)
        }
        nm.notify(NOTIFICATION_ID_EXPORT, buildNotification(context, Strings.current.notificationExportTitle, msg, current, total))
    }

    fun notifyExportComplete(context: Context, successCount: Int, total: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val msg = String.format(Strings.current.notificationExportSuccess, successCount, total)
        nm.notify(NOTIFICATION_ID_EXPORT, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle(Strings.current.notificationExportComplete)
            .setContentText(msg)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .build())
        // Auto-cancel after 5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            nm.cancel(NOTIFICATION_ID_EXPORT)
        }, 5000)
    }

    fun notifyExportFailed(context: Context, errorMessage: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_EXPORT, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle(Strings.current.notificationExportFailed)
            .setContentText(errorMessage)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .build())
    }

    fun cancelExport(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID_EXPORT)
    }

    // ── AI Rating notifications ──

    fun notifyAiRatingProgress(context: Context, current: Int, total: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val msg = String.format(Strings.current.notificationAiRatingProgress, current, total)
        nm.notify(NOTIFICATION_ID_AI_RATING, buildNotification(context, Strings.current.notificationAiRating, msg, current, total))
    }

    fun notifyAiRatingComplete(context: Context, total: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID_AI_RATING)
    }

    // ── AI Tagging notifications ──

    fun notifyAiTaggingProgress(context: Context, current: Int, total: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val msg = String.format(Strings.current.notificationAiTaggingProgress, current, total)
        nm.notify(NOTIFICATION_ID_AI_TAGGING, buildNotification(context, Strings.current.notificationAiTagging, msg, current, total))
    }

    fun notifyAiTaggingComplete(context: Context, total: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID_AI_TAGGING)
    }

    // ── Import notifications ──

    fun notifyImportProgress(context: Context, current: Int, total: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val msg = String.format(Strings.current.notificationImportProgress, current, total)
        nm.notify(NOTIFICATION_ID_IMPORT, buildNotification(context, Strings.current.notificationImageImport, msg, current, total))
    }

    fun notifyImportComplete(context: Context, total: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID_IMPORT)
    }

    // ── Model download notifications ──

    fun notifyModelDownloadProgress(context: Context, modelId: String, progress: Int, total: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val msg = String.format(Strings.current.notificationModelDownloadProgress, modelId, progress)
        nm.notify(NOTIFICATION_ID_MODEL_DOWNLOAD, buildNotification(context, Strings.current.notificationModelDownload, msg, progress, total))
    }

    fun notifyModelDownloadComplete(context: Context, modelId: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_MODEL_DOWNLOAD, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle(Strings.current.notificationModelDownloadComplete)
            .setContentText(String.format(Strings.current.notificationModelDownloadFinished, modelId))
            .setProgress(0, 0, false)
            .setOngoing(false)
            .build())
        Handler(Looper.getMainLooper()).postDelayed({
            nm.cancel(NOTIFICATION_ID_MODEL_DOWNLOAD)
        }, 3000)
    }

    fun notifyModelDownloadFailed(context: Context, modelId: String, error: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_MODEL_DOWNLOAD, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle(Strings.current.notificationModelDownloadFailed)
            .setContentText("$modelId: $error")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .build())
    }

    fun cancelModelDownload(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID_MODEL_DOWNLOAD)
    }
}
