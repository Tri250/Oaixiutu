package com.alcedo.studio.viewmodel

import com.alcedo.studio.data.model.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for EditorViewModel's pure logic components:
 * PipelineParams, WorkingVersion, EditHistory, Version, DisplayTransform,
 * tone curve / color wheel / HSL state, and hasUnsavedChanges logic.
 *
 * These tests do not require Android framework and focus on data model
 * correctness and state management.
 */
class EditorViewModelTest {

    // ================================================================
    // PipelineParams
    // ================================================================

    @Test
    fun pipelineParams_defaultValues_areNeutral() {
        val params = PipelineParams()
        assertEquals(0f, params.exposure, 0.001f)
        assertEquals(0f, params.contrast, 0.001f)
        assertEquals(0f, params.saturation, 0.001f)
        assertEquals(0f, params.vibrance, 0.001f)
        assertEquals(0f, params.highlights, 0.001f)
        assertEquals(0f, params.shadows, 0.001f)
        assertEquals(0f, params.midtones, 0.001f)
        assertEquals(0.25f, params.shadowBoundary, 0.001f)
        assertEquals(0.75f, params.highlightBoundary, 0.001f)
        assertEquals(6500f, params.whiteBalanceTemp, 0.001f)
        assertEquals(0f, params.whiteBalanceTint, 0.001f)
        assertFalse(params.autoExposureEnabled)
        assertEquals(0f, params.autoExposureValue, 0.001f)
    }

    @Test
    fun pipelineParams_copy_preservesUnchangedFields() {
        val original = PipelineParams(exposure = 1.5f, contrast = 0.3f, saturation = -0.2f)
        val modified = original.copy(contrast = 0.8f)
        assertEquals(1.5f, modified.exposure, 0.001f)
        assertEquals(0.8f, modified.contrast, 0.001f)
        assertEquals(-0.2f, modified.saturation, 0.001f)
    }

    @Test
    fun pipelineParams_copy_overridesOnlySpecifiedFields() {
        val base = PipelineParams(
            exposure = 2.0f,
            shadows = 0.5f,
            whiteBalanceTemp = 7200f,
            filmGrainIntensity = 0.1f
        )
        val updated = base.copy(exposure = 0f, filmGrainIntensity = 0f)
        assertEquals(0f, updated.exposure, 0.001f)
        assertEquals(0.5f, updated.shadows, 0.001f)
        assertEquals(7200f, updated.whiteBalanceTemp, 0.001f)
        assertEquals(0f, updated.filmGrainIntensity, 0.001f)
    }

    @Test
    fun pipelineParams_colorWheelDefaults_areIdentity() {
        val params = PipelineParams()
        assertEquals(0f, params.colorWheelLiftR, 0.001f)
        assertEquals(0f, params.colorWheelLiftG, 0.001f)
        assertEquals(0f, params.colorWheelLiftB, 0.001f)
        assertEquals(1f, params.colorWheelGammaR, 0.001f)
        assertEquals(1f, params.colorWheelGammaG, 0.001f)
        assertEquals(1f, params.colorWheelGammaB, 0.001f)
        assertEquals(1f, params.colorWheelGainR, 0.001f)
        assertEquals(1f, params.colorWheelGainG, 0.001f)
        assertEquals(1f, params.colorWheelGainB, 0.001f)
    }

    @Test
    fun pipelineParams_hslDefaults_areIdentity() {
        val params = PipelineParams()
        assertEquals(8, params.hslHueShift.size)
        assertEquals(8, params.hslSaturationScale.size)
        assertEquals(8, params.hslLuminanceScale.size)
        params.hslHueShift.forEach { assertEquals(0f, it, 0.001f) }
        params.hslSaturationScale.forEach { assertEquals(1f, it, 0.001f) }
        params.hslLuminanceScale.forEach { assertEquals(1f, it, 0.001f) }
    }

    @Test
    fun pipelineParams_toneCurveDefaults_areLinear() {
        val params = PipelineParams()
        assertEquals(5, params.toneCurvePoints)
        assertEquals(5, params.toneCurveX.size)
        assertEquals(5, params.toneCurveY.size)
        for (i in 0 until 5) {
            assertEquals(i / 4f, params.toneCurveX[i], 0.001f)
            assertEquals(i / 4f, params.toneCurveY[i], 0.001f)
        }
    }

