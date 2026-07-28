package com.alcedo.studio.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.EmptyState
import com.alcedo.studio.ui.common.ErrorDialog
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * AI culling/rating screen. Lists the top-rated images with their score bars
 * and suggested star rating. A "Cull all" action runs the batch rater over the
 * unrated catalogue; tapping an entry shows its detailed score breakdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRatingScreen(
    onBack: () -> Unit,
    onOpenImage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiRatingViewModel = hiltViewModel(),
) {
    val s = Strings.res
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AlcedoColors.SurfaceBase,
        topBar = {
            TopAppBar(
                title = { Text(s.aiRating, color = AlcedoColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.loadTopRated() }) {
                        Text("↻", color = AlcedoColors.AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AlcedoColors.Charcoal,
                    titleContentColor = AlcedoColors.TextPrimary,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isCulling) {
                Column(modifier = Modifier.fillMaxWidth().padding(DesignTokens.spacingLg)) {
                    Text(
                        text = "${s.aiCulling} (${state.culledCount}/${state.cullTotal})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlcedoColors.TextSecondary,
                    )
                    LinearProgressIndicator(
                        progress = { if (state.cullTotal > 0) state.culledCount.toFloat() / state.cullTotal else 0f },
                        modifier = Modifier.fillMaxWidth().padding(top = DesignTokens.spacingSm),
                        color = AlcedoColors.AccentBlue,
                    )
                }
            }

            if (state.topRated.isEmpty() && !state.isCulling) {
                EmptyState(
                    title = s.aiRating,
                    subtitle = s.noIssues,
                    icon = Icons.Outlined.AutoAwesome,
                    actionText = s.aiCulling,
                    onAction = { /* host triggers batch */ },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(DesignTokens.spacingSm),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.topRated, key = { it.image.id }) { entry ->
                        RatingRow(
                            entry = entry,
                            onClick = { viewModel.showDetail(entry.image.id) },
                            onOpen = { onOpenImage(entry.image.id) },
                        )
                    }
                }
            }
        }
    }

    // Detail dialog
    state.selectedDetail?.let { rating ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDetail,
            title = { Text(s.aiRating) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                    ScoreRow(s.overallScore, rating.overallScore)
                    ScoreRow(s.technicalScore, rating.technicalScore)
                    ScoreRow(s.aestheticScore, rating.aestheticScore)
                    ScoreRow(s.sharpnessScore, rating.sharpnessScore)
                    ScoreRow(s.exposureScore, rating.exposureScore)
                    ScoreRow(s.compositionScore, rating.compositionScore)
                    Text(
                        text = rating.rationale,
                        style = MaterialTheme.typography.bodySmall,
                        color = AlcedoColors.TextSecondary,
                        modifier = Modifier.padding(top = DesignTokens.spacingSm),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDetail) {
                    Text(s.close, color = AlcedoColors.AccentBlue)
                }
            },
        )
    }

    state.error?.let { err ->
        ErrorDialog(title = s.error, message = err, onDismiss = viewModel::dismissError)
    }
}

@Composable
private fun RatingRow(
    entry: AiRatingViewModel.RatingEntry,
    onClick: () -> Unit,
    onOpen: () -> Unit,
) {
    val s = Strings.res
    val rating = entry.rating
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlcedoColors.SurfaceRaised, MaterialTheme.shapes.small)
            .padding(DesignTokens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(AlcedoColors.SurfaceElevated, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = rating?.stars()?.toString() ?: "—",
                style = MaterialTheme.typography.titleSmall,
                color = AlcedoColors.Amber,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = entry.image.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = AlcedoColors.TextPrimary,
                maxLines = 1,
            )
            if (rating != null) {
                LinearProgressIndicator(
                    progress = { rating.overallScore },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = AlcedoColors.AccentBlue,
                )
            }
        }
        TextButton(onClick = onClick) { Text(s.issues, color = AlcedoColors.TextSecondary) }
        TextButton(onClick = onOpen) { Text(s.edit, color = AlcedoColors.AccentBlue) }
    }
}

@Composable
private fun ScoreRow(label: String, score: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = AlcedoColors.TextTertiary, modifier = Modifier.weight(1f))
        Text(
            "%.0f%%".format(score * 100f),
            style = MaterialTheme.typography.bodySmall,
            color = AlcedoColors.TextPrimary,
        )
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 4.dp)
                .background(AlcedoColors.SurfaceElevated),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AlcedoColors.AccentBlue)
                    .padding(end = ((1f - score) * 60).dp),
            )
        }
    }
}
