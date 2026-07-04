package com.alcedo.studio.domain.service

import android.content.Context
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AiCredentialService(private val context: Context) {

    private val _credentials = MutableStateFlow<List<AiCredential>>(emptyList())
    val credentials: StateFlow<List<AiCredential>> = _credentials.asStateFlow()

    private val _activeCredential = MutableStateFlow<AiCredential?>(null)
    val activeCredential: StateFlow<AiCredential?> = _activeCredential.asStateFlow()

    private val _providerProfiles = MutableStateFlow<List<AiProviderProfile>>(emptyList())
    val providerProfiles: StateFlow<List<AiProviderProfile>> = _providerProfiles.asStateFlow()

    suspend fun addCredential(credential: AiCredential): Boolean = false

    suspend fun removeCredential(credentialId: String) {}

    suspend fun setActiveCredential(credentialId: String): Boolean = false

    suspend fun updateCredential(credential: AiCredential): Boolean = false

    fun getActiveCredential(): AiCredential? = _activeCredential.value

    fun getCredential(credentialId: String): AiCredential? =
        _credentials.value.find { it.credentialId == credentialId }

    suspend fun loadCredentials() {}

    suspend fun saveCredentials() {}
}
