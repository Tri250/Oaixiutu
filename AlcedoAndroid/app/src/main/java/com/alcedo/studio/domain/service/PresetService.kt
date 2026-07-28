package com.alcedo.studio.domain.service

import com.alcedo.studio.data.dao.PipelinePresetDao
import com.alcedo.studio.data.local.PipelinePresetEntity
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.data.model.PipelinePreset
import com.alcedo.studio.utils.IdGenerator
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preset management. CRUD for the look/preset catalogue used by the editor's
 * Preset panel. Built-in film-emulation presets are seeded on first access.
 */
@Singleton
class PresetService @Inject constructor(
    private val presetDao: PipelinePresetDao,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var seeded = false

    fun observeAll(): Flow<List<PipelinePreset>> =
        presetDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeByCategory(category: String): Flow<List<PipelinePreset>> =
        presetDao.observeByCategory(category).map { list -> list.map { it.toDomain() } }

    fun observeFavorites(): Flow<List<PipelinePreset>> =
        presetDao.observeFavorites().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: String): PipelinePreset? = withContext(ThreadPool.database) {
        presetDao.getById(id)?.toDomain()
    }

    suspend fun categories(): List<String> = withContext(ThreadPool.database) { presetDao.categories() }

    suspend fun save(preset: PipelinePreset) = withContext(ThreadPool.database) {
        presetDao.upsert(preset.toEntity())
    }

    suspend fun delete(id: String) = withContext(ThreadPool.database) { presetDao.deleteById(id) }

    suspend fun setFavorite(id: String, favorite: Boolean) = withContext(ThreadPool.database) {
        presetDao.setFavorite(id, favorite)
    }

    /** Seed built-in film-emulation presets if not already present. */
    suspend fun ensureBuiltIns() = withContext(ThreadPool.database) {
        if (seeded) return@withContext
        if (presetDao.count() > 0) {
            seeded = true
            return@withContext
        }
        BUILT_INS.forEach { presetDao.upsert(it.toEntity()) }
        seeded = true
    }

    private fun PipelinePresetEntity.toDomain(): PipelinePreset = PipelinePreset(
        id = id,
        name = name,
        category = category,
        adjustments = runCatching {
            json.decodeFromString<AdjustmentParams>(adjustmentsJson)
        }.getOrDefault(AdjustmentParams.DEFAULT),
        isBuiltIn = isBuiltIn,
        isFavorite = isFavorite,
        thumbnailPath = thumbnailPath,
        createdAt = createdAt,
    )

    private fun PipelinePreset.toEntity(): PipelinePresetEntity = PipelinePresetEntity(
        id = id,
        name = name,
        category = category,
        adjustmentsJson = json.encodeToString(adjustments),
        isBuiltIn = isBuiltIn,
        isFavorite = isFavorite,
        thumbnailPath = thumbnailPath,
        createdAt = createdAt,
    )

    companion object {
        val BUILT_INS: List<PipelinePreset> = listOf(
            preset("Neutral", "Film", AdjustmentParams.DEFAULT),
            preset("Kodachrome 64", "Film", AdjustmentParams(contrast = 0.1f, saturation = 0.08f, vibrance = 0.04f)),
            preset("Portra 400", "Film", AdjustmentParams(contrast = 0.05f, saturation = 0.05f, temperature = 0.05f)),
            preset("Velvia 50", "Film", AdjustmentParams(contrast = 0.12f, saturation = 0.18f, vibrance = 0.1f)),
            preset("Tri-X 400", "B&W", AdjustmentParams(contrast = 0.15f, saturation = -1f, clarity = 0.1f)),
            preset("Ilford HP5", "B&W", AdjustmentParams(contrast = 0.12f, saturation = -1f, filmGrainAmount = 0.3f)),
            preset("Cinestill 800T", "Film", AdjustmentParams(contrast = 0.08f, temperature = -0.15f, tint = 0.05f, halationAmount = 0.2f)),
            preset("Punch", "Creative", AdjustmentParams(contrast = 0.12f, saturation = 0.12f, clarity = 0.15f, vibrance = 0.08f)),
            preset("Soft Pastel", "Creative", AdjustmentParams(contrast = -0.05f, saturation = -0.05f, highlights = 0.1f)),
        )

        private fun preset(name: String, category: String, params: AdjustmentParams): PipelinePreset {
            // Use a stable id for built-ins so re-seeding is idempotent.
            val id = "builtin_${name.lowercase().replace(Regex("[^a-z0-9]+"), "_")}"
            return PipelinePreset(
                id = id,
                name = name,
                category = category,
                adjustments = params,
                isBuiltIn = true,
                isFavorite = false,
                createdAt = 0L,
            )
        }

        fun newPreset(name: String, category: String, params: AdjustmentParams): PipelinePreset =
            PipelinePreset(
                id = IdGenerator.newId("preset"),
                name = name,
                category = category,
                adjustments = params,
                isBuiltIn = false,
            )
    }
}
