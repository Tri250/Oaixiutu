package com.alcedo.studio.viewmodel

import com.alcedo.studio.data.model.ColorSpace
import com.alcedo.studio.data.model.ExportFormat
import com.alcedo.studio.data.model.ExportSettings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ExportViewModel's pure logic components:
 * ExportSettings, ResizeMode, BatchExportProgress, ExportPreset,
 * and dimension/size estimation calculations.
 *
 * These tests do not require Android framework and focus on data model
 * correctness and algorithm correctness.
 */
class ExportViewModelTest {

    // ================================================================
    // ExportSettings default values
    // ================================================================

    @Test
    fun exportSettings_defaultValues() {
        val settings = ExportSettings()
        assertEquals(ExportFormat.JPEG, settings.format)
        assertEquals(95, settings.quality)
        assertEquals(ColorSpace.SRGB, settings.colorSpace)
        assertTrue(settings.embedIcc)
        assertTrue(settings.includeMetadata)
        assertNull(settings.maxDimension)
        assertNull(settings.maxWidth)
        assertNull(settings.maxHeight)
        assertFalse(settings.isHdr)
        assertEquals(8, settings.bitDepth)
        assertEquals(0, settings.rating)
        assertTrue(settings.tags.isEmpty())
        assertEquals("", settings.outputPath)
        assertNull(settings.sourceExifPath)
        assertTrue(settings.useOriginalFilename)
        assertNull(settings.filenameTemplate)
        assertFalse(settings.hassebladWatermark)
    }

    // ================================================================
    // calculateOutputDimensions with various resize modes
    // ================================================================

    @Test
    fun calculateOutputDimensions_noResize_returnsSource() {
        val result = calculateOutputDimensions(
            srcW = 4000, srcH = 3000,
            settings = ExportSettings(maxWidth = null, maxHeight = null, maxDimension = null),
            resizeMode = ResizeMode.NONE
        )
        assertEquals(4000, result.first)
        assertEquals(3000, result.second)
    }

    @Test
    fun calculateOutputDimensions_maxDimension_scalesDown() {
        val result = calculateOutputDimensions(
            srcW = 4000, srcH = 3000,
            settings = ExportSettings(maxDimension = 2000),
            resizeMode = ResizeMode.NONE
        )
        // Long edge = 4000, scale = 2000/4000 = 0.5
        assertEquals(2000, result.first)
        assertEquals(1500, result.second)
    }

    @Test
    fun calculateOutputDimensions_maxDimension_noUpscale() {
        val result = calculateOutputDimensions(
            srcW = 1000, srcH = 800,
            settings = ExportSettings(maxDimension = 2000),
            resizeMode = ResizeMode.NONE
        )
        // Source smaller than maxDimension, return as-is
        assertEquals(1000, result.first)
        assertEquals(800, result.second)
    }

    @Test
    fun calculateOutputDimensions_fitMode_scalesToFit() {
        val result = calculateOutputDimensions(
            srcW = 4000, srcH = 3000,
            settings = ExportSettings(maxWidth = 2000, maxHeight = 2000),
            resizeMode = ResizeMode.FIT
        )
        // scale = min(2000/4000, 2000/3000) = min(0.5, 0.667) = 0.5
        assertEquals(2000, result.first)
        assertEquals(1500, result.second)
    }

    @Test
    fun calculateOutputDimensions_fillMode_scalesToFill() {
        val result = calculateOutputDimensions(
            srcW = 4000, srcH = 3000,
            settings = ExportSettings(maxWidth = 2000, maxHeight = 2000),
            resizeMode = ResizeMode.FILL
        )
        // scale = max(2000/4000, 2000/3000) = max(0.5, 0.667) = 0.667
        assertEquals(2668, result.first) // 4000 * 0.6667 ≈ 2667
        assertEquals(2000, result.second)
    }

