package com.alcedo.studio.domain.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI credential management service. Wraps [AiCredentialStore] with reactive
 * state and provider-profile resolution, exposing the configured providers and
 * the active selection to the AI screens.
 */
@Singleton
class AiCredentialService @Inject constructor(
    private val store: AiCredentialStore,
) {

    data class CredentialState(
        val configuredProviderIds: Set<String> = emptySet(),
        val activeProviderId: String? = null,
    )

    private val _state = MutableStateFlow(refreshState())
    val state: StateFlow<CredentialState> = _state.asStateFlow()

    val isAvailable: Boolean get() = store.isAvailable

    /** Save an API key for [providerId] and mark it active. */
    fun saveCredential(providerId: String, apiKey: String) {
        store.setApiKey(providerId, apiKey)
        _state.value = refreshState().copy(activeProviderId = providerId)
    }

    /** Clear a provider's key. */
    fun clearCredential(providerId: String) {
        store.removeApiKey(providerId)
        val newState = refreshState()
        _state.value = if (newState.activeProviderId == providerId) {
            newState.copy(activeProviderId = newState.configuredProviderIds.firstOrNull())
        } else newState
    }

    fun setActiveProvider(providerId: String) {
        if (store.hasApiKey(providerId)) {
            _state.value = _state.value.copy(activeProviderId = providerId)
        }
    }

    fun getApiKey(providerId: String): String? = store.getApiKey(providerId)

    fun activeProfile(): AiProviderProfile? =
        _state.value.activeProviderId?.let { AiProviderProfiles.byId(it) }

    fun availableProfiles(): List<AiProviderProfile> = AiProviderProfiles.ALL

    /** True when at least one provider is ready to make requests. */
    fun hasActiveCredentials(): Boolean =
        _state.value.activeProviderId?.let { store.hasApiKey(it) } ?: false

    private fun refreshState(): CredentialState {
        val configured = store.configuredProviders()
        return CredentialState(configured, configured.firstOrNull())
    }
}
