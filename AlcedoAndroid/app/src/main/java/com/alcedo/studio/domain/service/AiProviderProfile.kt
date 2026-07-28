package com.alcedo.studio.domain.service

import kotlinx.serialization.Serializable

/**
 * Profile describing an AI provider (OpenAI-compatible / Anthropic / local).
 * Used by [AiCredentialService] and the LLM culling / image-analysis clients.
 */
@Serializable
data class AiProviderProfile(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val apiKind: ApiKind,
    val defaultModel: String,
    val supportsVision: Boolean,
    val supportsStreaming: Boolean,
    val maxTokens: Int,
    val rateLimitPerMinute: Int = 0,
    val description: String = "",
) {
    enum class ApiKind { OPENAI, ANTHROPIC, OLLAMA, CUSTOM }
}

/**
 * Catalogue of built-in provider profiles. Users can add custom profiles via
 * the AI model manager screen; the built-ins cover the common OpenAI-compatible
 * and local (Ollama) deployments.
 */
object AiProviderProfiles {

    val OPENAI = AiProviderProfile(
        id = "openai",
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        apiKind = AiProviderProfile.ApiKind.OPENAI,
        defaultModel = "gpt-4o",
        supportsVision = true,
        supportsStreaming = true,
        maxTokens = 4096,
        rateLimitPerMinute = 50,
        description = "OpenAI GPT models for culling and image analysis.",
    )

    val DEEPSEEK = AiProviderProfile(
        id = "deepseek",
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        apiKind = AiProviderProfile.ApiKind.OPENAI,
        defaultModel = "deepseek-chat",
        supportsVision = true,
        supportsStreaming = true,
        maxTokens = 4096,
        rateLimitPerMinute = 30,
    )

    val ANTHROPIC = AiProviderProfile(
        id = "anthropic",
        displayName = "Anthropic",
        baseUrl = "https://api.anthropic.com/v1",
        apiKind = AiProviderProfile.ApiKind.ANTHROPIC,
        defaultModel = "claude-3-5-sonnet-latest",
        supportsVision = true,
        supportsStreaming = true,
        maxTokens = 4096,
    )

    val OLLAMA_LOCAL = AiProviderProfile(
        id = "ollama",
        displayName = "Ollama (local)",
        baseUrl = "http://127.0.0.1:11434/v1",
        apiKind = AiProviderProfile.ApiKind.OLLAMA,
        defaultModel = "llava",
        supportsVision = true,
        supportsStreaming = true,
        maxTokens = 2048,
        description = "Local Ollama server for on-device LLM inference.",
    )

    val ALL: List<AiProviderProfile> = listOf(OPENAI, DEEPSEEK, ANTHROPIC, OLLAMA_LOCAL)

    fun byId(id: String): AiProviderProfile? = ALL.firstOrNull { it.id == id }
}
