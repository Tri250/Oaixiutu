package com.alcedo.studio.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.EmptyState
import com.alcedo.studio.ui.common.ErrorDialog
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * AI semantic search screen. Free-text query field that runs the hybrid
 * CLIP + structured search and renders ranked results in a grid. Tapping a
 * result opens the image in the editor via [onOpenImage].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSearchScreen(
    onBack: () -> Unit,
    onOpenImage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiSearchViewModel = hiltViewModel(),
) {
    val s = Strings.res
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AlcedoColors.Charcoal,
                    titleContentColor = AlcedoColors.TextPrimary,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.spacingLg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::updateQuery,
                    placeholder = { Text(s.aiSearchHint) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::search, enabled = !state.isSearching) {
                    Text(s.search, color = AlcedoColors.AccentBlue)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isSearching -> {
                        CircularProgressIndicator(
                            color = AlcedoColors.AccentBlue,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    state.results.isEmpty() -> {
                        EmptyState(
                            title = s.aiSearch,
                            subtitle = s.aiSearchHint,
                            icon = Icons.Outlined.Search,
                            modifier = Modifier.align(Alignment.Center),
                        )
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
private fun SearchCard(
    result: AiSearchViewModel.SearchResult,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = AlcedoColors.SurfaceRaised),
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXxs),
        ) {
            Text(
                text = result.image.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = AlcedoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "%.0f%% match".format(result.score * 100f),
                style = MaterialTheme.typography.labelSmall,
                color = if (result.fromSemantic) AlcedoColors.AccentBlue else AlcedoColors.Amber,
            )
            if (result.matchedTags.isNotEmpty()) {
                Text(
                    text = result.matchedTags.take(3).joinToString("·"),
                    style = MaterialTheme.typography.labelSmall,
                    color = AlcedoColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
