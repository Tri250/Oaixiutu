package com.alcedo.studio.ndk

import android.util.Log

/**
 * Safe wrapper around native JNI calls. Catches [UnsatisfiedLinkError] (library
 * not loaded / symbol missing) and generic [Throwable] (native exceptions
 * re-thrown as Java exceptions), logs them, and returns a caller-supplied
 * default. This keeps the UI responsive when the native layer is unavailable
 * (e.g. running on an emulator without Vulkan) instead of crashing.
 */
object NdkSafeCall {

    private const val TAG = "NdkSafeCall"

    /** True when the native bridge has loaded successfully. */
    val isAvailable: Boolean get() = AlcedoNativeBridge.isLoaded

    /**
     * Run [block] and return its result, or [default] on any failure. Native
     * methods that return primitive/object values should use this.
     */
    fun <T> call(default: T, block: () -> T): T {
        if (!AlcedoNativeBridge.isLoaded) {
            Log.w(TAG, "Native bridge not loaded; returning default")
            return default
        }
        return try {
            block()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "UnsatisfiedLinkError: ${e.message}")
            default
        } catch (e: Throwable) {
            Log.e(TAG, "Native call failed", e)
            default
        }
    }

    /**
     * Run [block] that has no meaningful return, swallowing native failures
     * after logging. Use for fire-and-forget native calls.
     */
    fun run(block: () -> Unit) {
        if (!AlcedoNativeBridge.isLoaded) return
        try {
            block()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "UnsatisfiedLinkError: ${e.message}")
        } catch (e: Throwable) {
            Log.e(TAG, "Native call failed", e)
        }
    }

    /**
     * Run [block] and return its non-null result, or null on failure. Shortcut
     * for nullable native returns.
     */
    fun <T : Any> callOrNull(block: () -> T?): T? = call(default = null, block = block)

    /**
     * Run [block] returning a native handle (Long). Returns 0L on failure,
     * which the native layer treats as an invalid handle.
     */
    fun handle(block: () -> Long): Long = call(default = 0L, block = block)

    /**
     * Ensure the library is loaded before performing native work. Returns true
     * if the bridge is ready (or becomes ready after [AlcedoNativeBridge.init]).
     */
    fun ensureLoaded(): Boolean {
        if (AlcedoNativeBridge.isLoaded) return true
        return AlcedoNativeBridge.init()
    }

    /**
     * Release a native handle guarded against double-free and exceptions.
     * Safe to call with a 0 handle.
     */
    fun release(handle: Long, release: (Long) -> Unit) {
        if (handle == 0L) return
        run { release(handle) }
    }
}
