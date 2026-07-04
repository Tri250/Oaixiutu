package com.alcedo.studio.utils

object TimeProvider {
    fun currentTimeMillis(): Long = System.currentTimeMillis()

    fun elapsedRealtime(): Long = android.os.SystemClock.elapsedRealtime()

    fun nanoTime(): Long = System.nanoTime()
}
