package com.alcedo.studio.ui.ai

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.alcedo.studio.data.model.ImageModel
import com.alcedo.studio.domain.service.RankedSearchResult
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.EmptyState
import com.alcedo.studio.ui.common.LiquidGlassPanel
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.ui.theme.AlcedoGlass
import com.alcedo.studio.ui.theme.AlcedoStroke
import com.alcedo.studio.ui.theme.AlcedoRadius
import com.alcedo.studio.ui.theme.AlcedoSpacing
import com.alcedo.studio.viewmodel.AlbumViewModel
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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
    val aiModelLoaded = remember { mutableStateOf(com.alcedo.studio.di.AppModule.aiService.isModelLoaded()) }
    // 缩略图通过 LruCache 存储；订阅版本号触发重组，bitmap 直接访问
    @Suppress("UNUSED_VARIABLE") val thumbnailCacheVersion by albumViewModel.thumbnailCacheVersion.collectAsStateWithLifecycle()
    var showAiModelToast by remember { mutableStateOf(false) }

    var inputText by remember { mutableStateOf("") }
    var searchHistory by remember { mutableStateOf(loadSearchHistory()) }
    val context = LocalContext.current

    // 长按预览的图片状态
    var previewImage by remember { mutableStateOf<ImageModel?>(null) }

    // Suggestions from AI
    val suggestions = remember {
        listOf(
            "日落风景", "人像特写", "城市夜景", "美食摄影",
            "街头纪实", "建筑线条", "花卉微距", "宠物",
            "黑白摄影", "长曝光", "逆光剪影", "冬日雪景",
            "汉服人像", "古镇风光", "烟花", "车展"
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Show toast when AI model is not loaded
    LaunchedEffect(showAiModelToast) {
        if (showAiModelToast) {
            snackbarHostState.showSnackbar("请先下载AI模型")
            showAiModelToast = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val topBarBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = AlcedoGlass.borderAlpha)
            TopAppBar(
                title = {
                    Text(
                        stringRes { aiSearchTitle },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(onClick = { navController.navigate("ai_models") }) {
                        Icon(
                            Icons.Default.ModelTraining,
                            contentDescription = stringRes { navAiModels },
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = AlcedoGlass.toolbarOpacity)
                ),
                modifier = Modifier.drawWithContent {
                    drawContent()
                    drawLine(
                        color = topBarBorderColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = AlcedoStroke.thin.toPx()
                    )
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
                    Text(
                        if (semanticEnabled) "输入语义描述搜索..." else "输入文件名或关键词...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
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
                        onClick = {
                            if (!aiModelLoaded.value) {
                                showAiModelToast = true
                            } else {
                                albumViewModel.toggleSemanticSearch()
                            }
                        },
                        label = {
                            Text(
                                "AI",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        shape = RoundedCornerShape(AlcedoRadius.full),
                        modifier = Modifier.padding(start = AlcedoSpacing.xs)
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
                        subtitle = if (aiModelLoaded.value) "智能评分" else "智能评分(本地模式)",
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
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
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
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (semanticEnabled) "AI 正在理解语义..." else "搜索中...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
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
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(images, key = { it.imageId }) { image ->
                        SearchResultCard(
                            image = image,
                            thumbnailBitmap = albumViewModel.getThumbnail(image.imageId),
                            similarity = searchResults.find { it.imageId == image.imageId }?.score,
                            onClick = { navController.navigate("editor/${image.imageId}") },
                            onLongClick = {
                                // 长按预览大图，不再跳转到编辑器
                                previewImage = image
                            },
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            } else if (searchQuery.isNotBlank() && images.isEmpty()) {
                // No results
                EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "未找到匹配结果",
                    message = if (semanticEnabled)
                        "AI 未找到匹配的图片，可尝试更换描述词或开启标签匹配模式"
                    else
                        "没有匹配该关键词的图片，可尝试启用 AI 语义搜索，用自然语言描述内容",
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
                                Text(
                                    "搜索历史",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
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
                                modifier = Modifier
                                    .animateItemPlacement()
                                    .clickable {
                                        inputText = query
                                        albumViewModel.onSearchQueryChange(query)
                                    }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }

                    // AI suggestions
                    item {
                        Text(
                            "智能推荐",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
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
                                    label = {
                                        Text(
                                            suggestion,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    shape = RoundedCornerShape(50),
                                    icon = {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
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
                                Text(
                                    "AI 搜索能力",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                AiCapabilityRow(Icons.Default.Image, "语义搜索", "用自然语言描述图片内容", enabled = aiModelLoaded.value)
                                AiCapabilityRow(Icons.Default.Label, "标签匹配", "自动识别图片中的场景和对象", enabled = aiModelLoaded.value)
                                AiCapabilityRow(Icons.Default.Camera, "EXIF 搜索", "按相机、镜头、参数筛选", enabled = true)
                                AiCapabilityRow(Icons.Default.Star, "智能评分", "AI 分析图片质量并推荐", enabled = true)
                            }
                        }
                    }
                }
            }
        }

        // 长按预览对话框
        previewImage?.let { image ->
            AlertDialog(
                onDismissRequest = { previewImage = null },
                title = {
                    Text(
                        image.imageName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                text = {
                    val previewBitmap = albumViewModel.getThumbnail(image.imageId)
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = image.imageName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                image.imageName.take(1).uppercase(),
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        previewImage = null
                        navController.navigate("editor/${image.imageId}")
                    }) { Text("编辑") }
                },
                dismissButton = {
                    TextButton(onClick = { previewImage = null }) {
                        Text(stringRes { cancel })
                    }
                }
            )
        }
    }
}

@Composable
private fun AiCapabilityRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!enabled) Modifier.alpha(0.5f) else Modifier)
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        if (!enabled) {
            Text(
                "需下载模型",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium
            )
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
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 6.dp)
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

            // Similarity badge — 半透明匹配度百分比
            if (similarity != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                ) {
                    Text(
                        "${(similarity * 100).toInt()}%",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
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
                    .padding(horizontal = 6.dp, vertical = 4.dp)
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
// 使用 JSONArray 替代 StringSet 保留插入顺序（StringSet 不保证顺序，会导致历史排序错乱）
private const val HISTORY_PREFS = "ai_search_history"
private const val KEY_HISTORY = "search_queries_json"
private const val MAX_HISTORY_SIZE = 20

private fun loadSearchHistory(): List<String> {
    return try {
        val prefs = com.alcedo.studio.di.AppModule.context.getSharedPreferences(HISTORY_PREFS, 0)
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val array = JSONArray(json)
        val result = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            array.optString(i).takeIf { it.isNotEmpty() }?.let { result.add(it) }
        }
        result
    } catch (_: Exception) { emptyList() }
}

private fun saveSearchHistory(current: List<String>, newQuery: String): List<String> {
    // 去重后置顶，限制最大条数，保持插入顺序
    val updated = (listOf(newQuery) + current.filter { it != newQuery }).take(MAX_HISTORY_SIZE)
    try {
        val prefs = com.alcedo.studio.di.AppModule.context.getSharedPreferences(HISTORY_PREFS, 0)
        val array = JSONArray()
        updated.forEach { array.put(it) }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    } catch (_: Exception) {}
    return updated
}

private fun clearSearchHistory(context: android.content.Context) {
    try {
        context.getSharedPreferences(HISTORY_PREFS, 0).edit().remove(KEY_HISTORY).apply()
    } catch (_: Exception) {}
}

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
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
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