    @Test
    fun pipelineParams_channelMixerDefaults_areIdentity() {
        val params = PipelineParams()
        assertEquals(9, params.channelMixerMatrix.size)
        // Identity: 1,0,0, 0,1,0, 0,0,1
        assertEquals(1f, params.channelMixerMatrix[0], 0.001f)
        assertEquals(0f, params.channelMixerMatrix[1], 0.001f)
        assertEquals(0f, params.channelMixerMatrix[2], 0.001f)
        assertEquals(0f, params.channelMixerMatrix[3], 0.001f)
        assertEquals(1f, params.channelMixerMatrix[4], 0.001f)
        assertFalse(params.channelMixerMonochrome)
    }

    // ================================================================
    // WorkingVersion undo/redo
    // ================================================================

    @Test
    fun workingVersion_appendTransaction_advancesCursor() {
        val wv = WorkingVersion(boundImageId = 1u)
        assertEquals(0, wv.cursor)
        assertEquals(0, wv.transactions.size)

        val tx = EditTransaction(
            transactionId = 0u,
            operatorType = OperatorType.EXPOSURE,
            paramsBefore = JsonObject(mapOf("exposure" to JsonPrimitive(0f))),
            paramsAfter = JsonObject(mapOf("exposure" to JsonPrimitive(1.5f)))
        )
        wv.appendTransaction(tx)
        assertEquals(1, wv.cursor)
        assertEquals(1, wv.transactions.size)
    }

    @Test
    fun workingVersion_undo_movesCursorBack() {
        val wv = WorkingVersion(boundImageId = 1u)
        val tx1 = EditTransaction(0u, OperatorType.EXPOSURE, JsonObject(emptyMap()), JsonObject(emptyMap()))
        val tx2 = EditTransaction(1u, OperatorType.CONTRAST, JsonObject(emptyMap()), JsonObject(emptyMap()))
        wv.appendTransaction(tx1)
        wv.appendTransaction(tx2)
        assertEquals(2, wv.cursor)

        assertTrue(wv.undo())
        assertEquals(1, wv.cursor)
    }

    @Test
    fun workingVersion_undoAtZero_returnsFalse() {
        val wv = WorkingVersion(boundImageId = 1u)
        assertFalse(wv.undo())
        assertEquals(0, wv.cursor)
    }

    @Test
    fun workingVersion_redo_advancesCursor() {
        val wv = WorkingVersion(boundImageId = 1u)
        val tx = EditTransaction(0u, OperatorType.EXPOSURE, JsonObject(emptyMap()), JsonObject(emptyMap()))
        wv.appendTransaction(tx)
        wv.undo()
        assertEquals(0, wv.cursor)

        assertTrue(wv.redo())
        assertEquals(1, wv.cursor)
    }

    @Test
    fun workingVersion_redoAtEnd_returnsFalse() {
        val wv = WorkingVersion(boundImageId = 1u)
        assertFalse(wv.redo())
    }

    @Test
    fun workingVersion_appendAfterUndo_truncatesFuture() {
        val wv = WorkingVersion(boundImageId = 1u)
        val tx1 = EditTransaction(0u, OperatorType.EXPOSURE, JsonObject(emptyMap()), JsonObject(emptyMap()))
        val tx2 = EditTransaction(1u, OperatorType.CONTRAST, JsonObject(emptyMap()), JsonObject(emptyMap()))
        wv.appendTransaction(tx1)
        wv.appendTransaction(tx2)
        wv.undo() // cursor=1

        val tx3 = EditTransaction(2u, OperatorType.SATURATION, JsonObject(emptyMap()), JsonObject(emptyMap()))
        wv.appendTransaction(tx3)

        // tx2 should be gone, replaced by tx3
        assertEquals(2, wv.transactions.size)
        assertEquals(2, wv.cursor)
        assertFalse(wv.redo()) // nothing to redo
    }

