package com.alcedo.studio.utils

import android.util.Log
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ThreadPoolExecutor

object ThreadPool {
    private val cpuCount = Runtime.getRuntime().availableProcessors()
    private val corePoolSize = cpuCount.coerceAtLeast(2)
    private val maxPoolSize = cpuCount * 2

    private const val QUEUE_CAPACITY_IO = 256
    private const val QUEUE_CAPACITY_COMPUTE = 128

    private class CallerRunsWithLoggingPolicy(private val name: String) : RejectedExecutionHandler {
        override fun rejectedExecution(r: Runnable, executor: ThreadPoolExecutor) {
            Log.w("ThreadPool", "$name queue is full (${executor.queue.size}), " +
                    "running on caller thread to prevent OOM")
            if (!executor.isShutdown) {
                try {
                    r.run()
                } catch (e: Throwable) {
                    Log.e("ThreadPool", "Error in caller-runs fallback for $name", e)
                }
            }
        }
    }

    val ioExecutor: ExecutorService = ThreadPoolExecutor(
        corePoolSize, maxPoolSize,
        60L, TimeUnit.SECONDS,
        LinkedBlockingQueue(QUEUE_CAPACITY_IO),
        ThreadFactory("Alcedo-IO"),
        CallerRunsWithLoggingPolicy("IO")
    )

    val computeExecutor: ExecutorService = ThreadPoolExecutor(
        corePoolSize, maxPoolSize, 30L, TimeUnit.SECONDS,
        LinkedBlockingQueue(QUEUE_CAPACITY_COMPUTE),
        ThreadFactory("Alcedo-Compute"),
        CallerRunsWithLoggingPolicy("Compute")
    )

    val singleExecutor: ExecutorService = ThreadPoolExecutor(
        1, 1,
        0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(512),
        ThreadFactory("Alcedo-Single"),
        CallerRunsWithLoggingPolicy("Single")
    )

    fun shutdown() {
        try { ioExecutor.shutdown() } catch (e: Throwable) { Log.e("ThreadPool", "shutdown ioExecutor failed", e) }
        try { computeExecutor.shutdown() } catch (e: Throwable) { Log.e("ThreadPool", "shutdown computeExecutor failed", e) }
        try { singleExecutor.shutdown() } catch (e: Throwable) { Log.e("ThreadPool", "shutdown singleExecutor failed", e) }
        try {
            listOf(ioExecutor, computeExecutor, singleExecutor).forEach { exec ->
                if (!exec.awaitTermination(2, TimeUnit.SECONDS)) {
                    exec.shutdownNow()
                }
            }
        } catch (e: InterruptedException) {
            Log.e("ThreadPool", "awaitTermination interrupted", e)
            listOf(ioExecutor, computeExecutor, singleExecutor).forEach { it.shutdownNow() }
        }
    }

    fun getQueueStatus(): Map<String, Int> {
        return mapOf(
            "io" to ((ioExecutor as? ThreadPoolExecutor)?.queue?.size ?: 0),
            "compute" to ((computeExecutor as? ThreadPoolExecutor)?.queue?.size ?: 0),
            "single" to ((singleExecutor as? ThreadPoolExecutor)?.queue?.size ?: 0)
        )
    }

    private class ThreadFactory(private val prefix: String) : java.util.concurrent.ThreadFactory {
        private val counter = AtomicInteger(0)
        override fun newThread(r: Runnable): Thread {
            return Thread(r, "$prefix-${counter.incrementAndGet()}").apply {
                isDaemon = true
                priority = when (prefix) {
                    "Alcedo-Compute" -> Thread.NORM_PRIORITY - 1
                    "Alcedo-IO" -> Thread.NORM_PRIORITY
                    else -> Thread.NORM_PRIORITY
                }
                setUncaughtExceptionHandler { thread, throwable ->
                    Log.e("ThreadPool", "Uncaught exception in ${thread.name}", throwable)
                    try {
                        com.alcedo.studio.crash.CrashReportService.logEvent(
                            "thread_crash:${thread.name}:${throwable.javaClass.simpleName}"
                        )
                    } catch (_: Throwable) {}
                }
            }
        }
    }
}
