package com.alcedo.studio.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.domain.service.GpuService
import com.alcedo.studio.domain.service.PresetService
import com.alcedo.studio.ndk.AlcedoNativeBridge
import com.alcedo.studio.ndk.NdkSafeCall
import com.alcedo.studio.privacy.PrivacyManager
import com.alcedo.studio.security.TempFileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings ViewModel. Manages privacy/feature toggles (persisted via
 * [PrivacyManager]), AI readiness reporting, native/GPU diagnostics, cache
 * management and built-in preset restoration. Privacy state is exposed
 * reactively so the Settings screen recomposes as the user flips toggles.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val privacyManager: PrivacyManager,
    private val tempFileManager: TempFileManager,
    private val gpuService: GpuService,
    private val presetService: PresetService,
) : ViewModel() {

    data class SettingsUiState(
        val privacy: PrivacyManager.PrivacyState? = null,
        val nativeAvailable: Boolean = false,
        val nativeVersion: String = "",
        val gpuAvailable: Boolean = false,
        val cacheSizeBytes: Long = 0L,
        val isClearingCache: Boolean = false,
        val isRestoringPresets: Boolean = false,
        val message: String? = null,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Reactively mirror the persisted privacy state into UI state.
        viewModelScope.launch {
            privacyManager.state.collect { state ->
                _uiState.update { it.copy(privacy = state) }
            }
        }
        // Snapshot native/GPU diagnostics once.
        val nativeOk = NdkSafeCall.isAvailable
        _uiState.update {
            it.copy(
                nativeAvailable = nativeOk,
                nativeVersion = if (nativeOk) gpuService.nativeVersion() else "unavailable",
                gpuAvailable = gpuService.isAvailable(),
            )
        }
        refreshCacheSize()
    }

    // ---- Privacy toggles -------------------------------------------------

    fun setCloudLlmAllowed(allowed: Boolean) = viewModelScope.launch {
        runCatching { privacyManager.setCloudLlmAllowed(allowed) }
            .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
    }

    fun setOnDeviceAiAllowed(allowed: Boolean) = viewModelScope.launch {
        runCatching { privacyManager.setOnDeviceAiAllowed(allowed) }
            .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
    }

    fun setTelemetryAllowed(allowed: Boolean) = viewModelScope.launch {
        runCatching { privacyManager.setTelemetryAllowed(allowed) }
            .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
    }

    fun setConsent(given: Boolean) = viewModelScope.launch {
        runCatching { privacyManager.setConsent(given) }
            .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
    }

    // ---- Cache management ------------------------------------------------

    fun clearCache() {
        if (_uiState.value.isClearingCache) return
        _uiState.update { it.copy(isClearingCache = true) }
        viewModelScope.launch {
            runCatching {
                tempFileManager.cleanupAll()
                gpuService.onLowMemory()
                NdkSafeCall.run { AlcedoNativeBridge.nativeOnLowMemory() }
            }.onSuccess {
                refreshCacheSize()
                _uiState.update { it.copy(isClearingCache = false, message = "Cache cleared") }
            }.onFailure { e ->
                _uiState.update { it.copy(isClearingCache = false, error = e.message) }
            }
        }
    }

    fun sweepOrphans() = viewModelScope.launch {
        runCatching { tempFileManager.sweepOrphans() }
            .onSuccess { refreshCacheSize() }
            .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
    }

    private fun refreshCacheSize() = viewModelScope.launch {
        runCatching { tempFileManager.usedBytes() }
            .onSuccess { bytes -> _uiState.update { it.copy(cacheSizeBytes = bytes) } }
    }

    // ---- Presets ---------------------------------------------------------

    fun restoreBuiltInPresets() {
        if (_uiState.value.isRestoringPresets) return
        _uiState.update { it.copy(isRestoringPresets = true) }
        viewModelScope.launch {
            runCatching { presetService.ensureBuiltIns() }
                .onSuccess { _uiState.update { it.copy(isRestoringPresets = false, message = "Default presets restored") } }
                .onFailure { e -> _uiState.update { it.copy(isRestoringPresets = false, error = e.message) } }
        }
    }

    // ---- Misc ------------------------------------------------------------

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
