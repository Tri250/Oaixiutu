package com.alcedo.studio.domain.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * AI Sidecar runtime service.
 * Ported from desktop ai_sidecar_runtime_service.cpp
 * Manages the lifecycle of AI inference sessions, connects to
 * either local ONNX Runtime or remote AI Sidecar (gRPC).
 */
class AiSidecarRuntimeService(
    private val credentialStore: AiCredentialStore,
    private val clipEngine: ClipInferenceEngine
) {
    enum class RuntimeState { IDLE, INITIALIZING, READY, ERROR }

    private val _state = MutableStateFlow(RuntimeState.IDLE)
    val state: StateFlow<RuntimeState> = _state

    private val _activeProvider = MutableStateFlow<String?>(null)
    val activeProvider: StateFlow<String?> = _activeProvider

    suspend fun initialize(providerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _state.value = RuntimeState.INITIALIZING
            // Verify credentials
            val apiKey = credentialStore.getApiKey(providerId)
            if (apiKey.isNullOrBlank() && providerId != "local") {
                throw IllegalStateException("No API key for provider: $providerId")
            }
            // Initialize CLIP engine if local
            if (providerId == "local") {
                clipEngine.loadModel("mobileclip-s2")
                // Verify CLIP engine is ready after loading
                if (!clipEngine.isLoaded) {
                    throw IllegalStateException("CLIP engine failed to load model for local provider")
                }
            }
            _activeProvider.value = providerId
            _state.value = RuntimeState.READY
            Result.success(Unit)
        } catch (e: Exception) {
            _state.value = RuntimeState.ERROR
            Result.failure(e)
        }
    }

    fun shutdown() {
        clipEngine.unloadModel()
        _state.value = RuntimeState.IDLE
        _activeProvider.value = null
    }

    fun isReady(): Boolean = _state.value == RuntimeState.READY

    fun getActiveProvider(): String? = _activeProvider.value
}
