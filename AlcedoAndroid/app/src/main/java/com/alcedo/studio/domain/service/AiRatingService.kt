package com.alcedo.studio.domain.service

import android.net.Uri
import android.util.Log
import com.alcedo.studio.ai.LlmCullingClient
import com.alcedo.studio.data.dao.AiEmbeddingDao
import com.alcedo.studio.data.local.AiRatingEntity
import com.alcedo.studio.data.model.AiRating
import com.alcedo.studio.data.model.ImageFlag
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LLM-based photo culling/rating. Sends a low-res encoded image plus EXIF
 * summary to the configured LLM provider and parses the returned scores into
 * an [AiRating], persisting it in Room. Falls back to a heuristic
 * sharpness/exposure estimator when no LLM credentials are configured.
 */
@Singleton
class AiRatingService @Inject constructor(
    private val llmClient: LlmCullingClient,
    private val imageAnalysisEncoder: ImageAnalysisEncoder,
    private val credentialService: AiCredentialService,
    private val imageRepository: ImageRepository,
    private val ratingDao: AiEmbeddingDao,
) {

    /** Rate a single image. Returns the persisted [AiRating], or null on failure. */
    suspend fun rateImage(uri: Uri, imageId: String, metadata: Map<String, String>): AiRating? =
        withContext(ThreadPool.aiInference) {
            val profile = credentialService.activeProfile()
            val rating = if (profile != null && credentialService.hasActiveCredentials()) {
                val base64 = imageAnalysisEncoder.encodeThumbnail(uri, 768) ?: return@withContext null
                llmClient.injectedKey = credentialService.getApiKey(profile.id)
                llmClient.rateImage(imageId, base64, metadata, profile)
            } else {
                heuristicRating(imageId, metadata)
            } ?: return@withContext null
            persist(rating)
            // Reflect the suggested rating/flag on the image record.
            runCatching {
                imageRepository.setRating(imageId, rating.suggestedRating)
                imageRepository.setFlag(imageId, rating.suggestedFlag)
                imageRepository.setAiMetadata(imageId, null, emptyList(), rating.overallScore)
            }.onFailure { Log.w(TAG, "apply rating failed", it) }
            rating
        }

    /** Rate a batch, invoking [onProgress] after each image. */
    suspend fun cullBatch(
        items: List<Pair<Uri, String>>,
        metadata: Map<String, Map<String, String>>,
        onProgress: (Int, Int) -> Unit,
    ): List<AiRating> = withContext(ThreadPool.aiInference) {
        val results = mutableListOf<AiRating>()
        items.forEachIndexed { index, (uri, imageId) ->
            val meta = metadata[imageId] ?: emptyMap()
            val r = runCatching { rateImage(uri, imageId, meta) }.getOrNull()
            if (r != null) results.add(r)
            onProgress(index + 1, items.size)
        }
        results
    }

    /** Retrieve a previously persisted rating. */
    suspend fun getRating(imageId: String): AiRating? = withContext(ThreadPool.database) {
        ratingDao.getRating(imageId)?.toDomain()
    }

    /** Top-N highest-rated images. */
    suspend fun topRated(limit: Int): List<AiRating> = withContext(ThreadPool.database) {
        ratingDao.topRated(limit).map { it.toDomain() }
    }

    private suspend fun persist(rating: AiRating) {
        ratingDao.upsertRating(
            AiRatingEntity(
                imageId = rating.imageId,
                overallScore = rating.overallScore,
                technicalScore = rating.technicalScore,
                aestheticScore = rating.aestheticScore,
                sharpnessScore = rating.sharpnessScore,
                exposureScore = rating.exposureScore,
                compositionScore = rating.compositionScore,
                emotionScore = rating.emotionScore,
                rationale = rating.rationale,
                suggestedRating = rating.suggestedRating,
                suggestedFlag = rating.suggestedFlag.name,
                generatedAt = rating.generatedAt,
                modelId = rating.modelId,
                provider = rating.provider,
                confidence = rating.confidence,
            ),
        )
    }

    /** Lightweight on-device heuristic rating when no LLM is configured. */
    private fun heuristicRating(imageId: String, metadata: Map<String, String>): AiRating {
        val iso = metadata["iso"]?.toIntOrNull() ?: 200
        val sharp = (1f - (iso.toFloat() / 6400f).coerceIn(0f, 1f)).coerceIn(0.3f, 0.9f)
        val exposure = 0.7f
        val overall = (sharp * 0.5f + exposure * 0.5f).coerceIn(0f, 1f)
        val now = System.currentTimeMillis()
        return AiRating(
            imageId = imageId,
            overallScore = overall,
            technicalScore = sharp,
            aestheticScore = overall,
            sharpnessScore = sharp,
            exposureScore = exposure,
            compositionScore = 0.6f,
            emotionScore = 0.5f,
            rationale = "Heuristic estimate (no LLM configured).",
            suggestedRating = (overall * 5f).toInt().coerceIn(1, 5),
            suggestedFlag = if (overall > 0.75f) ImageFlag.PICK else ImageFlag.NONE,
            generatedAt = now,
            modelId = "heuristic",
            provider = "on-device",
            confidence = 0.4f,
        )
    }

    private fun AiRatingEntity.toDomain(): AiRating = AiRating(
        imageId = imageId,
        overallScore = overallScore,
        technicalScore = technicalScore,
        aestheticScore = aestheticScore,
        sharpnessScore = sharpnessScore,
        exposureScore = exposureScore,
        compositionScore = compositionScore,
        emotionScore = emotionScore,
        rationale = rationale,
        suggestedRating = suggestedRating,
        suggestedFlag = runCatching { ImageFlag.valueOf(suggestedFlag) }.getOrDefault(ImageFlag.NONE),
        generatedAt = generatedAt,
        modelId = modelId,
        provider = provider,
        confidence = confidence,
    )

    companion object {
        private const val TAG = "AiRatingService"
    }
}