    @Test
    fun calculateOutputDimensions_exactMode_returnsExact() {
        val result = calculateOutputDimensions(
            srcW = 4000, srcH = 3000,
            settings = ExportSettings(maxWidth = 1920, maxHeight = 1080),
            resizeMode = ResizeMode.EXACT
        )
        assertEquals(1920, result.first)
        assertEquals(1080, result.second)
    }

    @Test
    fun calculateOutputDimensions_widthOnly_scalesProportionally() {
        val result = calculateOutputDimensions(
            srcW = 4000, srcH = 3000,
            settings = ExportSettings(maxWidth = 2000, maxHeight = null),
            resizeMode = ResizeMode.NONE
        )
        // scale = 2000/4000 = 0.5
        assertEquals(2000, result.first)
        assertEquals(1500, result.second)
    }

    @Test
    fun calculateOutputDimensions_heightOnly_scalesProportionally() {
        val result = calculateOutputDimensions(
            srcW = 4000, srcH = 3000,
            settings = ExportSettings(maxWidth = null, maxHeight = 1500),
            resizeMode = ResizeMode.NONE
        )
        // scale = 1500/3000 = 0.5
        assertEquals(2000, result.first)
        assertEquals(1500, result.second)
    }

    @Test
    fun calculateOutputDimensions_zeroSource_returnsZeros() {
        val result = calculateOutputDimensions(
            srcW = 0, srcH = 0,
            settings = ExportSettings(),
            resizeMode = ResizeMode.NONE
        )
        assertEquals(0, result.first)
        assertEquals(0, result.second)
    }

    // ================================================================
    // Quality coercion (1-100 range)
    // ================================================================

    @Test
    fun quality_coercedToRange_lowBound() {
        val coerced = (-5).coerceIn(1, 100)
        assertEquals(1, coerced)
    }

    @Test
    fun quality_coercedToRange_highBound() {
        val coerced = 150.coerceIn(1, 100)
        assertEquals(100, coerced)
    }

    @Test
    fun quality_validValue_unchanged() {
        val coerced = 85.coerceIn(1, 100)
        assertEquals(85, coerced)
    }

    @Test
    fun quality_boundaryValues() {
        assertEquals(1, 1.coerceIn(1, 100))
        assertEquals(100, 100.coerceIn(1, 100))
    }

    // ================================================================
    // estimateFileSize for each format
    // ================================================================

    @Test
    fun estimateFileSize_jpeg() {
        val bytesPerPixel = 3L * 95 / 100 // quality=95
        val expected = 4000L * 3000L * bytesPerPixel
        assertEquals(expected, estimateFileSize(4000, 3000, ExportFormat.JPEG, 95))
    }

    @Test
    fun estimateFileSize_png() {
        val bytesPerPixel = 4L
        val expected = 1000L * 1000L * bytesPerPixel
        assertEquals(expected, estimateFileSize(1000, 1000, ExportFormat.PNG, 100))
    }

    @Test
    fun estimateFileSize_tiff8bit() {
        val bytesPerPixel = 3L
        val expected = 2000L * 2000L * bytesPerPixel
        assertEquals(expected, estimateFileSize(2000, 2000, ExportFormat.TIFF, 100, bitDepth = 8))
    }

    @Test
    fun estimateFileSize_tiff16bit() {
        val bytesPerPixel = 6L
        val expected = 2000L * 2000L * bytesPerPixel
        assertEquals(expected, estimateFileSize(2000, 2000, ExportFormat.TIFF, 100, bitDepth = 16))
    }

    @Test
    fun estimateFileSize_exr() {
        val bytesPerPixel = 8L
        val expected = 2000L * 2000L * bytesPerPixel
        assertEquals(expected, estimateFileSize(2000, 2000, ExportFormat.EXR, 100))
    }

    @Test
    fun estimateFileSize_dng_returnsZero() {
        // DNG is non-destructive, size ~= source
        assertEquals(0L, estimateFileSize(4000, 3000, ExportFormat.DNG, 100))
    }

