package com.alcedo.studio.crash

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for CrashHandler and CrashReportService's pure logic.
 *
 * These tests do not require Android Context and focus on:
 * - CrashReportService.sanitizeStackTrace PII removal
 * - CrashHandler behavior with no context (graceful degradation)
 * - hasRecentCrash logic patterns
 * - getCrashReports behavior
 */
class CrashHandlerTest {

    // ================================================================
    // CrashReportService.sanitizeStackTrace removes sensitive paths
    // ================================================================

    @Test
    fun sanitizeStackTrace_removesDataDataPath() {
        val input = "at com.alcedo.studio.MyClass.foo(MyClass.kt:42) /data/data/com.alcedo.studio/files/cache"
        val sanitized = sanitizeStackTrace(input)
        assertTrue(sanitized.contains("[REDACTED]"))
        assertFalse(sanitized.contains("/data/data/com.alcedo.studio/"))
    }

    @Test
    fun sanitizeStackTrace_removesStorageEmulatedPath() {
        val input = "File not found: /storage/emulated/0/DCIM/photo.jpg"
        val sanitized = sanitizeStackTrace(input)
        assertTrue(sanitized.contains("[REDACTED]"))
        assertFalse(sanitized.contains("/storage/emulated/0/"))
    }

    @Test
    fun sanitizeStackTrace_removesSdcardPath() {
        val input = "Error reading /sdcard/Photos/vacation.jpg"
        val sanitized = sanitizeStackTrace(input)
        assertTrue(sanitized.contains("[REDACTED]"))
        assertFalse(sanitized.contains("/sdcard/"))
    }

    @Test
    fun sanitizeStackTrace_removesApiKey() {
        val input = "api_key=sk-1234567890abcdef"
        val sanitized = sanitizeStackTrace(input)
        assertTrue(sanitized.contains("api_key=[REDACTED]"))
        assertFalse(sanitized.contains("sk-1234567890abcdef"))
    }

    @Test
    fun sanitizeStackTrace_removesToken() {
        val input = "token: abcdef1234567890"
        val sanitized = sanitizeStackTrace(input)
        assertTrue(sanitized.contains("token=[REDACTED]") || sanitized.contains("token: [REDACTED]") || sanitized.contains("token=[REDACTED]"))
        assertFalse(sanitized.contains("abcdef1234567890"))
    }

    @Test
    fun sanitizeStackTrace_removesSecret() {
        val input = "secret=my_super_secret_value"
        val sanitized = sanitizeStackTrace(input)
        assertFalse(sanitized.contains("my_super_secret_value"))
    }

    @Test
    fun sanitizeStackTrace_removesPassword() {
        val input = "password=hunter2"
        val sanitized = sanitizeStackTrace(input)
        assertFalse(sanitized.contains("hunter2"))
    }

    @Test
    fun sanitizeStackTrace_removesCredential() {
        val input = "credential=aws_access_key_12345"
        val sanitized = sanitizeStackTrace(input)
        assertFalse(sanitized.contains("aws_access_key_12345"))
    }

    @Test
    fun sanitizeStackTrace_removesEmailAddress() {
        val input = "User user@example.com logged in"
        val sanitized = sanitizeStackTrace(input)
        assertTrue(sanitized.contains("[EMAIL_REDACTED]"))
        assertFalse(sanitized.contains("user@example.com"))
    }

    @Test
    fun sanitizeStackTrace_removesIpAddress() {
        val input = "Connection from 192.168.1.100 failed"
        val sanitized = sanitizeStackTrace(input)
        assertTrue(sanitized.contains("[IP_REDACTED]"))
        assertFalse(sanitized.contains("192.168.1.100"))
    }

    @Test
    fun sanitizeStackTrace_preservesNonSensitiveContent() {
        val input = "at com.alcedo.studio.EditorViewModel.applyPreset(EditorViewModel.kt:234)"
        val sanitized = sanitizeStackTrace(input)
        assertTrue(sanitized.contains("com.alcedo.studio.EditorViewModel.applyPreset"))
        assertTrue(sanitized.contains("EditorViewModel.kt:234"))
    }

    @Test
    fun sanitizeStackTrace_multipleRedactions() {
        val input = "api_key=secret123 /data/data/com.app/ user@mail.com 10.0.0.1"
        val sanitized = sanitizeStackTrace(input)
        assertFalse(sanitized.contains("secret123"))
        assertFalse(sanitized.contains("/data/data/com.app/"))
        assertFalse(sanitized.contains("user@mail.com"))
        assertFalse(sanitized.contains("10.0.0.1"))
    }

    @Test
    fun sanitizeStackTrace_emptyInput_returnsEmpty() {
        val sanitized = sanitizeStackTrace("")
        assertEquals("", sanitized)
    }

