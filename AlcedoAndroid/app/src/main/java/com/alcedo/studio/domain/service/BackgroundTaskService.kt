package com.alcedo.studio.domain.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class TaskType {
    IMPORT, EXPORT, AI_TAGGING, MODEL_DOWNLOAD,
    THUMBNAIL_GEN, SEMANTIC_INDEXING
}

enum class TaskPriority(val value: Int) {
    LOW(0), NORMAL(1), HIGH(2), CRITICAL(3)
}

enum class TaskStatus {
    PENDING, RUNNING, COMPLETED, FAILED, CANCELLED, RETRYING
}

data class BackgroundTask(
    val taskId: String = UUID.randomUUID().toString(),
    val type: TaskType,
    val title: String,
    val description: String = "",
    val priority: TaskPriority = TaskPriority.NORMAL,
    val cancellable: Boolean = true,
    val maxRetries: Int = 3,
    var status: TaskStatus = TaskStatus.PENDING,
    var progress: Float = 0f,
    var etaMillis: Long = 0L,
    val createdAt: Instant = Instant.now(),
    var startedAt: Instant? = null,
    var completedAt: Instant? = null,
    var errorMessage: String? = null,
    var retryCount: Int = 0,
    val interactionLockKeys: Set<String> = emptySet(),
    val metadata: Map<String, String> = emptyMap()
) {
    val isCompleted: Boolean get() = status == TaskStatus.COMPLETED
    val isFailed: Boolean get() = status == TaskStatus.FAILED
    val isActive: Boolean get() = status == TaskStatus.RUNNING || status == TaskStatus.RETRYING
    val isPending: Boolean get() = status == TaskStatus.PENDING

    fun getProgressPercent(): Int = (progress * 100).toInt().coerceIn(0, 100)
}

data class TaskSnapshot(
    val taskId: String,
    val type: TaskType,
    val title: String,
    val status: TaskStatus,
    val progress: Float,
    val etaMillis: Long,
    val createdAt: Instant,
    val completedAt: Instant?,
    val errorMessage: String?
)

class BackgroundTaskService(private val context: Context) {
    companion object {
        private const val TAG = "BackgroundTaskService"
        private const val CHANNEL_ID = "alcedo_tasks"
        private const val CHANNEL_NAME = "Background Tasks"
        private const val NOTIFICATION_ID_BASE = 1000
        private const val MAX_TASK_HISTORY = 100
        private const val TASK_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val taskExecutors = ConcurrentHashMap<TaskType, suspend (BackgroundTask) -> Unit>()
    private val activeTasks = ConcurrentHashMap<String, BackgroundTask>()
    private val taskHistory = mutableListOf<TaskSnapshot>()
    private val interactionLocks = ConcurrentHashMap<String, String>() // lockKey -> taskId
    private val taskQueue = Channel<Pair<BackgroundTask, TaskPriority>>(Channel.UNLIMITED)
    private val runningCount = AtomicInteger(0)
    private val maxConcurrent = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)

