package com.alcedo.studio.domain.service

import com.alcedo.studio.data.model.BackgroundTaskInfo
import com.alcedo.studio.data.model.BackgroundTaskType
import com.alcedo.studio.utils.IdGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

/**
 * Background task management. Tracks long-running operations (import, export,
 * AI embedding, model download) and exposes a reactive list for the
 * [BackgroundTaskBar] overlay. Supports ETA and cancellation flags.
 */
@Singleton
class BackgroundTaskService @Inject constructor() {

    private val _tasks = MutableStateFlow<List<BackgroundTaskInfo>>(emptyList())
    val tasks: StateFlow<List<BackgroundTaskInfo>> = _tasks.asStateFlow()

    private val active = ConcurrentHashMap<String, BackgroundTaskInfo>()
    private val startTimes = ConcurrentHashMap<String, Long>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    /** Register and start tracking a task. Returns its id. */
    fun start(
        type: BackgroundTaskType,
        title: String,
        totalItems: Int = 0,
        cancellable: Boolean = true,
    ): String {
        val id = IdGenerator.newId("task")
        val completedItems = 0
        val info = BackgroundTaskInfo(
            id = id, type = type, title = title,
            progress = if (totalItems > 0) completedItems.toFloat() / totalItems else 0f,
            indeterminate = totalItems == 0,
            totalItems = totalItems, completedItems = completedItems,
            cancellable = cancellable,
        )
        active[id] = info
        startTimes[id] = System.currentTimeMillis()
        publish()
        return id
    }

    /** Update progress for [taskId]. [completed] and [total] drive the fraction + ETA. */
    fun update(taskId: String, completed: Int, total: Int) {
        val current = active[taskId] ?: return
        val fraction = if (total > 0) completed.toFloat() / total.toFloat() else current.progress
        val eta = computeEta(taskId, completed, total)
        active[taskId] = current.copy(
            progress = fraction.coerceIn(0f, 1f),
            completedItems = completed,
            totalItems = total,
            indeterminate = total == 0,
            etaMs = eta,
        )
        publish()
    }

    /** Mark a task complete with an optional error. */
    fun complete(taskId: String, error: String? = null) {
        val current = active[taskId] ?: return
        active[taskId] = current.copy(
            progress = 1f, completedItems = current.totalItems,
            etaMs = null, error = error,
        )
        publish()
        // Auto-evict successful tasks; keep failures for inspection until dismissed.
        if (error == null) {
            active.remove(taskId)
            startTimes.remove(taskId)
            publish()
        }
    }

    /**
     * Request cancellation of [taskId]. Sets a volatile flag (the [cancelled]
     * set) that running tasks must poll via [isCancelled] to abort early.
     * The task is marked as cancelled and evicted from the active set.
     */
    fun cancel(taskId: String) {
        // Set the cancellation flag first so any task polling isCancelled() sees it.
        cancelled.add(taskId)
        val current = active[taskId]
        if (current != null) {
            active[taskId] = current.copy(error = "cancelled", progress = 1f)
            publish()
        }
        // Evict the cancelled task so it no longer counts as active.
        active.remove(taskId)
        startTimes.remove(taskId)
        // Clean up the cancellation flag after eviction so it doesn't accumulate.
        cancelled.remove(taskId)
        publish()
    }

    /** True when [taskId] has been cancelled via [cancel]. Running tasks should poll this. */
    fun isCancelled(taskId: String): Boolean = cancelled.contains(taskId)

    /** Remove a finished task from the list. */
    fun dismiss(taskId: String) {
        active.remove(taskId)
        startTimes.remove(taskId)
        cancelled.remove(taskId)
        publish()
    }

    /** Number of currently active (non-complete) tasks. */
    fun activeCount(): Int = active.values.count { it.error == null && it.progress < 1f }

    private fun computeEta(taskId: String, completed: Int, total: Int): Long? {
        if (completed <= 0 || total <= 0) return null
        val elapsed = System.currentTimeMillis() - (startTimes[taskId] ?: return null)
        val perItem = elapsed.toDouble() / completed
        val remaining = (total - completed).coerceAtLeast(0)
        return (perItem * remaining).toLong()
    }

    private fun publish() {
        _tasks.value = active.values.sortedByDescending { it.progress }
    }
}
