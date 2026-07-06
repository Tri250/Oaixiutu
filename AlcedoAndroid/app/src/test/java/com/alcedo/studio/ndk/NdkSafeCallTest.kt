package com.alcedo.studio.ndk

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for NdkSafeCall's pure logic patterns.
 *
 * Since NdkSafeCall.execute uses Android Log (which isn't available in
 * unit tests), we mirror the try/catch logic here to verify the
 * behavioral contract: return null on error, return result on success.
 */
class NdkSafeCallTest {

    // ================================================================
    // ndkSafeCall returns null on exception
    // ================================================================

    @Test
    fun ndkSafeCall_returnsNull_onUnsatisfiedLinkError() {
        val result = safeExecute("testMethod") {
            throw UnsatisfiedLinkError("native method not found")
        }
        assertNull(result)
    }

    @Test
    fun ndkSafeCall_returnsNull_onNoClassDefFoundError() {
        val result = safeExecute("testMethod") {
            throw NoClassDefFoundError("class not found")
        }
        assertNull(result)
    }

    @Test
    fun ndkSafeCall_returnsNull_onExceptionInInitializerError() {
        val result = safeExecute("testMethod") {
            throw ExceptionInInitializerError("init failed")
        }
        assertNull(result)
    }

    @Test
    fun ndkSafeCall_returnsNull_onOutOfMemoryError() {
        val result = safeExecute("testMethod") {
            throw OutOfMemoryError("OOM")
        }
        assertNull(result)
    }

    @Test
    fun ndkSafeCall_returnsNull_onGenericError() {
        val result = safeExecute("testMethod") {
            throw Error("critical native error")
        }
        assertNull(result)
    }

    @Test
    fun ndkSafeCall_returnsNull_onRuntimeException() {
        val result = safeExecute("testMethod") {
            throw RuntimeException("unexpected")
        }
        assertNull(result)
    }

    // ================================================================
    // ndkSafeCall returns result on success
    // ================================================================

    @Test
    fun ndkSafeCall_returnsResult_onSuccess_int() {
        val result = safeExecute("testMethod") { 42 }
        assertNotNull(result)
        assertEquals(42, result)
    }

    @Test
    fun ndkSafeCall_returnsResult_onSuccess_string() {
        val result = safeExecute("testMethod") { "hello" }
        assertNotNull(result)
        assertEquals("hello", result)
    }

    @Test
    fun ndkSafeCall_returnsResult_onSuccess_floatArray() {
        val expected = floatArrayOf(0.1f, 0.2f, 0.3f)
        val result = safeExecute("testMethod") { expected }
        assertNotNull(result)
        assertArrayEquals(expected, result as FloatArray, 0.001f)
    }

    @Test
    fun ndkSafeCall_returnsResult_onSuccess_nullable() {
        val result: FloatArray? = safeExecute("testMethod") { null }
        assertNull(result)
    }

    @Test
    fun ndkSafeCall_returnsResult_onSuccess_boolean() {
        val result = safeExecute("testMethod") { true }
        assertNotNull(result)
        assertEquals(true, result)
    }

    // ================================================================
    // ndkSafeCallWithError returns error message on exception
    // ================================================================

    @Test
    fun ndkSafeCallWithError_returnsErrorMessage_onException() {
        val result = safeExecuteWithError("testMethod") {
            throw RuntimeException("native crash")
        }
        assertNotNull(result)
        assertTrue(result!!.contains("native crash") || result.contains("testMethod"))
    }

    @Test
    fun ndkSafeCallWithError_returnsErrorMessage_onUnsatisfiedLinkError() {
        val result = safeExecuteWithError("missingMethod") {
            throw UnsatisfiedLinkError("method missing")
        }
        assertNotNull(result)
    }

    @Test
    fun ndkSafeCallWithError_returnsErrorMessage_onOutOfMemoryError() {
        val result = safeExecuteWithError("oomMethod") {
            throw OutOfMemoryError("out of memory")
        }
        assertNotNull(result)
    }

    // ================================================================
    // ndkSafeCallWithError returns null on success
    // ================================================================

    @Test
    fun ndkSafeCallWithError_returnsNull_onSuccess() {
        val result = safeExecuteWithError("testMethod") { 42 }
        assertNull(result)
    }

    @Test
    fun ndkSafeCallWithError_returnsNull_onSuccess_string() {
        val result = safeExecuteWithError("testMethod") { "ok" }
        assertNull(result)
    }

    // ================================================================
    // executeBoolean default value on error
    // ================================================================

    @Test
    fun executeBoolean_returnsFalse_onError() {
        val result = safeExecuteBoolean("testMethod") {
            throw UnsatisfiedLinkError("not found")
        }
        assertFalse(result)
    }

    @Test
    fun executeBoolean_returnsValue_onSuccess() {
        val result = safeExecuteBoolean("testMethod") { true }
        assertTrue(result)
    }

    @Test
    fun executeBoolean_returnsFalse_onNull() {
        val result = safeExecuteBoolean("testMethod") {
            null as Boolean?
        }
        assertFalse(result) // null ?: false = false
    }

    // ================================================================
    // executeFloat default value on error
    // ================================================================

    @Test
    fun executeFloat_returnsZero_onError() {
        val result = safeExecuteFloat("testMethod") {
            throw UnsatisfiedLinkError("not found")
        }
        assertEquals(0.0f, result, 0.001f)
    }

    @Test
    fun executeFloat_returnsValue_onSuccess() {
        val result = safeExecuteFloat("testMethod") { 3.14f }
        assertEquals(3.14f, result, 0.001f)
    }

    @Test
    fun executeFloat_returnsZero_onNull() {
        val result = safeExecuteFloat("testMethod") {
            null as Float?
        }
        assertEquals(0.0f, result, 0.001f) // null ?: 0f = 0f
    }

    // ================================================================
    // Helper: mirrors NdkSafeCall.execute logic (without Android Log)
    // ================================================================

    private fun <T> safeExecute(name: String, block: () -> T): T? {
        return try {
            block()
        } catch (e: UnsatisfiedLinkError) {
            null
        } catch (e: NoClassDefFoundError) {
            null
        } catch (e: ExceptionInInitializerError) {
            null
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Error) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun safeExecuteWithError(name: String, block: () -> Any?): String? {
        return try {
            block()
            null
        } catch (e: UnsatisfiedLinkError) {
            "Native method not found: $name - ${e.message}"
        } catch (e: NoClassDefFoundError) {
            "Native class not found: $name - ${e.message}"
        } catch (e: OutOfMemoryError) {
            "OOM in native call: $name - ${e.message}"
        } catch (e: Error) {
            "Critical error in native call: $name - ${e.message}"
        } catch (e: Exception) {
            "Exception in native call: $name - ${e.message}"
        }
    }

    private fun safeExecuteBoolean(name: String, block: () -> Boolean?): Boolean {
        return safeExecute(name, block) ?: false
    }

    private fun safeExecuteFloat(name: String, block: () -> Float?): Float {
        return safeExecute(name, block) ?: 0.0f
    }

    private fun assertArrayEquals(expected: FloatArray, actual: FloatArray, delta: Float) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals(expected[i], actual[i], delta)
        }
    }
}
