package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.graphics.Color
import com.alcedo.studio.data.dao.PipelinePresetDao
import com.alcedo.studio.data.model.ColorScience
import com.alcedo.studio.data.model.ColorSpace
import com.alcedo.studio.data.model.DisplayTransform
import com.alcedo.studio.data.model.EOTF
import com.alcedo.studio.data.model.OpenDrtLook
import com.alcedo.studio.data.model.OpenDrtTonescale
import com.alcedo.studio.data.model.PipelineParams
import com.alcedo.studio.data.model.PipelinePresetEntity
import com.alcedo.studio.domain.repository.EditHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * A preset entry paired with a real, pipeline-rendered thumbnail bitmap.
 */
data class PresetWithThumbnail(
    val id: Long,
    val name: String,
    val category: String,
    val isBuiltIn: Boolean,
    val createdTime: Long,
    val params: PipelineParams,
    val thumbnail: Bitmap?
)

/**
 * REAL preset management service (RapidRAW-inspired).
 *
 * Persists presets in [PipelinePresetDao], generates real thumbnails by running
 * the native image pipeline on a synthetic sample image with the preset's
 * [PipelineParams] applied, supports JSON export/import, batch application to
 * multiple images, and seeds 12 visually distinct built-in presets on first launch.
 */
class PresetService(
    private val presetDao: PipelinePresetDao,
    private val pipelineService: PipelineService,
    private val editHistoryRepository: EditHistoryRepository
) {

    companion object {
        const val CATEGORY_FILM = "Film"
        const val CATEGORY_PORTRAIT = "Portrait"
        const val CATEGORY_LANDSCAPE = "Landscape"
        const val CATEGORY_BW = "B&W"
        const val CATEGORY_GENERAL = "General"

        private const val SAMPLE_SIZE = 160
        private const val EXPORT_FORMAT_VERSION = 1
    }

    private data class ThumbnailCacheEntry(val paramsJson: String, val thumbnail: Bitmap?)

    private val thumbnailCache = ConcurrentHashMap<Long, ThumbnailCacheEntry>()
    @Volatile
    private var sampleBitmap: Bitmap? = null

    @Volatile
    private var builtInsInitialized = false

    // ================================================================
    // Built-in preset seeding
    // ================================================================

    /**
     * Seeds the 12 built-in presets on first launch. Safe to call repeatedly;
     * only inserts when no built-in presets exist yet.
     */
    suspend fun ensureBuiltInPresetsInitialized() {
        if (builtInsInitialized) return
        val existing = presetDao.getBuiltInPresets()
        if (existing.isEmpty()) {
            val now = System.currentTimeMillis()
            presetDao.insertAll(
                BUILT_IN_PRESETS.map { (name, category, params) ->
                    PipelinePresetEntity(
                        name = name,
                        category = category,
                        paramsJson = serializeParams(params),
                        createdTime = now,
                        isBuiltIn = true
                    )
                }
            )
        }
        builtInsInitialized = true
    }

    // ================================================================
    // CRUD + queries
    // ================================================================

    /**
     * Returns all presets (built-in + custom) as a Flow, each carrying a real
     * pipeline-rendered thumbnail. Thumbnails are cached per preset id and only
     * regenerated when the serialized params change.
     */
    fun getAllPresets(): Flow<List<PresetWithThumbnail>> =
        presetDao.getAllFlow().map { entities ->
            entities.map { entity ->
                val params = deserializeParams(entity.paramsJson)
                PresetWithThumbnail(
                    id = entity.id,
                    name = entity.name,
                    category = entity.category,
                    isBuiltIn = entity.isBuiltIn,
                    createdTime = entity.createdTime,
                    params = params,
                    thumbnail = getOrGenerateThumbnail(entity.id, entity.paramsJson, params)
                )
            }
        }

    /**
     * Creates a new user preset from the supplied [params]. The optional
     * [thumbnailBitmap] (e.g. the editor's current preview) is cached so the
     * grid can show it immediately; otherwise a real thumbnail is rendered from
     * the sample image.
     */
    suspend fun createPreset(
        name: String,
        category: String,
        params: PipelineParams,
        thumbnailBitmap: Bitmap?
    ): Long = withContext(Dispatchers.IO) {
        ensureBuiltInPresetsInitialized()
        val paramsJson = serializeParams(params)
        val entity = PipelinePresetEntity(
            name = name.ifBlank { "Untitled" },
            category = category,
            paramsJson = paramsJson,
            createdTime = System.currentTimeMillis(),
            isBuiltIn = false
        )
        val id = presetDao.insert(entity)
        if (thumbnailBitmap != null) {
            thumbnailCache[id] = ThumbnailCacheEntry(paramsJson, thumbnailBitmap)
        }
        id
    }

    /** Updates an existing preset's name/category/params. */
    suspend fun updatePreset(
        presetId: Long,
        name: String,
        category: String,
        params: PipelineParams
    ): Boolean = withContext(Dispatchers.IO) {
        val existing = presetDao.getById(presetId) ?: return@withContext false
        presetDao.update(
            existing.copy(
                name = name.ifBlank { existing.name },
                category = category,
                paramsJson = serializeParams(params)
            )
        )
        thumbnailCache.remove(presetId)
        true
    }

    /** Loads and returns the preset's [PipelineParams]. */
    suspend fun applyPreset(presetId: Long): PipelineParams = withContext(Dispatchers.IO) {
        val entity = presetDao.getById(presetId)
            ?: throw IllegalArgumentException("Preset $presetId not found")
        deserializeParams(entity.paramsJson)
    }

    /** Deletes a preset by id. */
    suspend fun deletePreset(presetId: Long): Unit = withContext(Dispatchers.IO) {
        presetDao.deleteById(presetId)
        thumbnailCache.remove(presetId)
    }

    /**
     * Exports a single preset as a JSON file to [outputPath].
     * Returns true on success.
     */
    suspend fun exportPreset(presetId: Long, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val entity = presetDao.getById(presetId) ?: return@withContext false
            val exportObj = buildMap {
                put("formatVersion", JsonPrimitive(EXPORT_FORMAT_VERSION))
                put("name", JsonPrimitive(entity.name))
                put("category", JsonPrimitive(entity.category))
                put("isBuiltIn", JsonPrimitive(entity.isBuiltIn))
                put("createdTime", JsonPrimitive(entity.createdTime))
                put("params", Json.parseToJsonElement(entity.paramsJson))
            }
            val json = Json { prettyPrint = true; encodeDefaults = true }
            File(outputPath).apply {
                parentFile?.mkdirs()
                writeText(json.encodeToString(JsonObject.serializer(), JsonObject(exportObj)))
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Imports a preset from a JSON file at [filePath]. Returns the new preset id.
     */
    suspend fun importPreset(filePath: String): Long = withContext(Dispatchers.IO) {
        val text = File(filePath).readText()
        val obj = Json.parseToJsonElement(text).jsonObject
        val name = obj["name"]?.jsonPrimitive?.content ?: "Imported Preset"
        val category = obj["category"]?.jsonPrimitive?.content ?: CATEGORY_GENERAL
        val paramsJson = obj["params"]?.let { it.toString() } ?: "{}"
        val entity = PipelinePresetEntity(
            name = name,
            category = category,
            paramsJson = paramsJson,
            createdTime = System.currentTimeMillis(),
            isBuiltIn = false
        )
        presetDao.insert(entity)
    }

    /**
     * Exports every preset to [outputDir] as individual JSON files.
     * Returns the number of presets exported.
     */
    suspend fun exportAllPresets(outputDir: String): Int = withContext(Dispatchers.IO) {
        val dir = File(outputDir).apply { mkdirs() }
        val all = presetDao.getAll()
        var count = 0
        for (entity in all) {
            val safeName = entity.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val out = File(dir, "$safeName.json")
            val exportObj = buildMap {
                put("formatVersion", JsonPrimitive(EXPORT_FORMAT_VERSION))
                put("name", JsonPrimitive(entity.name))
                put("category", JsonPrimitive(entity.category))
                put("isBuiltIn", JsonPrimitive(entity.isBuiltIn))
                put("createdTime", JsonPrimitive(entity.createdTime))
                put("params", Json.parseToJsonElement(entity.paramsJson))
            }
            val json = Json { prettyPrint = true; encodeDefaults = true }
            out.writeText(json.encodeToString(JsonObject.serializer(), JsonObject(exportObj)))
            count++
        }
        count
    }

    /**
     * Applies the preset's [PipelineParams] to a batch of images by writing the
     * materialized params into each image's active edit-history version.
     * Returns the number of images successfully updated.
     */
    suspend fun applyPresetToBatch(presetId: Long, imageIds: List<String>): Int =
        withContext(Dispatchers.IO) {
            val entity = presetDao.getById(presetId) ?: return@withContext 0
            val paramsJson = materializeParamsJsonForHistory(deserializeParams(entity.paramsJson))
            var applied = 0
            for (id in imageIds) {
                val imageIdLong = id.toLongOrNull()?.toUInt() ?: continue
                try {
                    val history = editHistoryRepository.getHistory(imageIdLong)
                    if (history != null) {
                        val activeVersionId = history.activeVersionId
                        history.getVersion(activeVersionId)?.let { v ->
                            v.materializedParams = paramsJson
                        }
                        editHistoryRepository.saveHistory(history)
                        applied++
                    }
                } catch (_: Throwable) {
                    /* skip this image */
                }
            }
            applied
        }

    // ================================================================
    // Thumbnail rendering
    // ================================================================

    private suspend fun getOrGenerateThumbnail(
        presetId: Long,
        paramsJson: String,
        params: PipelineParams
    ): Bitmap? = withContext(Dispatchers.Default) {
        val cached = thumbnailCache[presetId]
        if (cached != null && cached.paramsJson == paramsJson) {
            return@withContext cached.thumbnail
        }
        val thumb = generateThumbnail(params)
        thumbnailCache[presetId] = ThumbnailCacheEntry(paramsJson, thumb)
        thumb
    }

    private suspend fun generateThumbnail(params: PipelineParams): Bitmap? {
        return try {
            val sample = getOrCreateSampleBitmap()
            pipelineService.applyPipeline(sample, params)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Renders a preview of the given [params] on the sample image. Used by the
     * preset panel's before/after overlay. The "before" image is obtained by
     * calling this with default [PipelineParams]. Results are cached for the
     * default-params case so repeated overlay opens are instant.
     */
    @Volatile
    private var defaultPreviewCache: Bitmap? = null

    suspend fun renderPreview(params: PipelineParams): Bitmap? = withContext(Dispatchers.Default) {
        val isDefault = isDefaultParams(params)
        if (isDefault) {
            defaultPreviewCache?.let { return@withContext it }
        }
        val rendered = generateThumbnail(params)
        if (isDefault && rendered != null) {
            defaultPreviewCache = rendered
        }
        rendered
    }

    private fun isDefaultParams(p: PipelineParams): Boolean {
        return p.exposure == 0f && p.contrast == 0f && p.saturation == 0f &&
            p.vibrance == 0f && p.highlights == 0f && p.shadows == 0f &&
            p.whiteBalanceTemp == 6500f && p.whiteBalanceTint == 0f &&
            !p.channelMixerMonochrome && p.filmGrainIntensity == 0f &&
            p.halationIntensity == 0f && p.clarityAmount == 0f
    }

    /**
     * Builds a synthetic, multi-region sample image (skin, sky, foliage, red
     * accent) with a tone gradient, so that different presets produce visibly
     * distinct thumbnails (color casts, contrast, saturation, B&W, etc.).
     */
    private fun getOrCreateSampleBitmap(): Bitmap {
        sampleBitmap?.let { return it }
        val size = SAMPLE_SIZE
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val skinA = Color.rgb(180, 120, 90)
        val skinB = Color.rgb(225, 175, 135)
        val skyA = Color.rgb(70, 125, 175)
        val skyB = Color.rgb(150, 190, 225)
        val grassA = Color.rgb(55, 115, 55)
        val grassB = Color.rgb(115, 165, 80)
        val redA = Color.rgb(175, 55, 75)
        val redB = Color.rgb(225, 105, 140)
        for (y in 0 until size) {
            val tone = y.toFloat() / (size - 1).toFloat() // 0..1 dark->light
            val toneFactor = 0.32f + 0.68f * tone
            for (x in 0 until size) {
                val xf = x.toFloat() / size.toFloat()
                val base = when {
                    xf < 0.25f -> blend(skinA, skinB, xf / 0.25f)
                    xf < 0.5f -> blend(skyA, skyB, (xf - 0.25f) / 0.25f)
                    xf < 0.75f -> blend(grassA, grassB, (xf - 0.5f) / 0.25f)
                    else -> blend(redA, redB, (xf - 0.75f) / 0.25f)
                }
                val r = (Color.red(base) * toneFactor).toInt().coerceIn(0, 255)
                val g = (Color.green(base) * toneFactor).toInt().coerceIn(0, 255)
                val b = (Color.blue(base) * toneFactor).toInt().coerceIn(0, 255)
                bmp.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        sampleBitmap = bmp
        return bmp
    }

    private fun blend(c1: Int, c2: Int, t: Float): Int {
        val r = (Color.red(c1) * (1f - t) + Color.red(c2) * t).toInt()
        val g = (Color.green(c1) * (1f - t) + Color.green(c2) * t).toInt()
        val b = (Color.blue(c1) * (1f - t) + Color.blue(c2) * t).toInt()
        return Color.rgb(r, g, b)
    }

    // ================================================================
    // PipelineParams JSON serialization (mirrors EditorViewModel)
    // ================================================================

    private fun serializeParams(p: PipelineParams): String {
        return JsonObject(buildParamsJsonMap(p)).toString()
    }

    private fun buildParamsJsonMap(p: PipelineParams): Map<String, JsonElement> {
        val map = mutableMapOf<String, JsonElement>()
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
        map["tintHighlightHue"] = JsonPrimitive(p.tintHighlightHue)
        map["tintHighlightStrength"] = JsonPrimitive(p.tintHighlightStrength)
        map["tintShadowHue"] = JsonPrimitive(p.tintShadowHue)
        map["tintShadowStrength"] = JsonPrimitive(p.tintShadowStrength)
        map["tintBalance"] = JsonPrimitive(p.tintBalance)
        map["colorWheelLiftR"] = JsonPrimitive(p.colorWheelLiftR)
        map["colorWheelLiftG"] = JsonPrimitive(p.colorWheelLiftG)
        map["colorWheelLiftB"] = JsonPrimitive(p.colorWheelLiftB)
        map["colorWheelGammaR"] = JsonPrimitive(p.colorWheelGammaR)
        map["colorWheelGammaG"] = JsonPrimitive(p.colorWheelGammaG)
        map["colorWheelGammaB"] = JsonPrimitive(p.colorWheelGammaB)
        map["colorWheelGainR"] = JsonPrimitive(p.colorWheelGainR)
        map["colorWheelGainG"] = JsonPrimitive(p.colorWheelGainG)
        map["colorWheelGainB"] = JsonPrimitive(p.colorWheelGainB)
        map["clarityAmount"] = JsonPrimitive(p.clarityAmount)
        map["clarityRadius"] = JsonPrimitive(p.clarityRadius)
        map["sharpenAmount"] = JsonPrimitive(p.sharpenAmount)
        map["filmGrainIntensity"] = JsonPrimitive(p.filmGrainIntensity)
        map["halationIntensity"] = JsonPrimitive(p.halationIntensity)
        map["halationThreshold"] = JsonPrimitive(p.halationThreshold)
        map["halationSpread"] = JsonPrimitive(p.halationSpread)
        map["halationRedBias"] = JsonPrimitive(p.halationRedBias)
        map["lutEnabled"] = JsonPrimitive(p.lutEnabled)
        map["lutIntensity"] = JsonPrimitive(p.lutIntensity)
        map["lutPath"] = JsonPrimitive(p.lutPath)
        map["geometryRotate"] = JsonPrimitive(p.geometryRotate)
        map["geometryScale"] = JsonPrimitive(p.geometryScale)
        map["geometryFlipH"] = JsonPrimitive(p.geometryFlipH)
        map["geometryFlipV"] = JsonPrimitive(p.geometryFlipV)
        map["cropLeft"] = JsonPrimitive(p.cropLeft)
        map["cropTop"] = JsonPrimitive(p.cropTop)
        map["cropRight"] = JsonPrimitive(p.cropRight)
        map["cropBottom"] = JsonPrimitive(p.cropBottom)
        map["perspectiveH"] = JsonPrimitive(p.perspectiveH)
        map["perspectiveV"] = JsonPrimitive(p.perspectiveV)
        map["lensK1"] = JsonPrimitive(p.lensK1)
        map["lensK2"] = JsonPrimitive(p.lensK2)
        map["lensK3"] = JsonPrimitive(p.lensK3)
        map["lensP1"] = JsonPrimitive(p.lensP1)
        map["lensP2"] = JsonPrimitive(p.lensP2)
        map["lensVignetteStrength"] = JsonPrimitive(p.lensVignetteStrength)
        map["channelMixerMonochrome"] = JsonPrimitive(p.channelMixerMonochrome)
        map["toneCurvePoints"] = JsonPrimitive(p.toneCurvePoints)
        p.hslHueShift.forEachIndexed { i, v -> map["hslHueShift[$i]"] = JsonPrimitive(v) }
        p.hslSaturationScale.forEachIndexed { i, v -> map["hslSaturationScale[$i]"] = JsonPrimitive(v) }
        p.hslLuminanceScale.forEachIndexed { i, v -> map["hslLuminanceScale[$i]"] = JsonPrimitive(v) }
        p.channelMixerMatrix.forEachIndexed { i, v -> map["channelMixerMatrix[$i]"] = JsonPrimitive(v) }
        p.toneCurveX.forEachIndexed { i, v -> map["toneCurveX[$i]"] = JsonPrimitive(v) }
        p.toneCurveY.forEachIndexed { i, v -> map["toneCurveY[$i]"] = JsonPrimitive(v) }
        map["displayTransform_colorScience"] = JsonPrimitive(p.displayTransform.colorScience.ordinal)
        map["displayTransform_eotf"] = JsonPrimitive(p.displayTransform.eotf.ordinal)
        map["displayTransform_peakLuminance"] = JsonPrimitive(p.displayTransform.peakLuminance)
        map["displayTransform_displayColorSpace"] = JsonPrimitive(p.displayTransform.displayColorSpace.ordinal)
        map["displayTransform_openDrtLook"] = JsonPrimitive(p.displayTransform.openDrtLook.ordinal)
        map["displayTransform_openDrtTonescale"] = JsonPrimitive(p.displayTransform.openDrtTonescale.ordinal)
        return map
    }

    private fun deserializeParams(json: String): PipelineParams {
        return try {
            val obj = Json.parseToJsonElement(json).jsonObject
            fun f(key: String, default: Float = 0f): Float =
                obj[key]?.jsonPrimitive?.floatOrNull ?: default

            val hslHueShift = FloatArray(8) { i -> f("hslHueShift[$i]") }
            val hslSatScale = FloatArray(8) { i -> f("hslSaturationScale[$i]", 1f) }
            val hslLumScale = FloatArray(8) { i -> f("hslLuminanceScale[$i]", 1f) }
            val channelMixer = FloatArray(9) { i ->
                f("channelMixerMatrix[$i]", if (i % 4 == 0) 1f else 0f)
            }
            val toneCurveX = (0 until 5).map { f("toneCurveX[$it]", it / 4f) }.toFloatArray()
            val toneCurveY = (0 until 5).map { f("toneCurveY[$it]", it / 4f) }.toFloatArray()

            val colorScience = ColorScience.entries.getOrNull(
                f("displayTransform_colorScience").toInt()) ?: ColorScience.ACES20
            val eotf = EOTF.entries.getOrNull(
                f("displayTransform_eotf").toInt()) ?: EOTF.SRGB
            val colorSpace = ColorSpace.entries.getOrNull(
                f("displayTransform_displayColorSpace").toInt()) ?: ColorSpace.SRGB
            val openDrtLook = OpenDrtLook.entries.getOrNull(
                f("displayTransform_openDrtLook").toInt()) ?: OpenDrtLook.STANDARD
            val openDrtTonescale = OpenDrtTonescale.entries.getOrNull(
                f("displayTransform_openDrtTonescale").toInt()) ?: OpenDrtTonescale.STANDARD

            PipelineParams(
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
                autoExposureEnabled = obj["autoExposureEnabled"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                autoExposureValue = f("autoExposureValue"),
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
                lutEnabled = obj["lutEnabled"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                lutIntensity = f("lutIntensity", 100f),
                lutPath = obj["lutPath"]?.jsonPrimitive?.content ?: "",
                geometryRotate = f("geometryRotate"),
                geometryScale = f("geometryScale", 1f),
                geometryFlipH = obj["geometryFlipH"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                geometryFlipV = obj["geometryFlipV"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                cropLeft = f("cropLeft"),
                cropTop = f("cropTop"),
                cropRight = f("cropRight", 1f),
                cropBottom = f("cropBottom", 1f),
                perspectiveH = f("perspectiveH"),
                perspectiveV = f("perspectiveV"),
                lensK1 = f("lensK1"),
                lensK2 = f("lensK2"),
                lensK3 = f("lensK3"),
                lensP1 = f("lensP1"),
                lensP2 = f("lensP2"),
                lensVignetteStrength = f("lensVignetteStrength"),
                channelMixerMatrix = channelMixer,
                channelMixerMonochrome = obj["channelMixerMonochrome"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                hslHueShift = hslHueShift,
                hslSaturationScale = hslSatScale,
                hslLuminanceScale = hslLumScale,
                toneCurveX = toneCurveX,
                toneCurveY = toneCurveY,
                toneCurvePoints = f("toneCurvePoints", 5f).toInt(),
                displayTransform = DisplayTransform(
                    colorScience = colorScience,
                    eotf = eotf,
                    peakLuminance = f("displayTransform_peakLuminance", 100f),
                    displayColorSpace = colorSpace,
                    openDrtLook = openDrtLook,
                    openDrtTonescale = openDrtTonescale
                )
            )
        } catch (_: Throwable) {
            PipelineParams()
        }
    }

    /**
     * Builds the materialized-params JSON object written into edit-history
     * versions when applying a preset to a batch. Same shape as
     * [serializeParams] (a flat JSON object).
     */
    private fun materializeParamsJsonForHistory(p: PipelineParams): JsonObject =
        JsonObject(buildParamsJsonMap(p))

    // ================================================================
    // 12 built-in presets with REAL, visually distinct parameter values
    // ================================================================

    private data class BuiltInPreset(val name: String, val category: String, val params: PipelineParams)

    private val BUILT_IN_PRESETS: List<BuiltInPreset> = listOf(
        BuiltInPreset(
            name = "Cinematic Teal & Orange",
            category = CATEGORY_FILM,
            params = PipelineParams(
                contrast = 0.18f,
                saturation = 0.1f,
                vibrance = 0.15f,
                highlights = -0.15f,
                shadows = 0.1f,
                whiteBalanceTemp = 6800f,
                whiteBalanceTint = 6f,
                tintHighlightHue = 30f,    // orange highlights
                tintHighlightStrength = 0.22f,
                tintShadowHue = 195f,      // teal shadows
                tintShadowStrength = 0.28f,
                tintBalance = 0.1f,
                clarityAmount = 0.15f,
                sigmoidContrast = 0.12f
            )
        ),
        BuiltInPreset(
            name = "Vintage Film",
            category = CATEGORY_FILM,
            params = PipelineParams(
                contrast = -0.12f,
                saturation = -0.22f,
                vibrance = -0.1f,
                shadows = 0.18f,
                highlights = -0.1f,
                whiteBalanceTemp = 7200f,
                whiteBalanceTint = 8f,
                filmGrainIntensity = 0.18f,
                halationIntensity = 0.25f,
                halationThreshold = 0.82f,
                halationRedBias = 0.65f,
                tintHighlightHue = 35f,
                tintHighlightStrength = 0.12f,
                tintShadowHue = 210f,
                tintShadowStrength = 0.08f,
                lensVignetteStrength = 0.25f
            )
        ),
        BuiltInPreset(
            name = "Black & White High Contrast",
            category = CATEGORY_BW,
            params = PipelineParams(
                contrast = 0.45f,
                saturation = -1f,
                vibrance = -1f,
                highlights = -0.2f,
                shadows = -0.15f,
                clarityAmount = 0.3f,
                sharpenAmount = 0.2f,
                channelMixerMonochrome = true,
                channelMixerMatrix = floatArrayOf(
                    0.3f, 0.59f, 0.11f,
                    0.3f, 0.59f, 0.11f,
                    0.3f, 0.59f, 0.11f
                ),
                sigmoidContrast = 0.2f
            )
        ),
        BuiltInPreset(
            name = "Soft Portrait",
            category = CATEGORY_PORTRAIT,
            params = PipelineParams(
                contrast = -0.12f,
                saturation = 0.05f,
                vibrance = 0.1f,
                highlights = -0.1f,
                shadows = 0.12f,
                whiteBalanceTemp = 6700f,
                whiteBalanceTint = 5f,
                clarityAmount = -0.18f,
                sharpenAmount = 0.08f,
                halationIntensity = 0.1f,
                halationThreshold = 0.85f,
                tintHighlightHue = 28f,
                tintHighlightStrength = 0.08f,
                exposure = 0.08f
            )
        ),
        BuiltInPreset(
            name = "Landscape Vivid",
            category = CATEGORY_LANDSCAPE,
            params = PipelineParams(
                contrast = 0.22f,
                saturation = 0.28f,
                vibrance = 0.3f,
                highlights = -0.18f,
                shadows = 0.15f,
                clarityAmount = 0.35f,
                sharpenAmount = 0.18f,
                whiteBalanceTemp = 6300f,
                hslSaturationScale = floatArrayOf(1.0f, 1.15f, 1.1f, 1.25f, 1.0f, 1.0f, 1.0f, 1.0f),
                hslLuminanceScale = floatArrayOf(1.0f, 0.95f, 1.0f, 0.92f, 1.0f, 1.0f, 1.0f, 1.0f),
                exposure = 0.05f
            )
        ),
        BuiltInPreset(
            name = "Moody Blue",
            category = CATEGORY_FILM,
            params = PipelineParams(
                contrast = 0.2f,
                saturation = -0.18f,
                vibrance = -0.05f,
                shadows = -0.1f,
                highlights = -0.15f,
                whiteBalanceTemp = 5200f,
                whiteBalanceTint = -10f,
                exposure = -0.15f,
                tintShadowHue = 210f,
                tintShadowStrength = 0.25f,
                tintHighlightHue = 200f,
                tintHighlightStrength = 0.1f,
                colorWheelLiftB = 0.04f,
                colorWheelLiftR = -0.03f,
                clarityAmount = 0.1f
            )
        ),
        BuiltInPreset(
            name = "Warm Sunset",
            category = CATEGORY_LANDSCAPE,
            params = PipelineParams(
                contrast = 0.1f,
                saturation = 0.18f,
                vibrance = 0.25f,
                highlights = -0.08f,
                shadows = 0.1f,
                whiteBalanceTemp = 7800f,
                whiteBalanceTint = 12f,
                exposure = 0.12f,
                tintHighlightHue = 32f,
                tintHighlightStrength = 0.2f,
                tintShadowHue = 25f,
                tintShadowStrength = 0.1f,
                halationIntensity = 0.12f,
                halationRedBias = 0.8f
            )
        ),
        BuiltInPreset(
            name = "Clean Minimal",
            category = CATEGORY_GENERAL,
            params = PipelineParams(
                contrast = 0.08f,
                saturation = -0.05f,
                vibrance = 0.05f,
                highlights = -0.05f,
                shadows = 0.05f,
                clarityAmount = 0.1f,
                sharpenAmount = 0.05f,
                whiteBalanceTemp = 6500f
            )
        ),
        BuiltInPreset(
            name = "Film Grain Classic",
            category = CATEGORY_FILM,
            params = PipelineParams(
                contrast = 0.05f,
                saturation = -0.15f,
                vibrance = -0.05f,
                shadows = 0.08f,
                whiteBalanceTemp = 6600f,
                whiteBalanceTint = 4f,
                filmGrainIntensity = 0.32f,
                halationIntensity = 0.18f,
                halationThreshold = 0.8f,
                halationSpread = 12f,
                halationRedBias = 0.6f,
                tintShadowHue = 220f,
                tintShadowStrength = 0.06f,
                lensVignetteStrength = 0.18f
            )
        ),
        BuiltInPreset(
            name = "HDR Natural",
            category = CATEGORY_LANDSCAPE,
            params = PipelineParams(
                contrast = 0.35f,
                saturation = 0.2f,
                vibrance = 0.22f,
                highlights = -0.35f,
                shadows = 0.45f,
                midtones = 0.1f,
                clarityAmount = 0.4f,
                sharpenAmount = 0.15f,
                sigmoidContrast = 0.15f,
                whiteBalanceTemp = 6500f
            )
        ),
        BuiltInPreset(
            name = "Faded Memory",
            category = CATEGORY_PORTRAIT,
            params = PipelineParams(
                contrast = -0.2f,
                saturation = -0.3f,
                vibrance = -0.1f,
                shadows = 0.22f,
                highlights = -0.05f,
                whiteBalanceTemp = 7000f,
                whiteBalanceTint = 6f,
                exposure = 0.1f,
                tintHighlightHue = 40f,
                tintHighlightStrength = 0.1f,
                tintShadowHue = 220f,
                tintShadowStrength = 0.05f,
                filmGrainIntensity = 0.1f,
                halationIntensity = 0.08f
            )
        ),
        BuiltInPreset(
            name = "Sharp Architecture",
            category = CATEGORY_LANDSCAPE,
            params = PipelineParams(
                contrast = 0.28f,
                saturation = 0.08f,
                vibrance = 0.1f,
                highlights = -0.12f,
                shadows = -0.05f,
                clarityAmount = 0.5f,
                sharpenAmount = 0.35f,
                whiteBalanceTemp = 6200f,
                sigmoidContrast = 0.1f,
                lensVignetteStrength = -0.1f
            )
        )
    )
}
