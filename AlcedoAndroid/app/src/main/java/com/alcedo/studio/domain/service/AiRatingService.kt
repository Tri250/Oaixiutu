package com.alcedo.studio.domain.service

import android.content.Context
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AiRatingService(
    private val context: Context,
    private val aiCredentialService: AiCredentialService
) {

    private val _ratingStatus = MutableStateFlow<Map<UInt, AiRatingStatus>>(emptyMap())
    val ratingStatus: StateFlow<Map<UInt, AiRatingStatus>> = _ratingStatus.asStateFlow()

    private val _ratings = MutableStateFlow<Map<UInt, AiRating>>(emptyMap())
    val ratings: StateFlow<Map<UInt, AiRating>> = _ratings.asStateFlow()

    private fun setRatingStatus(imageId: UInt, status: AiRatingStatus) {
        _ratingStatus.value = _ratingStatus.value.toMutableMap().apply { this[imageId] = status }
    }

    suspend fun rateImage(
        imageId: UInt,
        imagePath: String,
        mood: RatingMood = RatingMood.NORMAL,
        credential: AiCredential? = null
    ): AiRating? = null

    suspend fun rateImages(
        imageIds: List<UInt>,
        imagePaths: Map<UInt, String>,
        mood: RatingMood = RatingMood.NORMAL,
        credential: AiCredential? = null
    ): Map<UInt, AiRating> = emptyMap()

    suspend fun cancelRating(imageId: UInt) {
        setRatingStatus(imageId, AiRatingStatus.CANCELLED)
    }

    fun getRating(imageId: UInt): AiRating? = _ratings.value[imageId]

    fun getRatingStatus(imageId: UInt): AiRatingStatus =
        _ratingStatus.value[imageId] ?: AiRatingStatus.IDLE

    suspend fun clearRating(imageId: UInt) {}

    suspend fun clearAllRatings() {}
}
