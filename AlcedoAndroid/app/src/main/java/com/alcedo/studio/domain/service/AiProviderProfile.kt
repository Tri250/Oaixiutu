package com.alcedo.studio.domain.service

/**
 * AI provider configuration profiles.
 * Ported from desktop ai_provider_profile.cpp
 * Supports OpenAI, Anthropic, OpenRouter, Volcengine providers.
 */
data class AiProviderConfig(
    val id: String,
    val displayName: String,
    val apiEndpoint: String,
    val models: List<String>,
    val supportsVision: Boolean = false,
    val supportsStreaming: Boolean = true,
    val maxTokens: Int = 4096
)

object AiProviderProfile {
    val OPENAI = AiProviderConfig(
        id = "openai",
        displayName = "OpenAI",
        apiEndpoint = "https://api.openai.com/v1",
        models = listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo"),
        supportsVision = true
    )

    val ANTHROPIC = AiProviderConfig(
        id = "anthropic",
        displayName = "Anthropic",
        apiEndpoint = "https://api.anthropic.com/v1",
        models = listOf("claude-sonnet-4-20250514", "claude-3-5-haiku-20241022"),
        supportsVision = true
    )

    val OPENROUTER = AiProviderConfig(
        id = "openrouter",
        displayName = "OpenRouter",
        apiEndpoint = "https://openrouter.ai/api/v1",
        models = listOf("openai/gpt-4o", "anthropic/claude-3.5-sonnet", "google/gemini-pro-vision"),
        supportsVision = true
    )

    val VOLCENGINE = AiProviderConfig(
        id = "volcengine",
        displayName = "Volcengine",
        apiEndpoint = "https://ark.cn-beijing.volces.com/api/v3",
        models = listOf("doubao-vision-pro-32k", "doubao-pro-32k"),
        supportsVision = true
    )

    val ALL_PROVIDERS = listOf(OPENAI, ANTHROPIC, OPENROUTER, VOLCENGINE)

    fun getById(id: String): AiProviderConfig? {
        return ALL_PROVIDERS.find { it.id == id }
    }
}
