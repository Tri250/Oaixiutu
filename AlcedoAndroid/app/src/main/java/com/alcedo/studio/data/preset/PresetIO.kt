package com.alcedo.studio.data.preset

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.alcedo.studio.data.model.PipelineParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * Serializable preset file format for import/export.
 * Uses kotlinx.serialization for JSON preset files with .alcedo-preset extension.
 */
@Serializable
data class PresetFileFormat(
    val version: Int = PRESET_FILE_VERSION,
    val name: String = "",
    val category: String = "custom",
    val created_at: String = "",
    val params: Map<String, Float> = emptyMap()
) {
    companion object {
        const val PRESET_FILE_VERSION = 1
        const val FILE_EXTENSION = ".alcedo-preset"
        const val MIME_TYPE = "application/json"
    }
}

/**
 * User preset data class for import/export operations.
 */
data class UserPreset(
    val name: String,
    val category: String,
    val params: PipelineParams,
    val createdAt: String = Instant.now().toString()
)

/**
 * Preset import/export utility.
 * Handles .alcedo-preset JSON files via SAF (Storage Access Framework).
 */
object PresetIO {

    private const val TAG = "PresetIO"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ── Export ─────────────────────────────────────────────────────

    /**
     * Export a user preset to a destination URI via SAF.
     * Writes the preset as a JSON file with the standard format.
     */
    suspend fun exportPreset(preset: UserPreset, uri: Uri, context: Context): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val paramsMap = paramsToMap(preset.params)
                val presetFile = PresetFileFormat(
                    version = PresetFileFormat.PRESET_FILE_VERSION,
                    name = preset.name,
                    category = preset.category,
                    created_at = preset.createdAt,
                    params = paramsMap
                )
                val jsonStr = json.encodeToString(PresetFileFormat.serializer(), presetFile)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonStr.toByteArray(Charsets.UTF_8))
                } ?: return@withContext Result.failure(
                    IOException("Cannot open output stream for URI: $uri")
                )
                Log.d(TAG, "Exported preset: ${preset.name}")
                Result.success(Unit)
            } catch (e: Throwable) {
                Log.e(TAG, "Export preset failed", e)
                Result.failure(e)
            }
        }

    /**
     * Export a preset to a local file.
     */
    suspend fun exportPreset(preset: UserPreset, outputFile: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val paramsMap = paramsToMap(preset.params)
                val presetFile = PresetFileFormat(
                    version = PresetFileFormat.PRESET_FILE_VERSION,
                    name = preset.name,
                    category = preset.category,
                    created_at = preset.createdAt,
                    params = paramsMap
                )
                val jsonStr = json.encodeToString(PresetFileFormat.serializer(), presetFile)
                outputFile.apply {
                    parentFile?.mkdirs()
                    writeText(jsonStr)
                }
                Log.d(TAG, "Exported preset to file: ${outputFile.absolutePath}")
                Result.success(Unit)
            } catch (e: Throwable) {
                Log.e(TAG, "Export preset to file failed", e)
                Result.failure(e)
            }
        }

    // ── Import ─────────────────────────────────────────────────────

    /**
     * Import a preset from a URI (via SAF).
     * Supports .alcedo-preset and .json files.
     */
    suspend fun importPreset(uri: Uri, context: Context): Result<UserPreset> =
        withContext(Dispatchers.IO) {
            try {
                val jsonStr = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                } ?: return@withContext Result.failure(
                    IOException("Cannot open input stream for URI: $uri")
                )

                if (jsonStr.isBlank()) {
                    return@withContext Result.failure(
                        IOException("Preset file is empty")
                    )
                }

                val preset = parsePresetJson(jsonStr)
                Log.d(TAG, "Imported preset: ${preset.name}")
                Result.success(preset)
            } catch (e: Throwable) {
                Log.e(TAG, "Import preset failed", e)
                Result.failure(e)
            }
        }

    /**
     * Import a preset from a local file.
     */
    suspend fun importPreset(file: File): Result<UserPreset> =
        withContext(Dispatchers.IO) {
            try {
                if (!file.exists()) {
                    return@withContext Result.failure(
                        IOException("File not found: ${file.absolutePath}")
                    )
                }
                if (!file.canRead()) {
                    return@withContext Result.failure(
                        IOException("File not readable: ${file.absolutePath}")
                    )
                }
                val jsonStr = file.readText()
                if (jsonStr.isBlank()) {
                    return@withContext Result.failure(
                        IOException("Preset file is empty: ${file.absolutePath}")
                    )
                }
                val preset = parsePresetJson(jsonStr)
                Log.d(TAG, "Imported preset from file: ${preset.name}")
                Result.success(preset)
            } catch (e: Throwable) {
                Log.e(TAG, "Import preset from file failed", e)
                Result.failure(e)
            }
        }

    // ── Share ──────────────────────────────────────────────────────

    /**
     * Share a preset via Android share intent.
     * Writes the preset to a temporary cache file and creates a share intent.
     */
    fun sharePreset(context: Context, preset: UserPreset) {
        try {
            val paramsMap = paramsToMap(preset.params)
            val presetFile = PresetFileFormat(
                version = PresetFileFormat.PRESET_FILE_VERSION,
                name = preset.name,
                category = preset.category,
                created_at = preset.createdAt,
                params = paramsMap
            )
            val jsonStr = json.encodeToString(PresetFileFormat.serializer(), presetFile)

            val tempFile = File(context.cacheDir, "${preset.name.replace(Regex("[^A-Za-z0-9._-]"), "_")}${PresetFileFormat.FILE_EXTENSION}")
            tempFile.writeText(jsonStr)

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = PresetFileFormat.MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, preset.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share preset"))
        } catch (e: Throwable) {
            Log.e(TAG, "Share preset failed", e)
        }
    }

    // ── JSON Parsing ───────────────────────────────────────────────

    /**
     * Parses a preset JSON string into a [UserPreset].
     * Supports both the standard PresetFileFormat and the legacy
     * format used by PresetService (with "params" as a nested JSON object).
     */
    private fun parsePresetJson(jsonStr: String): UserPreset {
        val root = Json.parseToJsonElement(jsonStr).jsonObject

        val name = root["name"]?.jsonPrimitive?.content ?: "Imported Preset"
        val category = root["category"]?.jsonPrimitive?.content?.ifBlank { "custom" } ?: "custom"
        val createdAt = root["created_at"]?.jsonPrimitive?.content ?: Instant.now().toString()

        // Support both new flat-params map and legacy nested JSON object
        val params = if (root.containsKey("params")) {
            val paramsElement = root["params"]
            if (paramsElement is JsonObject) {
                // Legacy format: params is a nested JSON object with PipelineParams fields
                parsePipelineParamsFromJsonObject(paramsElement.jsonObject)
            } else {
                PipelineParams()
            }
        } else {
            PipelineParams()
        }

        return UserPreset(
            name = name,
            category = category,
            params = params,
            createdAt = createdAt
        )
    }

    /**
     * Parse a [PipelineParams] from a JSON object, matching the serialization
     * format used by [com.alcedo.studio.domain.service.PresetService.serializeParams].
     */
    private fun parsePipelineParamsFromJsonObject(obj: JsonObject): PipelineParams {
        fun f(key: String, default: Float = 0f): Float =
            obj[key]?.jsonPrimitive?.floatOrNull ?: default

        fun b(key: String, default: Boolean = false): Boolean =
            obj[key]?.jsonPrimitive?.content?.toBoolean() ?: default

        fun s(key: String, default: String = ""): String =
            obj[key]?.jsonPrimitive?.content ?: default

        val hslHueShift = FloatArray(8) { i -> f("hslHueShift[$i]") }
        val hslSatScale = FloatArray(8) { i -> f("hslSaturationScale[$i]", 1f) }
        val hslLumScale = FloatArray(8) { i -> f("hslLuminanceScale[$i]", 1f) }
        val channelMixer = FloatArray(9) { i ->
            f("channelMixerMatrix[$i]", if (i % 4 == 0) 1f else 0f)
        }
        val toneCurveX = (0 until 5).map { f("toneCurveX[$it]", it / 4f) }.toFloatArray()
        val toneCurveY = (0 until 5).map { f("toneCurveY[$it]", it / 4f) }.toFloatArray()

        return PipelineParams(
            exposure = f("exposure"),
            contrast = f("contrast"),
            saturation = f("saturation"),
            vibrance = f("vibrance"),
            highlights = f("highlights"),
            shadows = f("shadows"),
            midtones = f("midtones"),
            shadowBoundary = f("shadowBoundary", 0.25f),
            highlightBoundary = f("highlightBoundary", 0.75f),
            whiteBalanceTemp = f("whiteBalanceTemp", 6500f),
            whiteBalanceTint = f("whiteBalanceTint"),
            sigmoidContrast = f("sigmoidContrast"),
            sigmoidPivot = f("sigmoidPivot", 0.18f),
            sigmoidShoulder = f("sigmoidShoulder", 0.5f),
            tintHighlightHue = f("tintHighlightHue"),
            tintHighlightStrength = f("tintHighlightStrength"),
            tintShadowHue = f("tintShadowHue"),
            tintShadowStrength = f("tintShadowStrength"),
            tintBalance = f("tintBalance"),
            colorWheelLiftR = f("colorWheelLiftR"),
            colorWheelLiftG = f("colorWheelLiftG"),
            colorWheelLiftB = f("colorWheelLiftB"),
            colorWheelGammaR = f("colorWheelGammaR", 1f),
            colorWheelGammaG = f("colorWheelGammaG", 1f),
            colorWheelGammaB = f("colorWheelGammaB", 1f),
            colorWheelGainR = f("colorWheelGainR", 1f),
            colorWheelGainG = f("colorWheelGainG", 1f),
            colorWheelGainB = f("colorWheelGainB", 1f),
            clarityAmount = f("clarityAmount"),
            clarityRadius = f("clarityRadius", 15f),
            sharpenAmount = f("sharpenAmount"),
            filmGrainIntensity = f("filmGrainIntensity"),
            halationIntensity = f("halationIntensity"),
            halationThreshold = f("halationThreshold", 0.8f),
            halationSpread = f("halationSpread", 10f),
            halationRedBias = f("halationRedBias", 0.7f),
            lutEnabled = b("lutEnabled"),
            lutIntensity = f("lutIntensity", 1f),
            lutPath = s("lutPath"),
            channelMixerMatrix = channelMixer,
            channelMixerMonochrome = b("channelMixerMonochrome"),
            hslHueShift = hslHueShift,
            hslSaturationScale = hslSatScale,
            hslLuminanceScale = hslLumScale,
            toneCurveX = toneCurveX,
            toneCurveY = toneCurveY,
            toneCurvePoints = f("toneCurvePoints", 5f).toInt(),
            lensVignetteStrength = f("lensVignetteStrength")
        )
    }

    // ── Params conversion ──────────────────────────────────────────

    /**
     * Convert a [PipelineParams] to a flat map of parameter names to float values.
     * Matches the serialization format used by PresetService for compatibility.
     */
    fun paramsToMap(p: PipelineParams): Map<String, Float> = buildMap {
        put("exposure", p.exposure)
        put("contrast", p.contrast)
        put("saturation", p.saturation)
        put("vibrance", p.vibrance)
        put("highlights", p.highlights)
        put("shadows", p.shadows)
        put("midtones", p.midtones)
        put("shadowBoundary", p.shadowBoundary)
        put("highlightBoundary", p.highlightBoundary)
        put("whiteBalanceTemp", p.whiteBalanceTemp)
        put("whiteBalanceTint", p.whiteBalanceTint)
        put("sigmoidContrast", p.sigmoidContrast)
        put("sigmoidPivot", p.sigmoidPivot)
        put("sigmoidShoulder", p.sigmoidShoulder)
        put("tintHighlightHue", p.tintHighlightHue)
        put("tintHighlightStrength", p.tintHighlightStrength)
        put("tintShadowHue", p.tintShadowHue)
        put("tintShadowStrength", p.tintShadowStrength)
        put("tintBalance", p.tintBalance)
        put("colorWheelLiftR", p.colorWheelLiftR)
        put("colorWheelLiftG", p.colorWheelLiftG)
        put("colorWheelLiftB", p.colorWheelLiftB)
        put("colorWheelGammaR", p.colorWheelGammaR)
        put("colorWheelGammaG", p.colorWheelGammaG)
        put("colorWheelGammaB", p.colorWheelGammaB)
        put("colorWheelGainR", p.colorWheelGainR)
        put("colorWheelGainG", p.colorWheelGainG)
        put("colorWheelGainB", p.colorWheelGainB)
        put("clarityAmount", p.clarityAmount)
        put("clarityRadius", p.clarityRadius)
        put("sharpenAmount", p.sharpenAmount)
        put("filmGrainIntensity", p.filmGrainIntensity)
        put("halationIntensity", p.halationIntensity)
        put("halationThreshold", p.halationThreshold)
        put("halationSpread", p.halationSpread)
        put("halationRedBias", p.halationRedBias)
        put("lutIntensity", p.lutIntensity)
        put("lensVignetteStrength", p.lensVignetteStrength)
        put("toneCurvePoints", p.toneCurvePoints.toFloat())
        p.hslHueShift.forEachIndexed { i, v -> put("hslHueShift[$i]", v) }
        p.hslSaturationScale.forEachIndexed { i, v -> put("hslSaturationScale[$i]", v) }
        p.hslLuminanceScale.forEachIndexed { i, v -> put("hslLuminanceScale[$i]", v) }
        p.channelMixerMatrix.forEachIndexed { i, v -> put("channelMixerMatrix[$i]", v) }
        p.toneCurveX.forEachIndexed { i, v -> put("toneCurveX[$i]", v) }
        p.toneCurveY.forEachIndexed { i, v -> put("toneCurveY[$i]", v) }
    }
}
