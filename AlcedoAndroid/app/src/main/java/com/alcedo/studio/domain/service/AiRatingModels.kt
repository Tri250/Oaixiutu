package com.alcedo.studio.domain.service

import kotlinx.serialization.Serializable

@Serializable
data class AiRating(
    val ratingId: String = "",
    val imageId: UInt = 0u,
    val stars: Int = 0,
    val caption: String = "",
    val tags: List<String> = emptyList(),
    val reason: String = "",
    val mood: RatingMood = RatingMood.CASUAL,
    val providerId: String? = null,
    val isWrittenToExif: Boolean = false
)

enum class RatingMood(val displayName: String, val emoji: String) {
    PROFESSIONAL("Professional", "🏢"),
    CASUAL("Casual", "😊"),
    SOCIAL_MEDIA("Social Media", "📱"),
    ARTISTIC("Artistic", "🎨"),
    PHOTOJOURNALISM("Photojournalism", "📰"),
    TECHNICAL("Technical", "⚙️"),
    MINIMALIST("Minimalist", "✨"),
    VINTAGE("Vintage", "📷")
}

enum class AiRatingStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}