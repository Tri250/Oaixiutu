package com.alcedo.studio.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alcedo.studio.data.model.Project
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.AlcedoTheme
import com.alcedo.studio.ui.theme.DesignTokens
import dagger.hilt.android.AndroidEntryPoint

/**
 * Standalone "Manage Space" activity, reachable from the system storage
 * settings (android.intent.action.MANAGE_STORAGE). Shows the cache size and
 * lets the user clear caches and orphaned temp files to reclaim space. Uses
 * Compose for content and [SettingsViewModel] for the cache operations.
 */
@AndroidEntryPoint
class ManageSpaceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlcedoTheme {
                ManageSpaceContent(onFinish = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageSpaceContent(
    onFinish: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s = Strings.res
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AlcedoColors.SurfaceBase,
        topBar = {
            TopAppBar(
                title = { Text(s.manageSpace, color = AlcedoColors.TextPrimary) },
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
                .padding(DesignTokens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingLg),
        ) {
            Text(
                text = s.cacheSize + ": " + Project.formatBytes(state.cacheSizeBytes),
                style = MaterialTheme.typography.titleMedium,
                color = AlcedoColors.TextPrimary,
            )
            Text(
                text = "Clearing caches removes thumbnails, AI model caches and temporary render files. Your imported photos and edits are not affected.",
                style = MaterialTheme.typography.bodyMedium,
                color = AlcedoColors.TextSecondary,
            )
            Button(
                onClick = { viewModel.clearCache() },
                enabled = !state.isClearingCache,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(s.clearCache)
            }
            Button(
                onClick = { viewModel.sweepOrphans() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(s.sweepOrphans)
            }
            state.message?.let { msg ->
                Text(text = msg, color = AlcedoColors.Success, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(s.done)
            }
        }
    }
}
