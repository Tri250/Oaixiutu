package com.alcedo.studio.ui.ai

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alcedo.studio.data.model.*
import com.alcedo.studio.di.AppModule
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelManagerScreen(navController: NavController) {
    val modelDownloadService = AppModule.modelDownloadService
    val aiService = AppModule.aiService
    val models by modelDownloadService.models.collectAsState()
    var selectedModel by remember { mutableStateOf<ModelAsset?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var modelToDelete by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val storageUsage = remember { mutableLongStateOf(0L) }
    val availableStorage = remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        storageUsage.longValue = modelDownloadService.getStorageUsage()
        availableStorage.longValue = modelDownloadService.getAvailableStorage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 模型管理") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        modelDownloadService.refreshModelCatalog()
                        storageUsage.longValue = modelDownloadService.getStorageUsage()
                    }
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Storage usage card
            item {
                StorageUsageCard(
                    storageUsage = storageUsage.longValue,
                    availableStorage = availableStorage.longValue
                )
            }

            // Section header
            item {
                Text(
                    "可用模型",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Model list
            items(models) { model ->
                ModelCard(
                    model = model,
                    isExpanded = selectedModel?.modelId == model.modelId,
                    onToggleExpand = {
                        selectedModel = if (selectedModel?.modelId == model.modelId) null else model
                    },
                    onDownload = {
                        scope.launch {
                            modelDownloadService.downloadModel(model.modelId) { progress ->
                                // Progress updates via StateFlow
                            }
                            storageUsage.longValue = modelDownloadService.getStorageUsage()
                        }
                    },
                    onActivate = {
                        scope.launch {
                            modelDownloadService.activateModel(model.modelId)
                            aiService.activateModel(model.modelId)
                        }
                    },
                    onDeactivate = {
                        scope.launch {
                            modelDownloadService.deactivateModel(model.modelId)
                            aiService.deactivateModel(model.modelId)
                        }
                    },
                    onDelete = {
                        modelToDelete = model.modelId
                        showDeleteDialog = true
                    },
                    onCancelDownload = {
                        scope.launch {
                            modelDownloadService.cancelDownload(model.modelId)
                        }
                    }
                )
            }

            // Footer
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "模型存储在本地，支持离线推理。下载前请确保有足够的存储空间。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog && modelToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除模型") },
            text = { Text("确定要删除模型 \"${modelToDelete}\" 吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            modelToDelete?.let { modelDownloadService.deleteModel(it) }
                            storageUsage.longValue = modelDownloadService.getStorageUsage()
                        }
                        showDeleteDialog = false
                        modelToDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; modelToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun StorageUsageCard(storageUsage: Long, availableStorage: Long) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("存储空间", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${formatBytes(storageUsage)} / ${formatBytes(availableStorage + storageUsage)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = {
                    if (availableStorage + storageUsage > 0) {
                        (storageUsage.toFloat() / (availableStorage + storageUsage)).coerceIn(0f, 1f)
                    } else 0f
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "可用: ${formatBytes(availableStorage)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelAsset,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDownload: () -> Unit,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onDelete: () -> Unit,
    onCancelDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggleExpand
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            model.modelName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        if (model.isActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "已激活",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "${model.modelType.name} · v${model.version} · ${formatBytes(model.fileSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status badge
                DownloadStatusBadge(model.downloadStatus)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Download progress
            if (model.downloadStatus == ModelDownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { model.downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${(model.downloadProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Expanded details
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text("嵌入维度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${model.embeddingDim}", style = MaterialTheme.typography.bodySmall)
                        }
                        Column {
                            Text("最低SDK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("API ${model.minAndroidVersion}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (model.downloadStatus) {
                    ModelDownloadStatus.NOT_DOWNLOADED,
                    ModelDownloadStatus.FAILED,
                    ModelDownloadStatus.PAUSED -> {
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("下载", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    ModelDownloadStatus.DOWNLOADING -> {
                        OutlinedButton(
                            onClick = onCancelDownload,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("取消", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    ModelDownloadStatus.DOWNLOADED,
                    ModelDownloadStatus.VERIFIED -> {
                        if (!model.isActive) {
                            Button(
                                onClick = onActivate,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("激活", style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onDeactivate,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("停用", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    ModelDownloadStatus.ACTIVATED -> {
                        OutlinedButton(
                            onClick = onDeactivate,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("停用", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadStatusBadge(status: ModelDownloadStatus) {
    val (label, color) = when (status) {
        ModelDownloadStatus.NOT_DOWNLOADED -> "未下载" to MaterialTheme.colorScheme.onSurfaceVariant
        ModelDownloadStatus.DOWNLOADING -> "下载中" to MaterialTheme.colorScheme.primary
        ModelDownloadStatus.DOWNLOADED -> "已下载" to MaterialTheme.colorScheme.tertiary
        ModelDownloadStatus.VERIFIED -> "已验证" to MaterialTheme.colorScheme.primary
        ModelDownloadStatus.ACTIVATED -> "已激活" to MaterialTheme.colorScheme.primary
        ModelDownloadStatus.FAILED -> "失败" to MaterialTheme.colorScheme.error
        ModelDownloadStatus.PAUSED -> "已暂停" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}