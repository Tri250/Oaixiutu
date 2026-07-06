package com.alcedo.studio.utils

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryGuardInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun availableHeapBytesIsPositive() {
        val available = MemoryGuard.availableHeapBytes()
        assertTrue("Available heap should be positive, got $available", available > 0)
    }

    @Test
    fun canAllocateSmallBitmap() {
        val smallBytes = 1024L * 1024L // 1MB
        assertTrue("Should be able to allocate 1MB bitmap", MemoryGuard.canAllocateBitmap(smallBytes))
    }

    @Test
    fun cannotAllocateEnormousBitmap() {
        val enormousBytes = 1024L * 1024L * 1024L * 4L // 4GB
        assertFalse("Should not be able to allocate 4GB bitmap", MemoryGuard.canAllocateBitmap(enormousBytes))
    }

    @Test
    fun estimateBitmapBytesArgb8888() {
        val bytes = MemoryGuard.estimateBitmapBytes(1000, 1000)
        assertEquals("ARGB_8888 1000x1000 = 4MB", 4_000_000L, bytes)
    }

    @Test
    fun estimateBitmapBytesRgb565() {
        val bytes = MemoryGuard.estimateBitmapBytes(1000, 1000, android.graphics.Bitmap.Config.RGB_565)
        assertEquals("RGB_565 1000x1000 = 2MB", 2_000_000L, bytes)
    }

    @Test
    fun calculateSafeSampleSizeReturnsAtLeast1() {
        val sampleSize = MemoryGuard.calculateSafeSampleSize(100, 100, 4_000_000L)
        assertTrue("Sample size should be >= 1", sampleSize >= 1)
    }

    @Test
    fun calculateSafeSampleSizeIncreasesForLargeImages() {
        // 8000x6000 image, max 8MB
        val sampleSize = MemoryGuard.calculateSafeSampleSize(8000, 6000, 8_000_000L)
        assertTrue("Large image should require sampling > 1", sampleSize > 1)
    }

    @Test
    fun suggestedMaxPixelsIsWithinBounds() {
        val maxPixels = MemoryGuard.suggestedMaxPixels(context)
        assertTrue("Max pixels should be >= 1M", maxPixels >= 1024 * 1024)
        assertTrue("Max pixels should be <= 16M", maxPixels <= 16 * 1024 * 1024)
    }

    @Test
    fun getMemoryPressureLevelReturnsValidLevel() {
        val level = MemoryGuard.getMemoryPressureLevel(context)
        // Should be one of the valid IMPORTANCE_* constants
        assertTrue("Pressure level should be valid", level in intArrayOf(
            android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
            android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND,
            android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
        ))
    }

    @Test
    fun emergencyGCDoesNotCrash() {
        // Should not throw
        MemoryGuard.emergencyGC()
    }
}
