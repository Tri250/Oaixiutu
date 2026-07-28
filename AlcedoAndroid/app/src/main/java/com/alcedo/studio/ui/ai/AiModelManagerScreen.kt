package com.alcedo.studio.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alcedo.studio.data.model.AiModelKind
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.ErrorDialog
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * AI model manager screen. Lists the downloadable models from the catalogue
 * with their download/load status and size, plus download/delete actions. The
 * default CLIP/SigLIP model is flagged for semantic search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiModelManagerViewModel = hiltViewModel(),
) {
    val s = Strings.res
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AlcedoColors.SurfaceBase,
        topBar = {
            TopAppBar(
                title = { Text(s.aiModels, color = AlcedoColors.TextPrimary) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(DesignTokens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            items(state.models, key = { it.asset.id }) { entry ->
                ModelCard(
                    entry = entry,
                    onDownload = { viewModel.download(entry.asset) },
                    onDelete = { viewModel.delete(entry.asset) },
                )
            }
        }
    }

    state.error?.let { err ->
        ErrorDialog(title = s.error, message = err, onDismiss = viewModel::dismissError)
    }
}

@Composable
private fun ModelCard(
    entry: AiModelManagerViewModel.ModelEntry,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = Strings.res
    val asset = entry.asset
    Card(
        colors = CardDefaults.cardColors(containerColor = AlcedoColors.SurfaceRaised),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(DesignTokens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(AlcedoColors.SurfaceElevated, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = kindLabel(asset.kind).take(1),
                        style = MaterialTheme.typography.titleSmall,
                        color = AlcedoColors.AccentBlue,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = AlcedoColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${kindLabel(asset.kind)} · ${formatBytes(asset.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AlcedoColors.TextTertiary,
                    )
                }
                if (entry.isLoaded) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = s.modelDownloaded,
                        tint = AlcedoColors.Success,
                        modifier = Modifier.size(20.dp),
                    )
                } else if (entry.isDownloading) {
                    CircularProgressIndicator(
                        color = AlcedoColors.AccentBlue,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            Text(
                text = asset.description,
                style = MaterialTheme.typography.bodySmall,
                color = AlcedoColors.TextSecondary,
            )

            if (entry.isDownloading) {
                LinearProgressIndicator(
                    progress = { 0.5f },
                    modifier = Modifier.fillMaxWidth(),
                    color = AlcedoColors.AccentBlue,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (!entry.isDownloaded && !entry.isDownloading) {
                    TextButton(onClick = onDownload) {
                        Icon(Icons.Outlined.Download, contentDescription = null, tint = AlcedoColors.AccentBlue)
                        Text(s.downloadModel, color = AlcedoColors.AccentBlue)
                    }
                } else if (entry.isDownloaded) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = AlcedoColors.Danger)
                        Text(s.deleteModel, color = AlcedoColors.Danger)
                    }
                }
            }
        }
    }
}

private fun kindLabel(kind: AiModelKind): String = when (kind) {
    AiModelKind.CLIP -> "CLIP"
    AiModelKind.SIGLIP -> "SigLIP"
    AiModelKind.MASK_SEGMENT -> "Segment"
    AiModelKind.LLM_PROXY -> "LLM"
    AiModelKind.IMAGE_CAPTIONER -> "Caption"
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb >= 1024) "%.2f GB".format(mb / 1024) else "%.0f MB".format(mb)
}
