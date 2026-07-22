package com.alcedo.studio.data.model

/**
 * AI 评分状态枚举
 */
enum class AiRatingStatus {
    IDLE, RUNNING, COMPLETED, FAILED, CANCELLED
}

/**
 * AI 评分风格/情绪枚举
 */
enum class RatingMood {
    PROFESSIONAL,
    CASUAL,
    SOCIAL_MEDIA,
    ARTISTIC,
    PHOTOJOURNALISM,
    TECHNICAL,
    MINIMALIST,
    VINTAGE
}

/**
 * AI 评分结果数据类
 */
data class AiRating(
    val ratingId: String,
    val imageId: UInt,
    val stars: Int,
    val caption: String,
    val tags: List<String>,
    val reason: String,
    val mood: RatingMood,
    val providerId: String
)
