package com.alcedo.studio.ui.ai

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.EmptyState
import com.alcedo.studio.ui.common.ErrorDialog
import com.alcedo.studio.ui.common.ShimmerBox
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

private val SEARCH_SUGGESTIONS = listOf(
    "sunset at the beach",
    "portrait with shallow depth of field",
    "city skyline at night",
    "snowy mountain landscape",
    "food photography",
    "street photography",
    "wildlife close-up",
    "architecture details",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiSearchScreen(
    onBack: () -> Unit,
    onOpenImage: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenModels: () -> Unit = {},
    viewModel: AiSearchViewModel = hiltViewModel(),
) {
    val s = Strings.res
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler { onBack() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AlcedoColors.SurfaceBase,
        topBar = {
            TopAppBar(
                title = { Text(s.aiSearch, color = AlcedoColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenModels) {
                        Icon(Icons.Outlined.Settings, contentDescription = s.settings, tint = AlcedoColors.TextTertiary)
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
            // ---- Search bar ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingMd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::updateQuery,
                    placeholder = {
                        Text(
                            text = "Type a description like 'sunset at the beach'",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlcedoColors.TextDisabled,
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = AlcedoColors.TextTertiary)
                    },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = viewModel::clearQuery) {
                                Icon(Icons.Outlined.Close, contentDescription = s.cancel, tint = AlcedoColors.TextTertiary)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                )
                TextButton(onClick = viewModel::search, enabled = !state.isSearching && state.query.isNotBlank()) {
                    Text(s.search, color = AlcedoColors.AccentBlue)
                }
            }

            // ---- Model status indicator ----
            ModelStatusBar(
                isReady = state.modelStatus.isReady,
                isDownloading = state.modelStatus.isDownloading,
                modelName = state.modelStatus.modelName,
            )

            // ---- Suggestions / recent searches (shown when idle) ----
            AnimatedVisibility(
                visible = state.results.isEmpty() && !state.isSearching && state.query.isBlank(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.spacingLg),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
                ) {
                    // Recent searches
                    if (state.recentSearches.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Recent Searches",
                                style = MaterialTheme.typography.labelMedium,
                                color = AlcedoColors.TextTertiary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = viewModel::clearRecentSearches) {
                                Text(s.clearSelection, style = MaterialTheme.typography.labelSmall, color = AlcedoColors.TextTertiary)
                            }
                        }
                        state.recentSearches.forEach { recent ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(DesignTokens.radiusSm))
                                    .clickable { viewModel.searchRecent(recent) }
                                    .padding(vertical = DesignTokens.spacingXs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
                            ) {
                                Icon(
                                    Icons.Outlined.History,
                                    contentDescription = null,
                                    tint = AlcedoColors.TextTertiary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(recent, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextSecondary)
                            }
                        }
                    }

                    // Search suggestions
                    Text(
                        text = "Suggestions",
                        style = MaterialTheme.typography.labelMedium,
                        color = AlcedoColors.TextTertiary,
                        modifier = Modifier.padding(top = DesignTokens.spacingSm),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
                        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
                    ) {
                        SEARCH_SUGGESTIONS.forEach { suggestion ->
                            AssistChip(
                                onClick = { viewModel.searchRecent(suggestion) },
                                label = {
                                    Text(
                                        suggestion,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AlcedoColors.TextSecondary,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // ---- Results area ----
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isSearching -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(DesignTokens.spacingLg),
                            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
                        ) {
                            LinearProgressIndicator(
                                color = AlcedoColors.AccentBlue,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            // Shimmer placeholders
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(120.dp),
                                verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
                                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(6) {
                                    ShimmerBox(modifier = Modifier.height(100.dp).fillMaxWidth())
                                }
                            }
                        }
                    }
                    state.results.isEmpty() && state.query.isNotBlank() -> {
                        EmptyState(
                            title = s.noSearchResults,
                            subtitle = "Try a different description",
                            icon = Icons.Outlined.ImageNotSupported,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    state.results.isEmpty() && state.query.isBlank() -> {
                        // Handled by suggestions above
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(120.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(DesignTokens.spacingSm),
                            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
                            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.results, key = { it.image.id }) { result ->
                                SearchCard(result = result, onClick = { onOpenImage(result.image.id) })
                            }
                        }
                    }
                }
            }
        }
    }

    state.error?.let { err ->
        ErrorDialog(
            title = s.error,
            message = err,
            onDismiss = viewModel::dismissError,
        )
    }
}

@Composable
private fun ModelStatusBar(isReady: Boolean, isDownloading: Boolean, modelName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlcedoColors.SurfaceRaised)
            .padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    when {
                        isReady -> AlcedoColors.Success
                        isDownloading -> AlcedoColors.Warning
                        else -> AlcedoColors.Danger
                    },
                    RoundedCornerShape(3.dp),
                ),
        )
        Text(
            text = when {
                isReady -> "$modelName • Ready"
                isDownloading -> "$modelName • Downloading…"
                else -> "$modelName • Not downloaded"
            },
            style = MaterialTheme.typography.labelSmall,
            color = AlcedoColors.TextTertiary,
        )
    }
}

@Composable
private fun SearchCard(
    result: AiSearchViewModel.SearchResult,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = AlcedoColors.SurfaceRaised),
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXxs),
        ) {
            // Thumbnail — prefer the decoded thumbnail path, fall back to the
            // original URI. Shows a monogram placeholder while loading.
            val thumbModel = result.image.thumbnailPath?.takeIf { it.isNotBlank() }
                ?: result.image.originalUri
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(DesignTokens.radiusSm))
                    .background(AlcedoColors.SurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(thumbModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = result.image.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = result.image.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = AlcedoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Relevance score
            val scorePercent = (result.score * 100f).toInt().coerceIn(0, 100)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXxs),
            ) {
                LinearProgressIndicator(
                    progress = { result.score.coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(3.dp),
                    color = if (result.fromSemantic) AlcedoColors.AccentBlue else AlcedoColors.Amber,
                    trackColor = AlcedoColors.SurfaceElevated,
                )
                Text(
                    text = "$scorePercent%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (result.fromSemantic) AlcedoColors.AccentBlue else AlcedoColors.Amber,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (result.matchedTags.isNotEmpty()) {
                Text(
                    text = result.matchedTags.take(3).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = AlcedoColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
