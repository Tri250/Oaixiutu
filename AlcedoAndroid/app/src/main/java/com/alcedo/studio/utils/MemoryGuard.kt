package com.alcedo.studio.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors process memory pressure and exposes a coarse [MemoryPressure] level
 * so services can shed caches or refuse large allocations when the device is
 * under stress. Used by the decode/pipeline services to bound concurrent work.
 */
class MemoryGuard(private val context: Context) {

    enum class MemoryPressure { OK, MODERATE, HIGH, CRITICAL }

    private val _pressure = MutableStateFlow(MemoryPressure.OK)
    val pressure: StateFlow<MemoryPressure> = _pressure.asStateFlow()

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /** Refresh and return the current memory pressure level. */
    fun refresh(): MemoryPressure {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        val avail = info.availMem
        val threshold = info.threshold
        val total = info.totalMem.takeIf { it > 0 } ?: runtimeMax()

        val level = when {
            avail < threshold -> MemoryPressure.CRITICAL
            avail < threshold * 2 -> MemoryPressure.HIGH
            avail < total / 6 -> MemoryPressure.MODERATE
            else -> MemoryPressure.OK
        }
        _pressure.value = level
        if (level != MemoryPressure.OK) {
            Log.w(TAG, "Memory pressure=$level avail=${avail / MB}MB total=${total / MB}MB")
        }
        return level
    }

    /** True if allocating [bytes] is currently advisable. */
    fun canAllocate(bytes: Long): Boolean {
        val level = refresh()
        if (level == MemoryPressure.CRITICAL) return false
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return info.availMem - bytes > info.threshold
    }

    /** Suggested max pixel dimension for a fresh decode given current pressure. */
    fun suggestedMaxDim(): Int = when (refresh()) {
        MemoryPressure.CRITICAL -> 1024
        MemoryPressure.HIGH -> 1536
        MemoryPressure.MODERATE -> 2048
        MemoryPressure.OK -> 4096
    }

    /** Per-process memory class (MB), used to size caches. */
    fun memoryClassMb(): Int = activityManager.memoryClass

    /** Whether the large heap flag granted a higher memory class. */
    fun largeMemoryClassMb(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) activityManager.largeMemoryClass
        else activityManager.memoryClass

    private fun runtimeMax(): Long = Runtime.getRuntime().maxMemory()

    companion object {
        private const val TAG = "MemoryGuard"
        private const val MB = 1024L * 1024L
    }
}
