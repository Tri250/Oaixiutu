package com.alcedo.studio.utils

import android.util.Log
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

object ThreadPool {
    private val cpuCount = Runtime.getRuntime().availableProcessors()
    private val corePoolSize = cpuCount.coerceAtLeast(2)
    private val maxPoolSize = cpuCount * 2

    val ioExecutor: ExecutorService = Executors.newFixedThreadPool(corePoolSize,
        ThreadFactory("Alcedo-IO"))

    val computeExecutor: ExecutorService = ThreadPoolExecutor(
        corePoolSize, maxPoolSize, 30L, TimeUnit.SECONDS,
        LinkedBlockingQueue(),
        ThreadFactory("Alcedo-Compute")
    )

    val singleExecutor: ExecutorService = Executors.newSingleThreadExecutor(
        ThreadFactory("Alcedo-Single"))

    fun shutdown() {
        ioExecutor.shutdown()
        computeExecutor.shutdown()
        singleExecutor.shutdown()
    }

    private class ThreadFactory(private val prefix: String) : java.util.concurrent.ThreadFactory {
        private val counter = AtomicInteger(0)
        override fun newThread(r: Runnable): Thread {
            return Thread(r, "$prefix-${counter.incrementAndGet()}").apply {
                isDaemon = true
                setUncaughtExceptionHandler { thread, throwable ->
                    Log.e("ThreadPool", "Uncaught exception in ${thread.name}", throwable)
                }
            }
        }
    }
}
