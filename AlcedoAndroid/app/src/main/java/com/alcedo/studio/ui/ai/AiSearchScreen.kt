package com.alcedo.studio.ui.ai

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.alcedo.studio.data.model.ImageModel
import com.alcedo.studio.data.model.RankedSearchResult
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.EmptyState
import com.alcedo.studio.ui.common.LiquidGlassPanel
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.ui.common.ShimmerEffect
import com.alcedo.studio.viewmodel.AlbumViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSearchScreen(
    navController: NavController,
    albumViewModel: AlbumViewModel = viewModel()
) {
    val searchQuery by albumViewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearching by albumViewModel.isSearching.collectAsStateWithLifecycle()
    val searchResults by albumViewModel.searchResults.collectAsStateWithLifecycle()
    val semanticEnabled by albumViewModel.semanticSearchEnabled.collectAsStateWithLifecycle()
    val images by albumViewModel.filteredImages.collectAsStateWithLifecycle()
    // 缩略图通过 LruCache 存储；订阅版本号触发重组，bitmap 直接访问
    @Suppress("UNUSED_VARIABLE") val thumbnailCacheVersion by albumViewModel.thumbnailCacheVersion.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var searchHistory by remember { mutableStateOf(loadSearchHistory()) }
    val context = LocalContext.current

    // Suggestions from AI
    val suggestions = remember {
        listOf(
            "日落风景", "人像特写", "城市夜景", "美食摄影",
            "街头纪实", "建筑线条", "花卉微距", "宠物",
            "黑白摄影", "长曝光", "逆光剪影", "冬日雪景",
            "汉服人像", "古镇风光", "烟花", "车展"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes { aiSearchTitle }) },
                actions = {
                    IconButton(onClick = { navController.navigate("ai_models") }) {
                        Icon(Icons.Default.ModelTraining, contentDescription = stringRes { navAiModels })
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Search input bar ──────────────────────────────────
            SearchBar(
                query = inputText,
                onQueryChange = { inputText = it },
                onSearch = {
                    if (inputText.isNotBlank()) {
                        albumViewModel.onSearchQueryChange(inputText)
                        searchHistory = saveSearchHistory(searchHistory, inputText)
                    }
                },
                active = false,
                onActiveChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = {
                    Text(if (semanticEnabled) "输入语义描述搜索..." else "输入文件名或关键词...")
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (inputText.isNotEmpty()) {
                        IconButton(onClick = {
                            inputText = ""
                            albumViewModel.clearSearch()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                    FilterChip(
                        selected = semanticEnabled,
                        onClick = { albumViewModel.toggleSemanticSearch() },
                        label = { Text("AI", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            ) {}

            // AI 快捷入口
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AiQuickActionCard(
                        icon = Icons.Default.AutoAwesome,
                        title = stringRes { navAiRating },
                        subtitle = "智能评分",
                        onClick = { navController.navigate("ai_rating") }
                    )
                }
                item {
                    AiQuickActionCard(
                        icon = Icons.Default.ModelTraining,
                        title = stringRes { navAiModels },
                        subtitle = "模型管理",
                        onClick = { navController.navigate("ai_models") }
                    )
                }
            }

            // ── Semantic mode indicator ───────────────────────────
            AnimatedVisibility(
                visible = semanticEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LiquidGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "语义搜索已启用 — 用自然语言描述图片内容，AI 将理解含义并匹配相似图片",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Content area ──────────────────────────────────────
            if (isSearching) {
                // Loading state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (semanticEnabled) "AI 正在理解语义..." else "搜索中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (searchQuery.isNotBlank() && images.isNotEmpty()) {
                // Search results
                Text(
                    "找到 ${images.size} 个结果",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(images, key = { it.imageId }) { image ->
                        SearchResultCard(
                            image = image,
                            thumbnailBitmap = albumViewModel.getThumbnail(image.imageId),
                            similarity = searchResults.find { it.imageId == image.imageId }?.score,
                            onClick = { navController.navigate("editor/${image.imageId}") },
                            onLongClick = {
                                // 长按预览大图
                                navController.navigate("editor/${image.imageId}")
                            }
                        )
                    }
                }
            } else if (searchQuery.isNotBlank() && images.isEmpty()) {
                // No results
                EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "未找到匹配结果",
                    message = if (semanticEnabled) "尝试使用不同的描述词" else "尝试使用不同的关键词",
                    action = {
                        OutlinedButton(onClick = {
                            inputText = ""
                            albumViewModel.clearSearch()
                        }) {
                            Text("清除搜索")
                        }
                    }
                )
            } else {
                // Default: history + suggestions
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Search history
                    if (searchHistory.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("搜索历史", style = MaterialTheme.typography.titleSmall)
                                TextButton(onClick = {
                                    searchHistory = emptyList()
                                    clearSearchHistory(context)
                                }) {
                                    Text("清除", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        items(searchHistory, key = { it }) { query ->
                            ListItem(
                                headlineContent = { Text(query) },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.clickable {
                                    inputText = query
                                    albumViewModel.onSearchQueryChange(query)
                                }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }

                    // AI suggestions
                    item {
                        Text("智能推荐", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            suggestions.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = {
                                        inputText = suggestion
                                        albumViewModel.onSearchQueryChange(suggestion)
                                        searchHistory = saveSearchHistory(searchHistory, suggestion)
                                    },
                                    label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) },
                                    icon = {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // AI capabilities info
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        LiquidGlassPanel(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("AI 搜索能力", style = MaterialTheme.typography.titleSmall)
                                AiCapabilityRow(Icons.Default.Image, "语义搜索", "用自然语言描述图片内容")
                                AiCapabilityRow(Icons.Default.Label, "标签匹配", "自动识别图片中的场景和对象")
                                AiCapabilityRow(Icons.Default.Camera, "EXIF 搜索", "按相机、镜头、参数筛选")
                                AiCapabilityRow(Icons.Default.Star, "智能评分", "AI 分析图片质量并推荐")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiCapabilityRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SearchResultCard(
    image: ImageModel,
    thumbnailBitmap: android.graphics.Bitmap?,
    similarity: Float?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumbnailBitmap.asImageBitmap(),
                        contentDescription = image.imageName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        image.imageName.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }

            // Similarity badge
            if (similarity != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                ) {
                    Text(
                        "${(similarity * 100).toInt()}%",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Image name
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
                    .padding(horizontal = 4.dp, vertical = 3.dp)
            ) {
                Text(
                    image.imageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Search history persistence ──────────────────────────────
private const val HISTORY_PREFS = "ai_search_history"
private const val KEY_HISTORY = "search_queries"

private fun loadSearchHistory(): List<String> {
    return try {
        val prefs = com.alcedo.studio.di.AppModule.context.getSharedPreferences(HISTORY_PREFS, 0)
        prefs.getStringSet(KEY_HISTORY, emptySet())?.toList()?.reversed() ?: emptyList()
    } catch (_: Exception) { emptyList() }
}

private fun saveSearchHistory(current: List<String>, newQuery: String): List<String> {
    val updated = (listOf(newQuery) + current.filter { it != newQuery }).take(20)
    try {
        val prefs = com.alcedo.studio.di.AppModule.context.getSharedPreferences(HISTORY_PREFS, 0)
        prefs.edit().putStringSet(KEY_HISTORY, updated.toSet()).apply()
    } catch (_: Exception) {}
    return updated
}

private fun clearSearchHistory(context: android.content.Context) {
    try {
        context.getSharedPreferences(HISTORY_PREFS, 0).edit().clear().apply()
    } catch (_: Exception) {}
}

private fun android.graphics.Bitmap.asImageBitmap() = androidx.compose.ui.graphics.asImageBitmap()

@Composable
private fun AiQuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}