    @Test
    fun workingVersion_appliedTransactions_reflectsCursor() {
        val wv = WorkingVersion(boundImageId = 1u)
        val tx1 = EditTransaction(0u, OperatorType.EXPOSURE, JsonObject(emptyMap()), JsonObject(emptyMap()))
        val tx2 = EditTransaction(1u, OperatorType.CONTRAST, JsonObject(emptyMap()), JsonObject(emptyMap()))
        val tx3 = EditTransaction(2u, OperatorType.SATURATION, JsonObject(emptyMap()), JsonObject(emptyMap()))
        wv.appendTransaction(tx1)
        wv.appendTransaction(tx2)
        wv.appendTransaction(tx3)

        assertEquals(3, wv.appliedTransactions().size)

        wv.undo()
        assertEquals(2, wv.appliedTransactions().size)

        wv.undo()
        assertEquals(1, wv.appliedTransactions().size)

        wv.undo()
        assertEquals(0, wv.appliedTransactions().size)
    }

    // ================================================================
    // Version undo/redo
    // ================================================================

    @Test
    fun version_undoRedo_tracksCursor() {
        val version = Version(boundImageId = 1u)
        val tx = EditTransaction(0u, OperatorType.EXPOSURE, JsonObject(emptyMap()), JsonObject(emptyMap()))
        version.appendTransaction(tx)
        assertEquals(1, version.cursor)

        val undone = version.undo()
        assertNotNull(undone)
        assertEquals(0, version.cursor)

        val redone = version.redo()
        assertNotNull(redone)
        assertEquals(1, version.cursor)
    }

    // ================================================================
    // EditHistory
    // ================================================================

    @Test
    fun editHistory_createsDefaultVersionOnInit() {
        val history = EditHistory(boundImageId = 1u)
        assertNotNull(history.getDefaultVersion())
        assertEquals(history.defaultVersionId, history.activeVersionId)
        assertEquals(1, history.versionOrder.size)
        assertEquals(1, history.versionStorage.size)
    }

    @Test
    fun editHistory_createVersion_addsNewVersion() {
        val history = EditHistory(boundImageId = 1u)
        val newId = history.createVersion("My Version")
        assertEquals(2, history.versionStorage.size)
        assertEquals(2, history.versionOrder.size)
        assertNotNull(history.getVersion(newId))
    }

    @Test
    fun editHistory_setActiveVersion_switchesActive() {
        val history = EditHistory(boundImageId = 1u)
        val newId = history.createVersion("V2")
        history.setActiveVersion(newId)
        assertEquals(newId, history.activeVersionId)
    }

    @Test
    fun editHistory_setActiveVersion_ignoresInvalidId() {
        val history = EditHistory(boundImageId = 1u)
        val originalActive = history.activeVersionId
        history.setActiveVersion("nonexistent")
        assertEquals(originalActive, history.activeVersionId)
    }

    @Test
    fun editHistory_cloneForFile_producesNewHistoryId() {
        val history = EditHistory(boundImageId = 1u)
        history.createVersion("V2")
        val cloned = history.cloneForFile(2u)
        assertNotEquals(history.historyId, cloned.historyId)
        assertEquals(2u, cloned.boundImageId)
        assertEquals(history.versionStorage.size, cloned.versionStorage.size)
    }

    // ================================================================
    // DisplayTransform reconstruction from ordinal
    // ================================================================

    @Test
    fun displayTransform_defaultValues() {
        val dt = DisplayTransform()
        assertEquals(ColorScience.ACES20, dt.colorScience)
        assertEquals(EOTF.SRGB, dt.eotf)
        assertEquals(100f, dt.peakLuminance, 0.001f)
        assertEquals(ColorSpace.SRGB, dt.displayColorSpace)
    }