    private val _tasks = MutableStateFlow<List<TaskSnapshot>>(emptyList())
    val tasks: StateFlow<List<TaskSnapshot>> = _tasks.asStateFlow()

    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
        startWorker()
    }

    // ── Task Executor Registration ──

    fun registerExecutor(type: TaskType, executor: suspend (BackgroundTask) -> Unit) {
        taskExecutors[type] = executor
    }

    // ── Enqueue ──

    fun enqueue(
        type: TaskType,
        title: String,
        description: String = "",
        priority: TaskPriority = TaskPriority.NORMAL,
        cancellable: Boolean = true,
        maxRetries: Int = 3,
        interactionLockKeys: Set<String> = emptySet(),
        metadata: Map<String, String> = emptyMap()
    ): String {
        val taskId = UUID.randomUUID().toString()
        val task = BackgroundTask(
            taskId = taskId,
            type = type,
            title = title,
            description = description,
            priority = priority,
            cancellable = cancellable,
            maxRetries = maxRetries,
            interactionLockKeys = interactionLockKeys,
            metadata = metadata
        )
        enqueueTask(task)
        return taskId
    }

    fun enqueueTask(task: BackgroundTask) {
        activeTasks[task.taskId] = task
        scope.launch {
            taskQueue.send(task to task.priority)
        }
        emitTasks()
    }

    // ── Cancel ──

    fun cancel(taskId: String) {
        val task = activeTasks[taskId] ?: return
        if (!task.cancellable) return

        task.status = TaskStatus.CANCELLED
        task.completedAt = Instant.now()
        releaseInteractionLocks(taskId)
        addToHistory(task)
        activeTasks.remove(taskId)
        emitTasks()
        cancelNotification(taskId)
        Log.i(TAG, "Task cancelled: ${task.title}")
    }

    fun cancelAll() {
        activeTasks.keys.toList().forEach { cancel(it) }
    }

    fun cancelByType(type: TaskType) {
        activeTasks.values.filter { it.type == type && it.isActive }.forEach { cancel(it.taskId) }
    }

    // ── Progress Tracking ──

    fun updateProgress(taskId: String, progress: Float, etaMillis: Long = 0L) {
        val task = activeTasks[taskId] ?: return
        task.progress = progress.coerceIn(0f, 1f)
        task.etaMillis = etaMillis
        if (task.status == TaskStatus.RUNNING && task.progress > 0f) {
            updateNotification(task)
        }
        emitTasks()
    }

    fun setProgress(taskId: String, current: Int, total: Int) {
        if (total > 0) {
            updateProgress(taskId, current.toFloat() / total)
        }
    }

    // ── Interaction Lock ──

    fun acquireInteractionLock(taskId: String, lockKey: String): Boolean {
        return interactionLocks.putIfAbsent(lockKey, taskId) == null
    }

    fun releaseInteractionLock(taskId: String, lockKey: String) {
        interactionLocks.remove(lockKey, taskId)
    }

    fun isLocked(lockKey: String): Boolean = lockKey in interactionLocks

    fun getLockHolder(lockKey: String): String? = interactionLocks[lockKey]

    // ── Task Queries ──

    fun getTask(taskId: String): BackgroundTask? = activeTasks[taskId]

    fun getActiveTasks(): List<BackgroundTask> = activeTasks.values.toList()

    fun getTasksByType(type: TaskType): List<BackgroundTask> =
        activeTasks.values.filter { it.type == type }

    fun getTaskHistory(): List<TaskSnapshot> = taskHistory.toList()

    fun getTaskHistory(type: TaskType): List<TaskSnapshot> =
        taskHistory.filter { it.type == type }

    fun clearHistory() {
        taskHistory.clear()
    }

    fun getActiveCount(): Int = activeTasks.values.count { it.isActive }

    // ── Private Worker ──

    private fun startWorker() {
        // Start multiple workers for concurrent processing
        repeat(maxConcurrent) { workerIndex ->
            scope.launch {
                // Sort queue by priority before processing
                val pending = mutableListOf<Pair<BackgroundTask, TaskPriority>>()
                for ((task, priority) in taskQueue) {
                    pending.add(task to priority)
                    // Collect a batch and sort by priority
                    if (taskQueue.isEmpty) {
                        processBatch(pending.filter { it.first.status == TaskStatus.PENDING }
                            .sortedByDescending { it.second.value })
                        pending.clear()
                    }
                }
            }
        }
    }

    private suspend fun processBatch(batch: List<Pair<BackgroundTask, TaskPriority>>) {
        for ((task, _) in batch) {
            if (task.status != TaskStatus.PENDING) continue

            // Check interaction locks
            val blockedBy = task.interactionLockKeys.mapNotNull { interactionLocks[it] }.firstOrNull()
            if (blockedBy != null) {
                // Re-queue at lower priority
                scope.launch { taskQueue.send(task to TaskPriority.LOW) }
                continue
            }

            executeTask(task)
        }
    }

    private suspend fun executeTask(task: BackgroundTask) {
        val executor = taskExecutors[task.type]
        if (executor == null) {
            task.status = TaskStatus.FAILED
            task.errorMessage = "No executor registered for ${task.type}"
            task.completedAt = Instant.now()
            addToHistory(task)
            activeTasks.remove(task.taskId)
            emitTasks()
            return
        }

        // Acquire interaction locks
        val acquiredLocks = mutableListOf<String>()
        for (lockKey in task.interactionLockKeys) {
            if (!acquireInteractionLock(task.taskId, lockKey)) {
                // Failed to acquire lock, requeue
                scope.launch { taskQueue.send(task to TaskPriority.LOW) }
                return
            }
            acquiredLocks.add(lockKey)
        }

        task.status = TaskStatus.RUNNING
        task.startedAt = Instant.now()
        runningCount.incrementAndGet()
        _activeCount.value = runningCount.get()
        showNotification(task)
        emitTasks()

        try {
            withTimeout(TASK_TIMEOUT_MS) {
                executor(task)
            }
            task.status = TaskStatus.COMPLETED
            task.progress = 1f
            task.completedAt = Instant.now()
            completeNotification(task)
            Log.i(TAG, "Task completed: ${task.title}")
        } catch (e: CancellationException) {
            task.status = TaskStatus.CANCELLED
            task.completedAt = Instant.now()
            cancelNotification(task.taskId)
            Log.i(TAG, "Task cancelled: ${task.title}")
        } catch (e: TimeoutCancellationException) {
            handleTaskFailure(task, "Task timed out", acquiredLocks)
        } catch (e: Exception) {
            handleTaskFailure(task, e.message ?: "Unknown error", acquiredLocks)
        } finally {
            runningCount.decrementAndGet()
            _activeCount.value = runningCount.get()
            releaseInteractionLocks(task.taskId)
            addToHistory(task)
            activeTasks.remove(task.taskId)
            emitTasks()
        }
    }

    private fun handleTaskFailure(
        task: BackgroundTask,
        error: String,
        acquiredLocks: List<String>
    ) {
        task.errorMessage = error
        Log.e(TAG, "Task failed: ${task.title} - $error")

        if (task.retryCount < task.maxRetries) {
            task.retryCount++
            task.status = TaskStatus.RETRYING
            // Re-enqueue with exponential backoff
            val delayMs = 1000L * (1 shl (task.retryCount - 1))
            scope.launch {
                delay(delayMs)
                task.status = TaskStatus.PENDING
                taskQueue.send(task to task.priority)
            }
            Log.i(TAG, "Retrying task ${task.title} (attempt ${task.retryCount}/${task.maxRetries})")
        } else {
            task.status = TaskStatus.FAILED
            task.completedAt = Instant.now()
            failNotification(task)
            releaseInteractionLocks(task.taskId)
        }
    }

    private fun releaseInteractionLocks(taskId: String) {
        interactionLocks.entries.removeAll { it.value == taskId }
    }

    // ── Notification Management ──

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for background tasks"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(task: BackgroundTask) {
        val notificationId = getNotificationId(task.taskId)
        val notification = buildNotification(task, task.progress)
        notificationManager.notify(notificationId, notification)
    }

    /**
     * Start a foreground service notification with Android 14+ compliance.
     * Call this from an Android Service subclass when running as a foreground service.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun startForegroundNotification(service: Service, task: BackgroundTask) {
        val notification = buildNotification(task, task.progress)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: Must specify foreground service type
            service.startForeground(
                NOTIFICATION_ID_BASE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            service.startForeground(NOTIFICATION_ID_BASE, notification)
        }
    }

    private fun updateNotification(task: BackgroundTask) {
        val notificationId = getNotificationId(task.taskId)
        val notification = buildNotification(task, task.progress)
        notificationManager.notify(notificationId, notification)
    }

    private fun completeNotification(task: BackgroundTask) {
        val notificationId = getNotificationId(task.taskId)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Completed: ${task.title}")
            .setContentText(task.description.ifEmpty { "Task completed successfully" })
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(false)
            .setProgress(0, 0, false)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun failNotification(task: BackgroundTask) {
        val notificationId = getNotificationId(task.taskId)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Failed: ${task.title}")
            .setContentText(task.errorMessage ?: "Task failed")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun cancelNotification(taskId: String) {
        val notificationId = getNotificationId(taskId)
        notificationManager.cancel(notificationId)
    }

    private fun buildNotification(task: BackgroundTask, progress: Float): android.app.Notification {
        val cancelIntent = Intent("com.alcedo.studio.CANCEL_TASK").apply {
            putExtra("task_id", task.taskId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context, task.taskId.hashCode(), cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(task.title)
            .setContentText(task.description.ifEmpty {
                when (task.type) {
                    TaskType.IMPORT -> "Importing images..."
                    TaskType.EXPORT -> "Exporting images..."
                    TaskType.AI_TAGGING -> "Analyzing images..."
                    TaskType.MODEL_DOWNLOAD -> "Downloading AI model..."
                    TaskType.THUMBNAIL_GEN -> "Generating thumbnails..."
                    TaskType.SEMANTIC_INDEXING -> "Indexing images..."
                }
            })
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setProgress(100, task.getProgressPercent(), false)
            .setSubText("${task.getProgressPercent()}%")
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun getNotificationId(taskId: String): Int {
        return NOTIFICATION_ID_BASE + (taskId.hashCode() and 0x7FFFFFFF) % 1000
    }

    // ── History ──

    private fun addToHistory(task: BackgroundTask) {
        val snapshot = TaskSnapshot(
            taskId = task.taskId,
            type = task.type,
            title = task.title,
            status = task.status,
            progress = task.progress,
            etaMillis = task.etaMillis,
            createdAt = task.createdAt,
            completedAt = task.completedAt,
            errorMessage = task.errorMessage
        )
        taskHistory.add(0, snapshot)
        if (taskHistory.size > MAX_TASK_HISTORY) {
            taskHistory.removeAt(taskHistory.size - 1)
        }
    }

    private fun emitTasks() {
        _tasks.value = activeTasks.values.map { task ->
            TaskSnapshot(
                taskId = task.taskId,
                type = task.type,
                title = task.title,
                status = task.status,
                progress = task.progress,
                etaMillis = task.etaMillis,
                createdAt = task.createdAt,
                completedAt = task.completedAt,
                errorMessage = task.errorMessage
            )
        }
    }

    // ── Cleanup ──

    fun shutdown() {
        cancelAll()
        scope.cancel()
        notificationManager.cancelAll()
    }
}