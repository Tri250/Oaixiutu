package com.alcedo.studio.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alcedo.studio.data.model.AiRating
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.EmptyState
import com.alcedo.studio.ui.common.ErrorDialog
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

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
    var showApplyDialog by remember { mutableStateOf(false) }

    BackHandler { onBack() }

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
                    IconButton(onClick = { viewModel.loadTopRated() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = s.back, tint = AlcedoColors.AccentBlue)
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
            // ---- Configuration card ----
            Card(
                colors = CardDefaults.cardColors(containerColor = AlcedoColors.SurfaceRaised),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.spacingLg),
            ) {
                Column(
                    modifier = Modifier.padding(DesignTokens.spacingLg),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
                ) {
                    // LLM Provider selection
                    Text(
                        text = s.llmProvider,
                        style = MaterialTheme.typography.labelMedium,
                        color = AlcedoColors.TextTertiary,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
                    ) {
                        LlmProvider.entries.forEach { provider ->
                            FilterChip(
                                selected = state.selectedProvider == provider,
                                onClick = { viewModel.setSelectedProvider(provider) },
                                label = {
                                    Text(
                                        provider.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                            )
                        }
                    }

                    // Strictness slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
                    ) {
                        Text(
                            text = s.strictnessGenerous,
                            style = MaterialTheme.typography.labelSmall,
                            color = AlcedoColors.TextTertiary,
                            modifier = Modifier.weight(1f),
                        )
                        Column(
                            modifier = Modifier.weight(3f),
                            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXxs),
                        ) {
                            Text(
                                text = "${s.strictness}: ${"%.0f%%".format(state.strictness * 100)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AlcedoColors.TextPrimary,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                            Slider(
                                value = state.strictness,
                                onValueChange = viewModel::setStrictness,
                                colors = SliderDefaults.colors(
                                    thumbColor = AlcedoColors.AccentBlue,
                                    activeTrackColor = AlcedoColors.AccentBlue,
                                ),
                            )
                        }
                        Text(
                            text = s.strictnessCritical,
                            style = MaterialTheme.typography.labelSmall,
                            color = AlcedoColors.TextTertiary,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Analyze button
                    Button(
                        onClick = { viewModel.analyzeSelected() },
                        enabled = !state.isCulling,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(s.analyzeImages, modifier = Modifier.padding(start = DesignTokens.spacingSm))
                    }
                }
            }

            // ---- Batch progress ----
            if (state.isCulling) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingSm),
                ) {
                    Text(
                        text = s.analyzingImages.format(state.culledCount, state.cullTotal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlcedoColors.TextSecondary,
                    )
                    LinearProgressIndicator(
                        progress = { if (state.cullTotal > 0) state.culledCount.toFloat() / state.cullTotal else 0f },
                        modifier = Modifier.fillMaxWidth().padding(top = DesignTokens.spacingXs),
                        color = AlcedoColors.AccentBlue,
                    )
                }
            }

            // ---- Results list ----
            if (state.topRated.isEmpty() && !state.isCulling) {
                EmptyState(
                    title = s.aiRating,
                    subtitle = s.analyzeHint,
                    icon = Icons.Outlined.AutoAwesome,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(DesignTokens.spacingSm),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
                    modifier = Modifier.weight(1f),
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

            // ---- Apply ratings button ----
            if (state.topRated.isNotEmpty() && !state.isCulling) {
                OutlinedButton(
                    onClick = { showApplyDialog = true },
                    enabled = !state.isApplyingRatings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(DesignTokens.spacingLg),
                ) {
                    if (state.isApplyingRatings) {
                        CircularProgressIndicator(
                            color = AlcedoColors.AccentBlue,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = AlcedoColors.AccentBlue)
                    }
                    Text(
                        text = if (state.isApplyingRatings) s.applying else s.applyRatingsToExif,
                        modifier = Modifier.padding(start = DesignTokens.spacingSm),
                        color = AlcedoColors.AccentBlue,
                    )
                }
            }
        }
    }

    // Detail dialog
    state.selectedDetail?.let { rating ->
        RatingDetailDialog(
            rating = rating,
            onDismiss = viewModel::dismissDetail,
        )
    }

    // Apply to EXIF confirmation dialog
    if (showApplyDialog) {
        AlertDialog(
            onDismissRequest = { showApplyDialog = false },
            title = { Text(s.applyRatingsToExif) },
            text = {
                Text(
                    s.applyRatingsConfirm.format(state.topRated.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlcedoColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showApplyDialog = false
                    viewModel.applyRatingsToExif()
                }) {
                    Text(s.confirm, color = AlcedoColors.AccentBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApplyDialog = false }) {
                    Text(s.cancel, color = AlcedoColors.TextSecondary)
                }
            },
        )
    }

    state.error?.let { err ->
        ErrorDialog(title = s.error, message = err, onDismiss = viewModel::dismissError)
    }

    state.message?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text(s.done) },
            text = { Text(msg, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMessage) {
                    Text(s.close, color = AlcedoColors.AccentBlue)
                }
            },
        )
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
    Card(
        colors = CardDefaults.cardColors(containerColor = AlcedoColors.SurfaceRaised),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
        ) {
            // Star rating circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(AlcedoColors.SurfaceElevated, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = rating?.stars()?.toString() ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                    color = AlcedoColors.Amber,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = entry.image.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlcedoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Quality flags row
                if (rating != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                        QualityFlag(label = s.focus, score = rating.sharpnessScore)
                        QualityFlag(label = s.motion, score = 1f - (rating.sharpnessScore * 0.3f))
                        QualityFlag(label = s.compShort, score = rating.compositionScore)
                    }
                }
                if (rating != null) {
                    LinearProgressIndicator(
                        progress = { rating.overallScore },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = AlcedoColors.AccentBlue,
                        trackColor = AlcedoColors.SurfaceElevated,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onClick) { Text(s.detail, color = AlcedoColors.TextSecondary) }
                TextButton(onClick = onOpen) { Text(s.edit, color = AlcedoColors.AccentBlue) }
            }
        }
    }
}