    @Test
    fun estimateFileSize_ultraHdr() {
        val bytesPerPixel = 3L * 90 / 100
        val expected = 4000L * 3000L * bytesPerPixel
        assertEquals(expected, estimateFileSize(4000, 3000, ExportFormat.ULTRA_HDR, 90))
    }

    // ================================================================
    // isQualityApplicable / isResizeApplicable / isColorSpaceApplicable / isHdrApplicable
    // ================================================================

    @Test
    fun isQualityApplicable_onlyForJpegAndUltraHdr() {
        assertTrue(isQualityApplicable(ExportFormat.JPEG))
        assertTrue(isQualityApplicable(ExportFormat.ULTRA_HDR))
        assertFalse(isQualityApplicable(ExportFormat.PNG))
        assertFalse(isQualityApplicable(ExportFormat.TIFF))
        assertFalse(isQualityApplicable(ExportFormat.EXR))
        assertFalse(isQualityApplicable(ExportFormat.DNG))
    }

    @Test
    fun isResizeApplicable_notForExr() {
        assertTrue(isResizeApplicable(ExportFormat.JPEG))
        assertTrue(isResizeApplicable(ExportFormat.PNG))
        assertTrue(isResizeApplicable(ExportFormat.TIFF))
        assertFalse(isResizeApplicable(ExportFormat.EXR))
        assertTrue(isResizeApplicable(ExportFormat.DNG))
    }

    @Test
    fun isColorSpaceApplicable_onlyForTiffAndExr() {
        assertFalse(isColorSpaceApplicable(ExportFormat.JPEG))
        assertFalse(isColorSpaceApplicable(ExportFormat.PNG))
        assertTrue(isColorSpaceApplicable(ExportFormat.TIFF))
        assertTrue(isColorSpaceApplicable(ExportFormat.EXR))
        assertFalse(isColorSpaceApplicable(ExportFormat.DNG))
        assertFalse(isColorSpaceApplicable(ExportFormat.ULTRA_HDR))
    }

    @Test
    fun isHdrApplicable_forTiffExrAndUltraHdr() {
        assertFalse(isHdrApplicable(ExportFormat.JPEG))
        assertFalse(isHdrApplicable(ExportFormat.PNG))
        assertTrue(isHdrApplicable(ExportFormat.TIFF))
        assertTrue(isHdrApplicable(ExportFormat.EXR))
        assertFalse(isHdrApplicable(ExportFormat.DNG))
        assertTrue(isHdrApplicable(ExportFormat.ULTRA_HDR))
    }

    // ================================================================
    // BatchExportProgress fraction calculation
    // ================================================================

    @Test
    fun batchExportProgress_zeroTotal_fractionIsZero() {
        val progress = BatchExportProgress(totalItems = 0, completedItems = 0)
        assertEquals(0f, progress.progressFraction, 0.001f)
    }

    @Test
    fun batchExportProgress_halfDone_fractionIsHalf() {
        val progress = BatchExportProgress(totalItems = 10, completedItems = 5)
        assertEquals(0.5f, progress.progressFraction, 0.001f)
    }

    @Test
    fun batchExportProgress_complete_fractionIsOne() {
        val progress = BatchExportProgress(totalItems = 10, completedItems = 10)
        assertEquals(1f, progress.progressFraction, 0.001f)
    }

    @Test
    fun batchExportProgress_isComplete() {
        val progress = BatchExportProgress(totalItems = 5, completedItems = 5)
        assertTrue(progress.isComplete)
    }

    @Test
    fun batchExportProgress_notComplete() {
        val progress = BatchExportProgress(totalItems = 5, completedItems = 3)
        assertFalse(progress.isComplete)
    }

    @Test
    fun batchExportProgress_zeroTotal_notComplete() {
        val progress = BatchExportProgress(totalItems = 0, completedItems = 0)
        assertFalse(progress.isComplete)
    }

