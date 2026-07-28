package com.alcedo.studio.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * User agreement / terms-of-service screen. Static legal text reached from
 * Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserAgreementScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    val scroll = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AlcedoColors.SurfaceBase,
        topBar = {
            TopAppBar(
                title = { Text(s.userAgreement, color = AlcedoColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AlcedoColors.Charcoal,
                    titleContentColor = AlcedoColors.TextPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(DesignTokens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
        ) {
            AgreementSection("1. License", """
                Alcedo Studio grants you a personal, non-exclusive license to use the
                application for editing photographs. You may not reverse-engineer,
                redistribute, or resell the software.
            """.trimIndent())
            AgreementSection("2. Acceptable Use", """
                You agree to use the app only for lawful purposes and to respect the
                intellectual property rights of others when editing and exporting images.
            """.trimIndent())
            AgreementSection("3. AI Features", """
                AI-assisted features (culling, semantic search, captioning) are provided
                as tools to assist your workflow. You remain responsible for the final
                selection and editing decisions.
            """.trimIndent())
            AgreementSection("4. Disclaimer", """
                The software is provided "as is" without warranty of any kind. The authors
                are not liable for any data loss or damage arising from the use of the
                application.
            """.trimIndent())
        }
    }
}

@Composable
private fun AgreementSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = AlcedoColors.AccentBlue)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextSecondary)
    }
}
