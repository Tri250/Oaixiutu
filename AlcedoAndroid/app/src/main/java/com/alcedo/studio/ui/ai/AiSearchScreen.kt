package com.alcedo.studio.ui.ai

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alcedo.studio.data.model.*
import com.alcedo.studio.di.AppModule
import com.alcedo.studio.domain.service.ClipInferenceEngine
import com.alcedo.studio.domain.service.SearchQueryClassifier
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSearchScreen(navController: NavController) {
    val aiService = AppModule.aiService
    val searchService = AppModule.searchService
    val classifier = remember { SearchQueryClassifier() }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<SearchResult>()) }
    var isSearching by remember { mutableStateOf(false) }
    var semanticEnabled by remember { mutableStateOf(true) }
    var classifiedType by remember { mutableStateOf<QueryType?>(null) }
    var activeModel by remember { mutableStateOf(aiService.getActiveModel()) }
    var indexedCount by remember { mutableIntStateOf(0) }
    var selectedLabels by remember { mutableStateOf<Set<String>>(emptySet()) }
    var allLabels by remember { mutableStateOf<List<String>>(emptyList()) }
    var showLabelFilter by remember { mutableStateOf(false) }

    // Model loading status from ClipInferenceEngine
    var modelLoadStatus by remember { mutableStateOf(aiService.getModelLoadStatus()) }
    var embeddingDim by remember { mutableIntStateOf(aiService.getClipEngine().embeddingDim) }

    val scope = rememberCoroutineScope()

    // Refresh model status and labels
    LaunchedEffect(Unit) {
        indexedCount = aiService.getIndexedCount()
        allLabels = aiService.getAllUniqueLabels()
        activeModel = aiService.getActiveModel()
        modelLoadStatus = aiService.getModelLoadStatus()
        embeddingDim = aiService.getClipEngine().embeddingDim
    }

    // Observe model load status changes
    LaunchedEffect(Unit) {
        aiService.modelLoadStatus.collect { status ->
            modelLoadStatus = status
        }
    }

    // Observe embedding dimension changes
    LaunchedEffect(Unit) {
        aiService.embeddingDimension.collect { dim ->
            embeddingDim = dim
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 语义搜索") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("ai_model_manager") }) {
                        Icon(Icons.Default.Settings, contentDescription = "模型管理")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Model status indicator with loading state and embedding dimension
            ModelStatusBar(
                activeModel = activeModel,
                modelLoadStatus = modelLoadStatus,
                embeddingDim = embeddingDim,
                indexedCount = indexedCount,
                onRefresh = {
                    scope.launch {
                        indexedCount = aiService.getIndexedCount()
                        activeModel = aiService.getActiveModel()
                        modelLoadStatus = aiService.getModelLoadStatus()
                        embeddingDim = aiService.getClipEngine().embeddingDim
                    }
                },
                onLoadModel = {
                    scope.launch {
                        val modelId = activeModel?.modelId ?: return@launch
                        aiService.loadModel(modelId)
                        modelLoadStatus = aiService.getModelLoadStatus()
                        embeddingDim = aiService.getClipEngine().embeddingDim
                    }
                }
            )

            // Search input
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.length >= 3) {
                        val searchQuery = classifier.classify(it)
                        classifiedType = searchQuery.classifiedType
                    } else {
                        classifiedType = null
                    }
                },
                label = { Text("描述你想找的照片...") },
                placeholder = { Text("例如：日落时分的海滩、f/1.4 人像、DSC_0001") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            isSearching = true
                            try {
                                val searchResults = aiService.searchByText(
                                    query = query,
                                    topK = 20
                                )
                                results = searchResults
                            } finally {
                                isSearching = false
                            }
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                },
                leadingIcon = {
                    classifiedType?.let { QueryTypeBadge(it) }
                }
            )

            // Search toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = semanticEnabled,
                    onClick = { semanticEnabled = !semanticEnabled },
                    label = { Text("语义搜索") },
                    leadingIcon = if (semanticEnabled) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )

                FilterChip(
                    selected = showLabelFilter,
                    onClick = { showLabelFilter = !showLabelFilter },
                    label = { Text("标签筛选") },
                    leadingIcon = if (showLabelFilter) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )

                Spacer(modifier = Modifier.weight(1f))

                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; results = emptyList(); classifiedType = null }) {
                        Icon(Icons.Default.Clear, contentDescription = "清除")
                    }
                }
            }

            // Label filter chips
            AnimatedVisibility(visible = showLabelFilter && allLabels.isNotEmpty()) {
                Column {
                    Text(
                        "AI 标签筛选",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allLabels.take(30)) { label ->
                            FilterChip(
                                selected = label in selectedLabels,
                                onClick = {
                                    selectedLabels = if (label in selectedLabels) {
                                        selectedLabels - label
                                    } else {
                                        selectedLabels + label
                                    }
                                },
                                label = { Text(label, maxLines = 1) }
                            )
                        }
                    }
                }
            }

            // Search results
            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("正在搜索...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (results.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${results.size} 个结果",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "相关度排序 · ${embeddingDim}d",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(results) { result ->
                        SearchResultCard(result = result)
                    }
                }
            } else if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "没有找到匹配的结果",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!semanticEnabled) {
                            Text(
                                "试试开启语义搜索？",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ImageSearch,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "输入自然语言描述来搜索照片",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "支持语义搜索、标签筛选、EXIF 查询",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        if (modelLoadStatus == ClipInferenceEngine.ModelStatus.LOADED) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "ONNX 模型已加载 · ${embeddingDim}d 向量",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Bottom info bar with model loading status and embedding dimensions
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusIcon = when (modelLoadStatus) {
                            ClipInferenceEngine.ModelStatus.LOADED -> Icons.Default.CheckCircle
                            ClipInferenceEngine.ModelStatus.LOADING -> Icons.Default.HourglassTop
                            ClipInferenceEngine.ModelStatus.FAILED -> Icons.Default.Error
                            else -> Icons.Default.CloudOff
                        }
                        Icon(
                            statusIcon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = when (modelLoadStatus) {
                                ClipInferenceEngine.ModelStatus.LOADED -> MaterialTheme.colorScheme.primary
                                ClipInferenceEngine.ModelStatus.LOADING -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            buildString {
                                append(activeModel?.modelName ?: "CLIP")
                                if (modelLoadStatus == ClipInferenceEngine.ModelStatus.LOADED) {
                                    append(" · ${embeddingDim}d")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${indexedCount} 张已索引",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelStatusBar(
    activeModel: AiModelProfile?,
    modelLoadStatus: ClipInferenceEngine.ModelStatus,
    embeddingDim: Int,
    indexedCount: Int,
    onRefresh: () -> Unit,
    onLoadModel: () -> Unit
) {
    val backgroundColor = when (modelLoadStatus) {
        ClipInferenceEngine.ModelStatus.LOADED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ClipInferenceEngine.ModelStatus.LOADING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        ClipInferenceEngine.ModelStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (modelLoadStatus) {
                    ClipInferenceEngine.ModelStatus.LOADED -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    ClipInferenceEngine.ModelStatus.LOADING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    ClipInferenceEngine.ModelStatus.FAILED -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buildString {
                        append(activeModel?.modelName ?: "无活动模型")
                        when (modelLoadStatus) {
                            ClipInferenceEngine.ModelStatus.LOADED -> append(" · 已加载")
                            ClipInferenceEngine.ModelStatus.LOADING -> append(" · 加载中...")
                            ClipInferenceEngine.ModelStatus.FAILED -> append(" · 加载失败")
                            else -> append(" · 未加载")
                        }
                        if (modelLoadStatus == ClipInferenceEngine.ModelStatus.LOADED) {
                            append(" · ${embeddingDim}d")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${indexedCount} 索引",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                if (modelLoadStatus == ClipInferenceEngine.ModelStatus.NOT_LOADED && activeModel != null) {
                    IconButton(
                        onClick = onLoadModel,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "加载模型",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "刷新",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QueryTypeBadge(queryType: QueryType) {
    val (label, color) = when (queryType) {
        QueryType.EXACT -> "精确" to MaterialTheme.colorScheme.primary
        QueryType.EXIF -> "EXIF" to MaterialTheme.colorScheme.tertiary
        QueryType.SEMANTIC -> "语义" to MaterialTheme.colorScheme.secondary
        QueryType.LABEL -> "标签" to MaterialTheme.colorScheme.error
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun SearchResultCard(result: SearchResult) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Image ${result.imageId}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Relevance score bar
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(80.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(result.score.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        result.score > 0.7f -> MaterialTheme.colorScheme.primary
                                        result.score > 0.4f -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                )
                        )
                    }
                    Text(
                        "%.1f%%".format(result.score * 100),
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            result.score > 0.7f -> MaterialTheme.colorScheme.primary
                            result.score > 0.4f -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (result.score > 0.7f) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
            ResultTypeBadge(result.resultType)
        }
    }
}

@Composable
private fun ResultTypeBadge(resultType: ResultType) {
    val (label, color) = when (resultType) {
        ResultType.EXACT -> "精确匹配" to MaterialTheme.colorScheme.primary
        ResultType.SEMANTIC -> "语义匹配" to MaterialTheme.colorScheme.secondary
        ResultType.EXIF -> "EXIF" to MaterialTheme.colorScheme.tertiary
        ResultType.LABEL -> "标签匹配" to MaterialTheme.colorScheme.error
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
