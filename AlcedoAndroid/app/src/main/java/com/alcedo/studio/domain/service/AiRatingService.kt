package com.alcedo.studio.domain.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AI aesthetic rating service.
 *
 * Sends images to remote LLMs for aesthetic evaluation
 * including caption generation, tagging, and 1-5 star rating.
 * Currently stubbed — TODO: implement LLM integration.
 */
class AiRatingService(
    private val context: Context,
    private val credentialService: AiCredentialService
) {
    companion object {
        private const val TAG = "AiRatingService"
    }

    private val _ratingStatus = MutableStateFlow<Map<UInt, AiRatingStatus>>(emptyMap())
    val ratingStatus: StateFlow<Map<UInt, AiRatingStatus>> = _ratingStatus.asStateFlow()

    suspend fun rateImage(
        imageId: UInt,
        bitmap: Bitmap,
        mood: RatingMood = RatingMood.CASUAL,
        providerId: String? = null
    ): AiRating? {
        Log.w(TAG, "rateImage: not implemented, returning null")
        return null
    }

    suspend fun cancelRating(imageId: UInt) {
        // No-op stub
    }

    suspend fun rateImages(
        images: List<Pair<UInt, Bitmap>>,
        mood: RatingMood = RatingMood.CASUAL,
        providerId: String? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<AiRating> {
        return emptyList()
    }

    suspend fun writeRatingToExif(imagePath: String, rating: AiRating): Boolean = withContext(Dispatchers.IO) {
        Log.w(TAG, "writeRatingToExif: not implemented")
        false
    }
}