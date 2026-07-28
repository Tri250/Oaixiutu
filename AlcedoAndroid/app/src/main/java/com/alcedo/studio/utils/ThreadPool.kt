package com.alcedo.studio.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Centralised coroutine dispatchers backed by fixed thread pools. The editor uses
 * dedicated, bounded pools for CPU-heavy work (decoding, AI inference, pipeline
 * staging) so they don't starve the main / IO dispatchers.
 *
 * Provided as plain objects; Hilt binds them in [com.alcedo.studio.di.AppModule].
 */
object ThreadPool {

    /** Heavy CPU work: RAW decode, pipeline staging, scope analysis. */
    val compute: CoroutineDispatcher by lazy {
        Executors.newFixedThreadPool(cpuCount(), namedFactory("alcedo-compute-%d"))
            .asCoroutineDispatcher()
    }

    /** AI inference (ONNX): single-threaded to avoid contention on accelerators. */
    val aiInference: CoroutineDispatcher by lazy {
        Executors.newFixedThreadPool(2, namedFactory("alcedo-ai-%d"))
            .asCoroutineDispatcher()
    }

    /** Thumbnail generation pipeline. */
    val thumbnail: CoroutineDispatcher by lazy {
        Executors.newFixedThreadPool(
            (cpuCount() / 2).coerceAtLeast(2),
            namedFactory("alcedo-thumb-%d"),
        ).asCoroutineDispatcher()
    }

    /** Database / DuckDB bridging work. */
    val database: CoroutineDispatcher = Dispatchers.IO

    /** Default IO. */
    val io: CoroutineDispatcher = Dispatchers.IO

    /** A supervisor scope owned by the application for fire-and-forget work. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + compute)

    private fun cpuCount(): Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)

    private fun namedFactory(prefix: String): ThreadFactory = object : ThreadFactory {
        private val counter = AtomicInteger(0)
        override fun newThread(r: Runnable): Thread =
            Thread(r, "$prefix-${counter.incrementAndGet()}").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY
            }
    }
}

/** Convenience accessor for executor services backing the dispatchers. */
object ThreadPoolExecutors {
    val computeExecutor: ExecutorService by lazy {
        Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors().coerceIn(2, 8),
        ) { r -> Thread(r, "alcedo-compute").apply { isDaemon = true } }
    }

    val aiExecutor: ExecutorService by lazy {
        Executors.newFixedThreadPool(2) { r -> Thread(r, "alcedo-ai").apply { isDaemon = true } }
    }
}
