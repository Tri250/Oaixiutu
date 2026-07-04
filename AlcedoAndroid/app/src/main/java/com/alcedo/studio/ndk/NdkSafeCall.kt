package com.alcedo.studio.ndk

import android.util.Log
import com.alcedo.studio.crash.CrashHandler

object NdkSafeCall {
    private const val TAG = "NdkSafeCall"

    // Execute NDK call with full safety wrapping
    fun <T> execute(name: String, block: () -> T): T? {
        return try {
            block()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native method not found: $name", e)
            null
        } catch (e: NoClassDefFoundError) {
            Log.e(TAG, "Native class not found: $name", e)
            null
        } catch (e: ExceptionInInitializerError) {
            Log.e(TAG, "Native init failed: $name", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM in native call: $name", e)
            System.gc()
            null
        } catch (e: Error) {
            // Catch any other Error (including native crashes that manifest as Error subclasses)
            Log.e(TAG, "Critical error in native call: $name", e)
            null
        }
    }

    // Execute NDK call with boolean return type
    fun executeBoolean(name: String, block: () -> Boolean): Boolean {
        return execute(name, block) ?: false
    }

    // Execute NDK call with float return type
    fun executeFloat(name: String, block: () -> Float): Float {
        return execute(name, block) ?: 0.0f
    }

    // Execute NDK call that returns an array
    fun executeFloatArray(name: String, block: () -> FloatArray?): FloatArray? {
        return try {
            block()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native method not found: $name", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM in native call: $name", e)
            System.gc()
            null
        } catch (e: Error) {
            Log.e(TAG, "Critical error in native call: $name", e)
            null
        }
    }
}