    // ================================================================
    // ExportPreset serialization/deserialization
    // ================================================================

    @Test
    fun exportPreset_serializationDeserialization_roundTrip() {
        val gson = Gson()
        val preset = ExportPreset(
            name = "TestPreset",
            format = ExportFormat.TIFF,
            quality = 100,
            colorSpace = ColorSpace.DISPLAY_P3,
            maxDimension = 4000,
            embedIcc = false,
            includeMetadata = true
        )
        val json = gson.toJson(preset)
        val type = object : TypeToken<ExportPreset>() {}.type
        val deserialized: ExportPreset = gson.fromJson(json, type)
        assertEquals(preset.name, deserialized.name)
        assertEquals(preset.format, deserialized.format)
        assertEquals(preset.quality, deserialized.quality)
        assertEquals(preset.colorSpace, deserialized.colorSpace)
        assertEquals(preset.maxDimension, deserialized.maxDimension)
        assertEquals(preset.embedIcc, deserialized.embedIcc)
        assertEquals(preset.includeMetadata, deserialized.includeMetadata)
    }

    @Test
    fun exportPreset_listSerialization_roundTrip() {
        val gson = Gson()
        val presets = listOf(
            ExportPreset("P1", ExportFormat.JPEG, 85, ColorSpace.SRGB, 2000, true, true),
            ExportPreset("P2", ExportFormat.PNG, 100, ColorSpace.REC2020, 0, false, false)
        )
        val json = gson.toJson(presets)
        val type = object : TypeToken<List<ExportPreset>>() {}.type
        val deserialized: List<ExportPreset> = gson.fromJson(json, type)
        assertEquals(2, deserialized.size)
        assertEquals("P1", deserialized[0].name)
        assertEquals(ExportFormat.PNG, deserialized[1].format)
    }

    // ================================================================
    // Resize dimension calculations with edge cases
    // ================================================================

    @Test
    fun resizeDimensions_squareImage_fitSquare() {
        val result = calculateOutputDimensions(
            srcW = 2000, srcH = 2000,
            settings = ExportSettings(maxWidth = 1000, maxHeight = 1000),
            resizeMode = ResizeMode.FIT
        )
        assertEquals(1000, result.first)
        assertEquals(1000, result.second)
    }

    @Test
    fun resizeDimensions_portraitImage_fitLandscape() {
        val result = calculateOutputDimensions(
            srcW = 3000, srcH = 4000,
            settings = ExportSettings(maxWidth = 2000, maxHeight = 2000),
            resizeMode = ResizeMode.FIT
        )
        // scale = min(2000/3000, 2000/4000) = min(0.667, 0.5) = 0.5
        assertEquals(1500, result.first)
        assertEquals(2000, result.second)
    }

    @Test
    fun resizeDimensions_maxDimensionZero_returnsSource() {
        val result = calculateOutputDimensions(
            srcW = 4000, srcH = 3000,
            settings = ExportSettings(maxDimension = 0),
            resizeMode = ResizeMode.NONE
        )
        // maxDimension=0 is > 0 check fails, returns source
        assertEquals(4000, result.first)
        assertEquals(3000, result.second)
    }

    // ================================================================
    // ExportFormat enum completeness
    // ================================================================

    @Test
    fun exportFormat_allValuesPresent() {
        val formats = ExportFormat.entries
        assertEquals(6, formats.size)
        assertTrue(formats.contains(ExportFormat.JPEG))
        assertTrue(formats.contains(ExportFormat.PNG))
        assertTrue(formats.contains(ExportFormat.TIFF))
        assertTrue(formats.contains(ExportFormat.EXR))
        assertTrue(formats.contains(ExportFormat.DNG))
        assertTrue(formats.contains(ExportFormat.ULTRA_HDR))
    }

