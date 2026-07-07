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

/**
 * Helper for showing task progress in the notification bar.
 * Supports concurrent tasks with individual progress tracking.
 */
object TaskNotificationHelper {

    private const val CHANNEL_ID = "alcedo_task_progress"
    private const val CHANNEL_NAME = "任务进度"

    // Notification IDs for different task types
    private const val NOTIFICATION_ID_EXPORT = 2001
    private const val NOTIFICATION_ID_AI_RATING = 2002
    private const val NOTIFICATION_ID_AI_TAGGING = 2003
    private const val NOTIFICATION_ID_IMPORT = 2004
    private const val NOTIFICATION_ID_MODEL_DOWNLOAD = 2005

    private var channelCreated = false

    private fun ensureChannel(context: Context) {
        if (channelCreated) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示导出、AI处理等后台任务的进度"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
        channelCreated = true
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
        val msg = if (fileName.isNotBlank()) "正在导出: $fileName" else "正在导出 $current/$total"
        nm.notify(NOTIFICATION_ID_EXPORT, buildNotification(context, "导出中", msg, current, total))
    }

    fun notifyExportComplete(context: Context, successCount: Int, total: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val msg = "成功: $successCount/$total"
        nm.notify(NOTIFICATION_ID_EXPORT, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle("导出完成")
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
            .setContentTitle("导出失败")
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
        nm.notify(NOTIFICATION_ID_AI_RATING, buildNotification(context, "AI 评分中", "正在评分 $current/$total", current, total))
    }

    fun notifyAiRatingComplete(context: Context, total: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID_AI_RATING)
    }

    // ── AI Tagging notifications ──

    fun notifyAiTaggingProgress(context: Context, current: Int, total: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_AI_TAGGING, buildNotification(context, "AI 标签生成中", "正在处理 $current/$total", current, total))
    }

    fun notifyAiTaggingComplete(context: Context, total: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID_AI_TAGGING)
    }

    // ── Import notifications ──

    fun notifyImportProgress(context: Context, current: Int, total: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_IMPORT, buildNotification(context, "图片导入中", "正在导入 $current/$total", current, total))
    }

    fun notifyImportComplete(context: Context, total: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID_IMPORT)
    }

    // ── Model download notifications ──

    fun notifyModelDownloadProgress(context: Context, modelId: String, progress: Int, total: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val msg = "正在下载 $modelId... $progress%"
        nm.notify(NOTIFICATION_ID_MODEL_DOWNLOAD, buildNotification(context, "模型下载", msg, progress, total))
    }

    fun notifyModelDownloadComplete(context: Context, modelId: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_MODEL_DOWNLOAD, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle("模型下载完成")
            .setContentText("$modelId 下载完成")
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
            .setContentTitle("模型下载失败")
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
