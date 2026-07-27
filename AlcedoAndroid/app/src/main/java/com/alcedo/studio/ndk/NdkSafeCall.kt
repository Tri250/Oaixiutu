package com.alcedo.studio.ndk

import android.util.Log
import com.alcedo.studio.crash.CrashReportService

object NdkSafeCall {
    private const val TAG = "NdkSafeCall"
    private const val MAX_RETRY = 2

    private inline fun <T> wrapWithBreadcrumb(name: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        try {
            CrashReportService.logBreadcrumb("ndk_call", name)
            val result = block()
            val elapsed = System.currentTimeMillis() - start
            if (elapsed > 500) {
                Log.w(TAG, "Slow NDK call: $name took ${elapsed}ms")
                CrashReportService.logEvent("slow_ndk:${name}:${elapsed}ms")
            }
            return result
        } finally {
        }
    }

    fun <T> execute(name: String, block: () -> T): T? {
        var lastError: Throwable? = null
        repeat(MAX_RETRY) { attempt ->
            try {
                return wrapWithBreadcrumb(name) { block() }
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native method not found: $name (attempt ${attempt + 1}/$MAX_RETRY)", e)
                lastError = e
                CrashReportService.logEvent("ndk_error:UnsatisfiedLinkError:$name")
                return null
            } catch (e: NoClassDefFoundError) {
                Log.e(TAG, "Native class not found: $name", e)
                lastError = e
                CrashReportService.logEvent("ndk_error:NoClassDefFoundError:$name")
                return null
            } catch (e: ExceptionInInitializerError) {
                Log.e(TAG, "Native init failed: $name", e)
                lastError = e
                CrashReportService.logEvent("ndk_error:ExceptionInInitializerError:$name")
                return null
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OOM in native call: $name (attempt ${attempt + 1}/$MAX_RETRY)", e)
                lastError = e
                CrashReportService.logEvent("ndk_error:OOM:$name")
                try {
                    Runtime.getRuntime().gc()
                    Thread.sleep(50)
                } catch (_: Throwable) {}
            } catch (e: StackOverflowError) {
                Log.e(TAG, "Stack overflow in native call: $name", e)
                lastError = e
                CrashReportService.logEvent("ndk_error:StackOverflow:$name")
                return null
            } catch (e: LinkageError) {
                Log.e(TAG, "Linkage error in native call: $name", e)
                lastError = e
                CrashReportService.logEvent("ndk_error:LinkageError:$name")
                return null
            } catch (e: VirtualMachineError) {
                Log.e(TAG, "VM error in native call: $name", e)
                lastError = e
                CrashReportService.logEvent("ndk_error:VMError:$name")
                return null
            } catch (e: Error) {
                Log.e(TAG, "Critical error in native call: $name (attempt ${attempt + 1}/$MAX_RETRY)", e)
                lastError = e
                CrashReportService.logEvent("ndk_error:Error:$name:${e.javaClass.simpleName}")
                if (attempt == MAX_RETRY - 1) return null
                try { Thread.sleep(30) } catch (_: Throwable) {}
            } catch (e: RuntimeException) {
                Log.e(TAG, "Runtime exception in native call: $name", e)
                lastError = e
                CrashReportService.logEvent("ndk_error:RuntimeException:$name:${e.javaClass.simpleName}")
                return null
            } catch (e: Throwable) {
                Log.e(TAG, "Unexpected throwable in native call: $name", e)
                lastError = e
                CrashReportService.logEvent("ndk_error:Throwable:$name:${e.javaClass.simpleName}")
                return null
            }
        }
        Log.wtf(TAG, "NDK call exhausted retries: $name", lastError)
        return null
    }

    fun executeBoolean(name: String, block: () -> Boolean): Boolean {
        return execute(name, block) ?: false
    }

    fun executeFloat(name: String, block: () -> Float): Float {
        return execute(name, block) ?: 0.0f
    }

    fun executeFloatArray(name: String, block: () -> FloatArray?): FloatArray? {
        var lastError: Throwable? = null
        repeat(MAX_RETRY) { attempt ->
            try {
                return wrapWithBreadcrumb(name) { block() }
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native method not found: $name", e)
                lastError = e
                return null
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OOM in native call: $name (attempt ${attempt + 1}/$MAX_RETRY)", e)
                lastError = e
                try {
                    Runtime.getRuntime().gc()
                    Thread.sleep(50)
                } catch (_: Throwable) {}
            } catch (e: StackOverflowError) {
                Log.e(TAG, "Stack overflow in native call: $name", e)
                lastError = e
                return null
            } catch (e: LinkageError) {
                Log.e(TAG, "Linkage error in native call: $name", e)
                lastError = e
                return null
            } catch (e: Error) {
                Log.e(TAG, "Critical error in native float array call: $name", e)
                lastError = e
                if (attempt == MAX_RETRY - 1) return null
                try { Thread.sleep(30) } catch (_: Throwable) {}
            } catch (e: Throwable) {
                Log.e(TAG, "Unexpected in native float array call: $name", e)
                lastError = e
                return null
            }
        }
        Log.wtf(TAG, "Native float array call exhausted retries: $name", lastError)
        return null
    }

    fun executeIntArray(name: String, block: () -> IntArray?): IntArray? {
        return try {
            wrapWithBreadcrumb(name) { block() }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native method not found: $name", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM in native call: $name", e)
            System.gc(); null
        } catch (e: Error) {
            Log.e(TAG, "Critical error in native int array call: $name", e)
            null
        } catch (e: Throwable) {
            Log.e(TAG, "Unexpected in native int array call: $name", e)
            null
        }
    }

    fun executeByteArray(name: String, block: () -> ByteArray?): ByteArray? {
        return try {
            wrapWithBreadcrumb(name) { block() }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native method not found: $name", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM in native byte array call: $name", e)
            System.gc(); null
        } catch (e: Error) {
            Log.e(TAG, "Critical error in native byte array call: $name", e)
            null
        } catch (e: Throwable) {
            Log.e(TAG, "Unexpected in native byte array call: $name", e)
            null
        }
    }
}