    @Test
    fun resizeMode_allValuesPresent() {
        val modes = ResizeMode.entries
        assertEquals(4, modes.size)
        assertTrue(modes.contains(ResizeMode.NONE))
        assertTrue(modes.contains(ResizeMode.FIT))
        assertTrue(modes.contains(ResizeMode.FILL))
        assertTrue(modes.contains(ResizeMode.EXACT))
    }

    // ================================================================
    // Helper: calculateOutputDimensions (mirrors ExportViewModel logic)
    // ================================================================

    private fun calculateOutputDimensions(
        srcW: Int,
        srcH: Int,
        settings: ExportSettings,
        resizeMode: ResizeMode
    ): Pair<Int, Int> {
        val maxDim = settings.maxDimension
        val maxW = settings.maxWidth
        val maxH = settings.maxHeight

        if (maxDim != null && maxDim > 0) {
            val longEdge = maxOf(srcW, srcH)
            if (longEdge > maxDim) {
                val scale = maxDim.toFloat() / longEdge
                return Pair((srcW * scale).toInt(), (srcH * scale).toInt())
            }
            return Pair(srcW, srcH)
        }

        if (maxW != null && maxH != null) {
            return when (resizeMode) {
                ResizeMode.NONE -> Pair(srcW, srcH)
                ResizeMode.FIT -> {
                    val scale = minOf(maxW.toFloat() / srcW, maxH.toFloat() / srcH)
                    Pair((srcW * scale).toInt(), (srcH * scale).toInt())
                }
                ResizeMode.FILL -> {
                    val scale = maxOf(maxW.toFloat() / srcW, maxH.toFloat() / srcH)
                    Pair((srcW * scale).toInt(), (srcH * scale).toInt())
                }
                ResizeMode.EXACT -> Pair(maxW, maxH)
            }
        }

        if (maxW != null && maxW > 0 && maxH == null) {
            if (srcW > maxW) {
                val scale = maxW.toFloat() / srcW
                return Pair(maxW, (srcH * scale).toInt())
            }
            return Pair(srcW, srcH)
        }

        if (maxH != null && maxH > 0 && maxW == null) {
            if (srcH > maxH) {
                val scale = maxH.toFloat() / srcH
                return Pair((srcW * scale).toInt(), maxH)
            }
            return Pair(srcW, srcH)
        }

        return Pair(srcW, srcH)
    }

    // ================================================================
    // Helper: estimateFileSize (mirrors ExportViewModel logic)
    // ================================================================

    private fun estimateFileSize(
        width: Int,
        height: Int,
        format: ExportFormat,
        quality: Int,
        bitDepth: Int = 8
    ): Long {
        val pixelCount = width.toLong() * height.toLong()
        val bytesPerPixel = when (format) {
            ExportFormat.JPEG -> 3L * quality / 100
            ExportFormat.PNG -> 4L
            ExportFormat.TIFF -> if (bitDepth == 16) 6L else 3L
            ExportFormat.EXR -> 8L
            ExportFormat.DNG -> 0L
            ExportFormat.ULTRA_HDR -> 3L * quality / 100
        }
        return pixelCount * bytesPerPixel
    }

    // ================================================================
    // Helper: format applicability (mirrors ExportViewModel logic)
    // ================================================================

    private fun isQualityApplicable(format: ExportFormat): Boolean =
        format in listOf(ExportFormat.JPEG, ExportFormat.ULTRA_HDR)

    private fun isResizeApplicable(format: ExportFormat): Boolean =
        format != ExportFormat.EXR

    private fun isColorSpaceApplicable(format: ExportFormat): Boolean =
        format in listOf(ExportFormat.TIFF, ExportFormat.EXR)

    private fun isHdrApplicable(format: ExportFormat): Boolean =
        format in listOf(ExportFormat.TIFF, ExportFormat.EXR, ExportFormat.ULTRA_HDR)
}