@Composable
private fun QualityFlag(label: String, score: Float) {
    val color = when {
        score >= 0.7f -> AlcedoColors.Success
        score >= 0.4f -> AlcedoColors.Warning
        else -> AlcedoColors.Danger
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AlcedoColors.TextTertiary,
        )
    }
}

@Composable
private fun RatingDetailDialog(rating: AiRating, onDismiss: () -> Unit) {
    val s = Strings.res
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.ratingDetails) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                ScoreRow(s.overallScore, rating.overallScore)
                ScoreRow(s.technicalScore, rating.technicalScore)
                ScoreRow(s.aestheticScore, rating.aestheticScore)
                ScoreRow(s.sharpnessScore, rating.sharpnessScore)
                ScoreRow(s.exposureScore, rating.exposureScore)
                ScoreRow(s.compositionScore, rating.compositionScore)

                // Star display
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = DesignTokens.spacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
                ) {
                    Text(s.suggestedRating, style = MaterialTheme.typography.bodySmall, color = AlcedoColors.TextTertiary)
                    repeat(5) { i ->
                        Icon(
                            Icons.Outlined.Star,
                            contentDescription = null,
                            tint = if (i < rating.stars()) AlcedoColors.Amber else AlcedoColors.TextDisabled,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Text(
                    text = rating.rationale,
                    style = MaterialTheme.typography.bodySmall,
                    color = AlcedoColors.TextSecondary,
                    modifier = Modifier.padding(top = DesignTokens.spacingSm),
                )

                // Quality flags
                Row(
                    modifier = Modifier.padding(top = DesignTokens.spacingSm),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
                ) {
                    QualityFlag(label = s.focus, score = rating.sharpnessScore)
                    QualityFlag(label = s.motionBlur, score = 1f - rating.sharpnessScore * 0.5f)
                    QualityFlag(label = s.compositionScore, score = rating.compositionScore)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(s.close, color = AlcedoColors.AccentBlue)
            }
        },
    )
}

@Composable
private fun ScoreRow(label: String, score: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = AlcedoColors.TextTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            "%.0f%%".format(score * 100f),
            style = MaterialTheme.typography.bodySmall,
            color = AlcedoColors.TextPrimary,
        )
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AlcedoColors.SurfaceElevated),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(AlcedoColors.AccentBlue)
                    .padding(end = ((1f - score) * 60).dp),
            )
        }
    }
}
