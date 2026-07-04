package com.alcedo.studio.data.model

// Missing AI-related types needed by service stubs

enum class ModelDownloadStatus {
    NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, VERIFIED, FAILED, PAUSED, ACTIVATED
}

data class ModelAsset(
    val modelId: String = "",
    val modelName: String = "",
    val modelType: AiModelType = AiModelType.CLIP,
    val version: String = "",
    val fileSizeBytes: Long = 0L,
    val downloadUrl: String = "",
    val description: String = "",
    val embeddingDim: Int = 512,
    val requiredStorageBytes: Long = 0L,
    val downloadStatus: ModelDownloadStatus = ModelDownloadStatus.NOT_DOWNLOADED,
    val downloadProgress: Float = 0f,
    val localPath: String = "",
    val isActive: Boolean = false,
    val checksum: String = "",
    val minAndroidVersion: Int = 21
)

enum class RatingMood {
    LITE, NORMAL, HIGH, XHIGH, MAX
}

enum class AiRatingStatus {
    IDLE, PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
}

data class AiRating(
    val imageId: UInt,
    val score: Float = 0f,
    val mood: RatingMood = RatingMood.NORMAL,
    val reasoning: String = "",
    val tags: List<String> = emptyList(),
    val status: AiRatingStatus = AiRatingStatus.IDLE
)

enum class AiProviderType {
    OPENAI, ANTHROPIC, GOOGLE, LOCAL, CUSTOM
}

data class AiCredential(
    val credentialId: String = "",
    val providerType: AiProviderType = AiProviderType.OPENAI,
    val providerName: String = "",
    val apiKey: String = "",
    val baseUrl: String = "",
    val isActive: Boolean = false,
    val modelId: String = ""
)

data class AiProviderProfile(
    val profileId: String = "",
    val name: String = "",
    val providerType: AiProviderType = AiProviderType.OPENAI,
    val baseUrl: String = "",
    val defaultModelId: String = "",
    val isConfigured: Boolean = false
)

data class SearchQuery(
    val queryText: String = "",
    val isSemantic: Boolean = false,
    val filters: List<SearchFilter> = emptyList()
)

data class SearchFilter(
    val field: String = "",
    val value: String = "",
    val operator: String = "="
)
