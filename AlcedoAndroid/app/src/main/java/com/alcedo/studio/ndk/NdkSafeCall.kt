package com.alcedo.studio.ndk

import android.util.Log

/**
 * Result of a guarded native call. [error] carries a human-readable message
 * (non-null when [success] is false) so callers can surface the failure to the
 * UI instead of silently degrading.
 */
data class NdkResult<out T>(
    val success: Boolean,
    val value: T?,
    val error: String?,
) {
    /** The value on success, or null on failure. */
    fun getOrNull(): T? = if (success) value else null
}

/**
 * Safe wrapper around native JNI calls. Catches [UnsatisfiedLinkError] (library
 * not loaded / symbol missing) and generic [Throwable] (native exceptions
 * re-thrown as Java exceptions), logs them, records the most recent failure in
 * [lastError], and returns a caller-supplied default. This keeps the UI
 * responsive when the native layer is unavailable (e.g. running on an emulator
 * without Vulkan) instead of crashing, while still letting callers obtain the
 * failure reason via [lastError] or the structured [result] helper.
 */
object NdkSafeCall {

    private const val TAG = "NdkSafeCall"

    /** True when the native bridge has loaded successfully. */
    val isAvailable: Boolean get() = AlcedoNativeBridge.isLoaded

    /** The most recent native failure message, or null if the last call succeeded. */
    @Volatile
    var lastError: String? = null
        private set

    /**
     * Run [block] and return its result, or [default] on any failure. Native
     * methods that return primitive/object values should use this. On failure
     * the cause is recorded in [lastError] so the UI can surface it.
     */
    fun <T> call(default: T, block: () -> T): T {
        if (!AlcedoNativeBridge.isLoaded) {
            lastError = "Native bridge not loaded"
            Log.w(TAG, "Native bridge not loaded; returning default")
            return default
        }
        return try {
            val r = block()
            lastError = null
            r
        } catch (e: UnsatisfiedLinkError) {
            record(e, "UnsatisfiedLinkError: ${e.message}")
            default
        } catch (e: Throwable) {
            record(e, "Native call failed: ${e.javaClass.simpleName}: ${e.message}")
            default
        }
    }

    /**
     * Run [block] and return an [NdkResult] carrying either the value or a
     * meaningful error string. Prefer this over [call] when the caller needs to
     * react to the failure reason rather than silently fall back.
     */
    fun <T> result(block: () -> T): NdkResult<T> {
        if (!AlcedoNativeBridge.isLoaded) {
            lastError = "Native bridge not loaded"
            return NdkResult(success = false, value = null, error = lastError)
        }
        return try {
            val r = block()
            lastError = null
            NdkResult(success = true, value = r, error = null)
        } catch (e: UnsatisfiedLinkError) {
            val msg = "UnsatisfiedLinkError: ${e.message}"
            record(e, msg)
            NdkResult(success = false, value = null, error = msg)
        } catch (e: Throwable) {
            val msg = "Native call failed: ${e.javaClass.simpleName}: ${e.message}"
            record(e, msg)
            NdkResult(success = false, value = null, error = msg)
        }
    }

    /**
     * Run [block] that has no meaningful return, swallowing native failures
     * after logging. Use for fire-and-forget native calls.
     */
    fun run(block: () -> Unit) {
        if (!AlcedoNativeBridge.isLoaded) {
            lastError = "Native bridge not loaded"
            return
        }
        try {
            block()
            lastError = null
        } catch (e: UnsatisfiedLinkError) {
            record(e, "UnsatisfiedLinkError: ${e.message}")
        } catch (e: Throwable) {
            record(e, "Native call failed: ${e.javaClass.simpleName}: ${e.message}")
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

    private fun record(e: Throwable, message: String) {
        lastError = message
        Log.e(TAG, message, e)
    }
}