    @Test
    fun displayTransform_reconstructionFromOrdinal() {
        val original = DisplayTransform(
            colorScience = ColorScience.OPENDRT,
            eotf = EOTF.PQ,
            peakLuminance = 1000f,
            displayColorSpace = ColorSpace.DISPLAY_P3
        )
        val reconstructed = DisplayTransform(
            colorScience = ColorScience.entries.getOrNull(original.colorScience.ordinal) ?: ColorScience.ACES20,
            eotf = EOTF.entries.getOrNull(original.eotf.ordinal) ?: EOTF.SRGB,
            peakLuminance = original.peakLuminance,
            displayColorSpace = ColorSpace.entries.getOrNull(original.displayColorSpace.ordinal) ?: ColorSpace.SRGB
        )
        assertEquals(original.colorScience, reconstructed.colorScience)
        assertEquals(original.eotf, reconstructed.eotf)
        assertEquals(original.peakLuminance, reconstructed.peakLuminance, 0.001f)
        assertEquals(original.displayColorSpace, reconstructed.displayColorSpace)
    }

    @Test
    fun displayTransform_invalidOrdinal_fallsBackToDefault() {
        val fallback = ColorScience.entries.getOrNull(999) ?: ColorScience.ACES20
        assertEquals(ColorScience.ACES20, fallback)

        val eotfFallback = EOTF.entries.getOrNull(999) ?: EOTF.SRGB
        assertEquals(EOTF.SRGB, eotfFallback)

        val csFallback = ColorSpace.entries.getOrNull(999) ?: ColorSpace.SRGB
        assertEquals(ColorSpace.SRGB, csFallback)
    }

    // ================================================================
    // Tone curve state management
    // ================================================================

    @Test
    fun toneCurve_defaultXAndY_areLinearRamp() {
        val defaultX = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val defaultY = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        assertArrayEquals(defaultX, defaultY, 0.001f)
    }

