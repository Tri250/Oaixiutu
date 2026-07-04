package com.alcedo.studio.domain.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

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
        _state.value = RuntimeState.READY
        _activeProvider.value = providerId
        Result.success(Unit)
    }

    fun shutdown() {
        _state.value = RuntimeState.IDLE
        _activeProvider.value = null
    }

    fun isReady(): Boolean = _state.value == RuntimeState.READY
    fun getActiveProvider(): String? = _activeProvider.value
}