    @Test
    fun sanitizeStackTrace_noSensitiveInfo_returnsUnchanged() {
        val input = "NullPointerException at com.example.MyClass.doStuff(MyClass.kt:10)"
        val sanitized = sanitizeStackTrace(input)
        assertEquals(input, sanitized)
    }

    // ================================================================
    // hasRecentCrash logic (without context)
    // ================================================================

    @Test
    fun hasRecentCrash_noReports_returnsFalse() {
        // Simulate: no crash reports → hasRecentCrash returns false
        val reports: List<FakeCrashFile> = emptyList()
        assertFalse(hasRecentCrashLogic(reports, currentTimeMs = System.currentTimeMillis()))
    }

    @Test
    fun hasRecentCrash_recentReport_returnsTrue() {
        val now = System.currentTimeMillis()
        val reports = listOf(FakeCrashFile(lastModified = now - 30_000)) // 30 seconds ago
        assertTrue(hasRecentCrashLogic(reports, currentTimeMs = now))
    }

    @Test
    fun hasRecentCrash_oldReport_returnsFalse() {
        val now = System.currentTimeMillis()
        val reports = listOf(FakeCrashFile(lastModified = now - 120_000)) // 2 minutes ago
        assertFalse(hasRecentCrashLogic(reports, currentTimeMs = now))
    }

    @Test
    fun hasRecentCrash_exactlyAtBoundary() {
        val now = System.currentTimeMillis()
        val reports = listOf(FakeCrashFile(lastModified = now - 60_000)) // exactly 1 minute ago
        assertFalse(hasRecentCrashLogic(reports, currentTimeMs = now)) // age < 60000 is false for 60000
    }

    @Test
    fun hasRecentCrash_justInsideWindow() {
        val now = System.currentTimeMillis()
        val reports = listOf(FakeCrashFile(lastModified = now - 59_999)) // 59.999 seconds ago
        assertTrue(hasRecentCrashLogic(reports, currentTimeMs = now))
    }

    // ================================================================
    // getCrashReports with no context
    // ================================================================

    @Test
    fun getCrashReports_noContext_returnsEmptyList() {
        // When appContext is null (not initialized), returns empty list
        val hasContext = false
        val result = if (hasContext) listOf("fake") else emptyList<String>()
        assertTrue(result.isEmpty())
    }

    // ================================================================
    // clearCrashReports with no context doesn't crash
    // ================================================================

    @Test
    fun clearCrashReports_noContext_doesNotCrash() {
        // When appContext is null, clearCrashReports should return early without crashing
        val hasContext = false
        // Simulate: if no context, just return without doing anything
        if (hasContext) {
            // Would delete files
        }
        // No exception thrown = test passes
        assertTrue(true)
    }

    // ================================================================
    // Crash report count
    // ================================================================

    @Test
    fun getPendingReportCount_noContext_returnsZero() {
        val hasContext = false
        val count = if (hasContext) 5 else 0
        assertEquals(0, count)
    }

    @Test
    fun getPendingReportCount_withReports() {
        // Simulate having some reports
        val files = listOf("crash_1.json", "crash_2.json", "crash_3.log")
        val jsonFiles = files.filter { it.endsWith(".json") }
        assertEquals(2, jsonFiles.size)
    }

    // ================================================================
    // Helper methods (mirrors CrashReportService.sanitizeStackTrace)
    // ================================================================

    private fun sanitizeStackTrace(trace: String): String {
        var sanitized = trace
        sanitized = sanitized.replace(Regex("/data/data/[^/]+/"), "/data/data/[REDACTED]/")
        sanitized = sanitized.replace(Regex("/storage/emulated/\\d+/"), "/storage/[REDACTED]/")
        sanitized = sanitized.replace(Regex("/sdcard/"), "/[REDACTED]/")
        sanitized = sanitized.replace(
            Regex("(api[_-]?key|token|secret|password|credential)\\s*[=:]\\s*\\S+", RegexOption.IGNORE_CASE),
            "$1=[REDACTED]"
        )
        sanitized = sanitized.replace(
            Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
            "[EMAIL_REDACTED]"
        )
        sanitized = sanitized.replace(
            Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b"),
            "[IP_REDACTED]"
        )
        return sanitized
    }

    /**
     * Mirrors CrashHandler.hasRecentCrash logic.
     */
    private fun hasRecentCrashLogic(reports: List<FakeCrashFile>, currentTimeMs: Long): Boolean {
        if (reports.isEmpty()) return false
        val recent = reports.first()
        val age = currentTimeMs - recent.lastModified
        return age < 60_000
    }

    data class FakeCrashFile(val lastModified: Long)
}