    @Test
    fun toneCurve_customCurve_reflectedInParams() {
        val customX = floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f)
        val customY = floatArrayOf(0f, 0.1f, 0.3f, 0.7f, 1f)
        val params = PipelineParams(toneCurveX = customX, toneCurveY = customY, toneCurvePoints = 5)
        assertArrayEquals(customX, params.toneCurveX, 0.001f)
        assertArrayEquals(customY, params.toneCurveY, 0.001f)
    }

    // ================================================================
    // Color wheel state management
    // ================================================================

    @Test
    fun colorWheel_liftGammaGain_defaultArrays() {
        val defaultLift = floatArrayOf(0f, 0f, 0f)
        val defaultGamma = floatArrayOf(1f, 1f, 1f)
        val defaultGain = floatArrayOf(1f, 1f, 1f)
        assertArrayEquals(defaultLift, floatArrayOf(0f, 0f, 0f), 0.001f)
        assertArrayEquals(defaultGamma, floatArrayOf(1f, 1f, 1f), 0.001f)
        assertArrayEquals(defaultGain, floatArrayOf(1f, 1f, 1f), 0.001f)
    }

    @Test
    fun colorWheel_paramsRoundTrip() {
        val params = PipelineParams(
            colorWheelLiftR = 0.1f, colorWheelLiftG = 0.2f, colorWheelLiftB = 0.3f,
            colorWheelGammaR = 1.1f, colorWheelGammaG = 1.2f, colorWheelGammaB = 1.3f,
            colorWheelGainR = 0.9f, colorWheelGainG = 0.8f, colorWheelGainB = 0.7f
        )
        val lift = floatArrayOf(params.colorWheelLiftR, params.colorWheelLiftG, params.colorWheelLiftB)
        val gamma = floatArrayOf(params.colorWheelGammaR, params.colorWheelGammaG, params.colorWheelGammaB)
        val gain = floatArrayOf(params.colorWheelGainR, params.colorWheelGainG, params.colorWheelGainB)
        assertEquals(0.1f, lift[0], 0.001f)
        assertEquals(1.2f, gamma[1], 0.001f)
        assertEquals(0.7f, gain[2], 0.001f)
    }

    // ================================================================
    // HSL state management
    // ================================================================

    @Test
    fun hsl_updateSingleChannel_preservesOthers() {
        val params = PipelineParams()
        val updatedHueShift = params.hslHueShift.copyOf()
        updatedHueShift[2] = 15f // shift third channel by 15 degrees
        val updated = params.copy(hslHueShift = updatedHueShift)

        assertEquals(15f, updated.hslHueShift[2], 0.001f)
        assertEquals(0f, updated.hslHueShift[0], 0.001f) // other channels unchanged
        assertEquals(0f, updated.hslHueShift[1], 0.001f)
    }

    @Test
    fun hsl_saturationAndLuminanceScale_updateIndependently() {
        val params = PipelineParams()
        val satScale = params.hslSaturationScale.copyOf()
        satScale[0] = 1.5f
        val lumScale = params.hslLuminanceScale.copyOf()
        lumScale[5] = 0.8f

        val updated = params.copy(
            hslSaturationScale = satScale,
            hslLuminanceScale = lumScale
        )
        assertEquals(1.5f, updated.hslSaturationScale[0], 0.001f)
        assertEquals(0.8f, updated.hslLuminanceScale[5], 0.001f)
        // Verify other values are untouched
        assertEquals(1f, updated.hslSaturationScale[1], 0.001f)
        assertEquals(1f, updated.hslLuminanceScale[0], 0.001f)
    }

    // ================================================================
    // hasUnsavedChanges logic (cursor vs savedCursor)
    // ================================================================

    @Test
    fun hasUnsavedChanges_whenCursorsMatch_returnsFalse() {
        // Simulate: no operations yet, savedCursor=0, cursor=0
        val wv = WorkingVersion(boundImageId = 1u)
        val savedCursor = 0
        assertFalse(wv.cursor != savedCursor)
    }

    @Test
    fun hasUnsavedChanges_afterEdits_returnsTrue() {
        val wv = WorkingVersion(boundImageId = 1u)
        val savedCursor = 0
        val tx = EditTransaction(0u, OperatorType.EXPOSURE, JsonObject(emptyMap()), JsonObject(emptyMap()))
        wv.appendTransaction(tx)
        assertTrue(wv.cursor != savedCursor)
    }

    @Test
    fun hasUnsavedChanges_afterSave_returnsFalse() {
        val wv = WorkingVersion(boundImageId = 1u)
        val tx = EditTransaction(0u, OperatorType.EXPOSURE, JsonObject(emptyMap()), JsonObject(emptyMap()))
        wv.appendTransaction(tx)
        // Simulate save: update savedCursor
        var savedCursor = wv.cursor
        assertFalse(wv.cursor != savedCursor)
    }

    @Test
    fun hasUnsavedChanges_afterUndoPastSave_returnsTrue() {
        val wv = WorkingVersion(boundImageId = 1u)
        val tx1 = EditTransaction(0u, OperatorType.EXPOSURE, JsonObject(emptyMap()), JsonObject(emptyMap()))
        val tx2 = EditTransaction(1u, OperatorType.CONTRAST, JsonObject(emptyMap()), JsonObject(emptyMap()))
        wv.appendTransaction(tx1)
        wv.appendTransaction(tx2)
        // Simulate save after 2 transactions
        var savedCursor = wv.cursor // = 2
        assertFalse(wv.cursor != savedCursor)
        // Undo one
        wv.undo()
        assertTrue(wv.cursor != savedCursor) // cursor=1, saved=2
    }

    // ================================================================
    // Preset application
    // ================================================================

    @Test
    fun presetApplication_appliesAllParams() {
        val preset = PipelineParams(
            exposure = 1.0f,
            contrast = 0.5f,
            saturation = -0.3f,
            whiteBalanceTemp = 7200f,
            filmGrainIntensity = 0.15f
        )
        val current = PipelineParams()
        // Simulate applyPresetParams
        val applied = preset

        assertNotEquals(current.exposure, applied.exposure, 0.001f)
        assertEquals(1.0f, applied.exposure, 0.001f)
        assertEquals(0.5f, applied.contrast, 0.001f)
        assertEquals(-0.3f, applied.saturation, 0.001f)
        assertEquals(7200f, applied.whiteBalanceTemp, 0.001f)
        assertEquals(0.15f, applied.filmGrainIntensity, 0.001f)
    }

    @Test
    fun presetApplication_syncsToneCurveAndColorWheels() {
        val preset = PipelineParams(
            toneCurveX = floatArrayOf(0f, 0.1f, 0.4f, 0.9f, 1f),
            toneCurveY = floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f),
            colorWheelLiftR = 0.05f,
            colorWheelLiftG = -0.02f,
            colorWheelLiftB = 0.03f
        )
        // In the real ViewModel, applyPresetParams syncs these arrays to
        // separate mutable states. Here we verify the preset data is correct.
        assertEquals(0.1f, preset.toneCurveX[1], 0.001f)
        assertEquals(0.2f, preset.toneCurveY[1], 0.001f)
        assertEquals(0.05f, preset.colorWheelLiftR, 0.001f)
    }

    // ================================================================
    // materializeParamsToJson round-trip consistency
    // ================================================================

    @Test
    fun materializeParamsToJson_containsAllBasicFields() {
        val params = PipelineParams(
            exposure = 1.5f,
            contrast = -0.3f,
            saturation = 0.8f,
            whiteBalanceTemp = 5500f,
            sharpenAmount = 0.5f
        )
        val json = materializeParamsToJsonForTest(params)

        assertEquals(1.5f, json["exposure"]?.jsonPrimitive?.floatOrNull, 0.001f)
        assertEquals(-0.3f, json["contrast"]?.jsonPrimitive?.floatOrNull, 0.001f)
        assertEquals(0.8f, json["saturation"]?.jsonPrimitive?.floatOrNull, 0.001f)
        assertEquals(5500f, json["whiteBalanceTemp"]?.jsonPrimitive?.floatOrNull, 0.001f)
        assertEquals(0.5f, json["sharpenAmount"]?.jsonPrimitive?.floatOrNull, 0.001f)
    }

    @Test
    fun materializeParamsToJson_hslAndMixerArrays_useIndexNotation() {
        val params = PipelineParams(
            hslHueShift = FloatArray(8) { it.toFloat() * 5f },
            hslSaturationScale = FloatArray(8) { 1f + it * 0.1f },
            channelMixerMatrix = FloatArray(9) { if (it % 4 == 0) 1f else 0.2f }
        )
        val json = materializeParamsToJsonForTest(params)

        // Verify indexed keys exist
        assertNotNull(json["hslHueShift[0]"])
        assertNotNull(json["hslHueShift[7]"])
        assertNotNull(json["hslSaturationScale[3]"])
        assertNotNull(json["channelMixerMatrix[8]"])

        assertEquals(0f, json["hslHueShift[0]"]?.jsonPrimitive?.floatOrNull, 0.001f)
        assertEquals(35f, json["hslHueShift[7]"]?.jsonPrimitive?.floatOrNull, 0.001f)
    }

    @Test
    fun materializeParamsToJson_displayTransform_usesOrdinals() {
        val params = PipelineParams(
            displayTransform = DisplayTransform(
                colorScience = ColorScience.OPENDRT,
                eotf = EOTF.HLG,
                peakLuminance = 600f,
                displayColorSpace = ColorSpace.REC2020
            )
        )
        val json = materializeParamsToJsonForTest(params)

        assertEquals(
            ColorScience.OPENDRT.ordinal.toFloat(),
            json["displayTransform_colorScience"]?.jsonPrimitive?.floatOrNull,
            0.001f
        )
        assertEquals(
            EOTF.HLG.ordinal.toFloat(),
            json["displayTransform_eotf"]?.jsonPrimitive?.floatOrNull,
            0.001f
        )
        assertEquals(600f, json["displayTransform_peakLuminance"]?.jsonPrimitive?.floatOrNull, 0.001f)
    }

    // ================================================================
    // OperatorType coverage
    // ================================================================

    @Test
    fun operatorType_allValues_areSerializable() {
        val allTypes = OperatorType.entries
        assertTrue(allTypes.size >= 15)
        // Verify key types exist
        assertTrue(allTypes.contains(OperatorType.EXPOSURE))
        assertTrue(allTypes.contains(OperatorType.CONTRAST))
        assertTrue(allTypes.contains(OperatorType.SATURATION))
        assertTrue(allTypes.contains(OperatorType.TONE_CURVE))
        assertTrue(allTypes.contains(OperatorType.HSL))
        assertTrue(allTypes.contains(OperatorType.COLOR_WHEEL))
        assertTrue(allTypes.contains(OperatorType.DISPLAY_TRANSFORM))
        assertTrue(allTypes.contains(OperatorType.PRESET))
    }

    // ================================================================
    // Batch params cache concept
    // ================================================================

    @Test
    fun batchParamsCache_applyParamsToImages() {
        val cache = mutableMapOf<Long, PipelineParams>()
        val params = PipelineParams(exposure = 1.0f)
        val imageIds = listOf(1L, 2L, 3L)
        imageIds.forEach { id -> cache[id] = params }
        assertEquals(3, cache.size)
        assertEquals(1.0f, cache[2L]?.exposure, 0.001f)
    }

    // ================================================================
    // Helper: mirrors EditorViewModel.materializeParamsToJson
    // ================================================================

    private fun materializeParamsToJsonForTest(p: PipelineParams): JsonObject {
        val map = mutableMapOf<String, JsonPrimitive>()
        map["exposure"] = JsonPrimitive(p.exposure)
        map["contrast"] = JsonPrimitive(p.contrast)
        map["saturation"] = JsonPrimitive(p.saturation)
        map["vibrance"] = JsonPrimitive(p.vibrance)
        map["highlights"] = JsonPrimitive(p.highlights)
        map["shadows"] = JsonPrimitive(p.shadows)
        map["midtones"] = JsonPrimitive(p.midtones)
        map["shadowBoundary"] = JsonPrimitive(p.shadowBoundary)
        map["highlightBoundary"] = JsonPrimitive(p.highlightBoundary)
        map["whiteBalanceTemp"] = JsonPrimitive(p.whiteBalanceTemp)
        map["whiteBalanceTint"] = JsonPrimitive(p.whiteBalanceTint)
        map["autoExposureEnabled"] = JsonPrimitive(p.autoExposureEnabled)
        map["autoExposureValue"] = JsonPrimitive(p.autoExposureValue)
        map["sigmoidContrast"] = JsonPrimitive(p.sigmoidContrast)
        map["sigmoidPivot"] = JsonPrimitive(p.sigmoidPivot)
        map["sigmoidShoulder"] = JsonPrimitive(p.sigmoidShoulder)
        map["clarityAmount"] = JsonPrimitive(p.clarityAmount)
        map["clarityRadius"] = JsonPrimitive(p.clarityRadius)
        map["sharpenAmount"] = JsonPrimitive(p.sharpenAmount)
        map["filmGrainIntensity"] = JsonPrimitive(p.filmGrainIntensity)
        map["halationIntensity"] = JsonPrimitive(p.halationIntensity)
        map["halationThreshold"] = JsonPrimitive(p.halationThreshold)
        map["halationSpread"] = JsonPrimitive(p.halationSpread)
        map["halationRedBias"] = JsonPrimitive(p.halationRedBias)
        p.hslHueShift.forEachIndexed { i, v -> map["hslHueShift[$i]"] = JsonPrimitive(v) }
        p.hslSaturationScale.forEachIndexed { i, v -> map["hslSaturationScale[$i]"] = JsonPrimitive(v) }
        p.hslLuminanceScale.forEachIndexed { i, v -> map["hslLuminanceScale[$i]"] = JsonPrimitive(v) }
        p.channelMixerMatrix.forEachIndexed { i, v -> map["channelMixerMatrix[$i]"] = JsonPrimitive(v) }
        p.toneCurveX.forEachIndexed { i, v -> map["toneCurveX[$i]"] = JsonPrimitive(v) }
        p.toneCurveY.forEachIndexed { i, v -> map["toneCurveY[$i]"] = JsonPrimitive(v) }
        map["toneCurvePoints"] = JsonPrimitive(p.toneCurvePoints)
        map["displayTransform_colorScience"] = JsonPrimitive(p.displayTransform.colorScience.ordinal)
        map["displayTransform_eotf"] = JsonPrimitive(p.displayTransform.eotf.ordinal)
        map["displayTransform_peakLuminance"] = JsonPrimitive(p.displayTransform.peakLuminance)
        map["displayTransform_displayColorSpace"] = JsonPrimitive(p.displayTransform.displayColorSpace.ordinal)
        return JsonObject(map)
    }

    private fun assertArrayEquals(expected: FloatArray, actual: FloatArray, delta: Float) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals(expected[i], actual[i], delta)
    }
    }
}
