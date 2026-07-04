package com.alcedo.studio.ui.common

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SearchTypeIndicator(val label: String, val icon: @Composable () -> Unit) {
    TRADITIONAL("Traditional", { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp)) }),
    SEMANTIC("Semantic", { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp)) }),
    LABEL("Label", { Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.size(14.dp)) }),
    EXACT("Exact", { Icon(Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(14.dp)) })
}

data class SearchSuggestion(
    val text: String,
    val type: SearchTypeIndicator = SearchTypeIndicator.TRADITIONAL
)

data class SearchResultItem(
    val imageId: Long,
    val imageName: String,
    val score: Float = 0f,
    val resultType: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchDialog(
    onDismiss: () -> Unit,
    onSearch: (String, SearchTypeIndicator) -> Unit,
    onResultClick: (Long) -> Unit,
    recentSearches: List<String> = emptyList(),
    suggestions: List<SearchSuggestion> = emptyList(),
    results: List<SearchResultItem> = emptyList(),
    isSearching: Boolean = false,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var selectedSearchType by remember { mutableStateOf(SearchTypeIndicator.TRADITIONAL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
            ) {
                // Search input
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (it.length >= 2) {
                            onSearch(it, selectedSearchType)
                        }
                    },
                    placeholder = { Text("Search images...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.large
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Search type indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SearchTypeIndicator.entries.forEach { type ->
                        FilterChip(
                            selected = selectedSearchType == type,
                            onClick = {
                                selectedSearchType = type
                                if (query.length >= 2) onSearch(query, type)
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    type.icon()
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(type.label, style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                }

                when {
                    results.isNotEmpty() -> {
                        // Results grid
                        Text(
                            "${results.size} results",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(results, key = { it.imageId }) { result ->
                                SearchResultCard(
                                    result = result,
                                    onClick = { onResultClick(result.imageId) }
                                )
                            }
                        }
                    }
                    query.isEmpty() -> {
                        // Recent searches & suggestions
                        if (recentSearches.isNotEmpty()) {
                            Text(
                                "Recent Searches",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            recentSearches.take(5).forEach { recent ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            query = recent
                                            onSearch(recent, selectedSearchType)
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        recent,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (suggestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Suggestions",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            suggestions.take(5).forEach { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            query = suggestion.text
                                            onSearch(suggestion.text, suggestion.type)
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    suggestion.type.icon()
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        suggestion.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(
                                        Icons.Default.NorthWest,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }

                        if (recentSearches.isEmpty() && suggestions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Type to search images",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun SearchResultCard(
    result: SearchResultItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box {
            // Thumbnail placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    result.imageName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            // Score badge
            if (result.score > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Text(
                        "%.0f%%".format(result.score * 100),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 9.sp
                    )
                }
            }

            // Name
            Text(
                result.imageName,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 9.sp
            )
        }
    }
}

