package com.alcedo.studio.domain.service

import android.content.Context
import com.alcedo.studio.data.model.AiCredential
import com.alcedo.studio.data.model.AiProviderProfile

class AiCredentialStore(private val context: Context) {

    suspend fun saveCredential(credential: AiCredential) {}

    suspend fun loadCredentials(): List<AiCredential> = emptyList()

    suspend fun deleteCredential(credentialId: String) {}

    suspend fun saveProviderProfile(profile: AiProviderProfile) {}

    suspend fun loadProviderProfiles(): List<AiProviderProfile> = emptyList()

    suspend fun deleteProviderProfile(profileId: String) {}
}
