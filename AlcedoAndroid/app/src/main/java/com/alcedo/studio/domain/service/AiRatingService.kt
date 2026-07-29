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
    private val taskService: BackgroundTaskService,
) {

    /** Rate a single image. Returns the persisted [AiRating], or null on failure. */
    suspend fun rateImage(uri: Uri, imageId: String, metadata: Map<String, String>): AiRating? =
        withContext(ThreadPool.aiInference) {
            val profile = credentialService.activeProfile()
            val rating = if (profile != null && credentialService.hasActiveCredentials()) {
                val base64 = imageAnalysisEncoder.encodeThumbnail(uri, 768) ?: return@withContext null
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

    /**
     * Rate a batch, invoking [onProgress] after each image.
     *
     * @param taskId optional background-task id; the loop polls
     *  [BackgroundTaskService.isCancelled] and aborts early if cancelled.
     * @param provider the LLM provider preference (e.g. "OPENAI"). Used to
     *  annotate the resulting ratings; the active credential profile still
     *  drives which provider is actually called.
     * @param strictness 0..1 scaling factor applied to the suggested rating and
     *  pick threshold: higher strictness yields lower (pickier) ratings.
     */
    suspend fun cullBatch(
        items: List<Pair<Uri, String>>,
        metadata: Map<String, Map<String, String>>,
        onProgress: (Int, Int) -> Unit,
        taskId: String? = null,
        provider: String? = null,
        strictness: Float = 0.5f,
    ): List<AiRating> = withContext(ThreadPool.aiInference) {
        val results = mutableListOf<AiRating>()
        val s = strictness.coerceIn(0f, 1f)
        items.forEachIndexed { index, (uri, imageId) ->
            // Cooperative cancellation: abort the cull if the task was cancelled.
            if (taskId != null && taskService.isCancelled(taskId)) {
                Log.i(TAG, "Cull cancelled by user at $index/${items.size}")
                return@withContext results
            }
            val meta = metadata[imageId] ?: emptyMap()
            val r = runCatching { rateImage(uri, imageId, meta) }.getOrNull()
            if (r != null) {
                val adjusted = applyStrictness(r, s, provider)
                // rateImage already persisted/applied the raw rating; overwrite
                // with the strictness-adjusted values so the DB and image record
                // stay consistent with what the user sees.
                persist(adjusted)
                runCatching {
                    imageRepository.setRating(imageId, adjusted.suggestedRating)
                    imageRepository.setFlag(imageId, adjusted.suggestedFlag)
                    imageRepository.setAiMetadata(imageId, null, emptyList(), adjusted.overallScore)
                }.onFailure { Log.w(TAG, "apply adjusted rating failed", it) }
                results.add(adjusted)
            }
            onProgress(index + 1, items.size)
        }
        results
    }

    /** Scale a rating's suggested stars/flag by [strictness] and tag the provider. */
    private fun applyStrictness(rating: AiRating, strictness: Float, provider: String?): AiRating {
        if (strictness == 0.5f && provider == null) return rating
        // strictness 0 = lenient (boost), 1 = strict (penalise).
        val penalty = (strictness - 0.5f) * 2f // -1..1
        val adjustedOverall = (rating.overallScore - penalty * 0.15f).coerceIn(0f, 1f)
        val stars = (adjustedOverall * 5f).toInt().coerceIn(1, 5)
        val flag = if (adjustedOverall > 0.75f - penalty * 0.1f) ImageFlag.PICK
        else if (adjustedOverall < 0.3f) ImageFlag.REJECT
        else ImageFlag.NONE
        return rating.copy(
            overallScore = adjustedOverall,
            suggestedRating = stars,
            suggestedFlag = flag,
            provider = provider ?: rating.provider,
        )
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
