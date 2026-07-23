package com.alcedo.studio.ui.ai

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alcedo.studio.data.model.AiModelType
import com.alcedo.studio.data.model.ModelAsset
import com.alcedo.studio.data.model.ModelDownloadStatus
import com.alcedo.studio.di.AppModule
import com.alcedo.studio.domain.service.ModelAssetCatalog
import com.alcedo.studio.domain.service.ModelDownloadService
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.EmptyState
import com.alcedo.studio.ui.common.LiquidGlassPanel
import com.alcedo.studio.ui.common.LiquidGlassSurface
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelManagerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val modelDownloadService = remember { AppModule.modelDownloadService }
    val modelAssetCatalog = remember { AppModule.modelAssetCatalog }

    // Observe the live model list from ModelDownloadService
    val serviceModels by modelDownloadService.models.collectAsState()

    // Map ModelAsset → display-friendly data
    var models by remember {
        mutableStateOf(serviceModels.map { it.toModelInfo() })
    }

    // Keep models in sync with service state
    LaunchedEffect(serviceModels) {
        models = serviceModels.map { it.toModelInfo() }
    }

    // 下载进度状态
    val downloadProgress = remember { mutableStateMapOf<String, Float>() }
    val downloadErrors = remember { mutableStateMapOf<String, String>() }
    // 下载协程任务，用于支持取消
    val downloadJobs = remember { mutableStateMapOf<String, Job>() }

    // Observe the service's download progress StateFlow for real-time updates
    val serviceDownloadProgress by modelDownloadService.downloadProgress.collectAsState()
    LaunchedEffect(serviceDownloadProgress) {
        serviceDownloadProgress.forEach { (modelId, progress) ->
            downloadProgress[modelId] = progress
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(downloadErrors.toMap()) {
        downloadErrors.values.lastOrNull()?.let { error ->
            snackbarHostState.showSnackbar(error)
            downloadErrors.clear()
        }
    }

    fun refreshModels() {
        val currentServiceModels = modelDownloadService.models.value
        models = currentServiceModels.map { it.toModelInfo() }
    }

    fun downloadModel(modelId: String) {
        if (downloadJobs.containsKey(modelId)) return
        val job = scope.launch(Dispatchers.IO) {
            try {
                downloadProgress[modelId] = 0f
                var retryCount = 0
                val maxRetries = 3
                var success = false
                while (!success && retryCount < maxRetries) {
                    try {
                        success = modelDownloadService.downloadModel(modelId) { progress ->
                            downloadProgress[modelId] = progress
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        retryCount++
                        if (retryCount >= maxRetries) throw e
                        delay(2000L * retryCount)
                    }
                }
                if (success) {
                    withContext(Dispatchers.Main) {
                        refreshModels()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户主动取消，不显示错误
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    downloadErrors[modelId] = e.message ?: "下载失败"
                }
            } finally {
                downloadProgress.remove(modelId)
                downloadJobs.remove(modelId)
            }
        }
        downloadJobs[modelId] = job
    }

    fun cancelDownload(modelId: String) {
        scope.launch {
            modelDownloadService.cancelDownload(modelId)
        }
        downloadJobs.remove(modelId)?.cancel()
        downloadProgress.remove(modelId)
    }

    fun deleteModel(modelId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                modelDownloadService.deleteModel(modelId)
                withContext(Dispatchers.Main) {
                    refreshModels()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    downloadErrors[modelId] = e.message ?: "删除失败"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes { aiModelManager }) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringRes { back })
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header info
            item {
                LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringRes { aiModelPrivacy }, style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            stringRes { aiModelPrivacyDesc },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val totalSize = models.sumOf { if (it.isDownloaded) it.sizeMB else 0 }
                        val downloadedCount = models.count { it.isDownloaded }
                        LinearProgressIndicator(
                            progress = { if (models.isNotEmpty()) downloadedCount.toFloat() / models.size else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringRes { aiModelDownloaded }.format(downloadedCount, models.size, totalSize),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Model items
            items(models, key = { it.id }) { model ->
                ModelCard(
                    model = model,
                    downloadProgress = downloadProgress[model.id],
                    isDownloading = downloadJobs.containsKey(model.id),
                    onDownload = { downloadModel(model.id) },
                    onCancelDownload = { cancelDownload(model.id) },
                    onDelete = { deleteModel(model.id) }
                )
            }

            // Usage stats
            item {
                Spacer(modifier = Modifier.height(8.dp))
                LiquidGlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringRes { aiModelUsage }, style = MaterialTheme.typography.titleSmall)
                        ModelUsageRow("CLIP 图像编码器", "语义搜索核心模型，将图片编码为语义向量")
                        ModelUsageRow("CLIP 文本编码器", "将文本查询编码为语义向量，与图片向量匹配")
                        ModelUsageRow("评分推荐模型", "基于图片质量分析，自动推荐最佳图片")
                    }
                }
            }
        }
    }
}

private fun ModelAsset.toModelInfo(): AiModelInfo =
    AiModelInfo(
        id = modelId,
        name = modelName,
        description = description,
        sizeMB = (fileSizeBytes / 1_000_000).toInt(),
        isDownloaded = downloadStatus == ModelDownloadStatus.DOWNLOADED
                || downloadStatus == ModelDownloadStatus.VERIFIED
                || downloadStatus == ModelDownloadStatus.ACTIVATED,
        isCorrupt = downloadStatus == ModelDownloadStatus.CORRUPT,
        isFailed = downloadStatus == ModelDownloadStatus.FAILED,
        version = version,
        downloadedVersion = downloadedVersion,
        isRequired = modelType == AiModelType.CLIP
    )

data class AiModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val sizeMB: Int,
    val isDownloaded: Boolean,
    val isCorrupt: Boolean = false,
    val isFailed: Boolean = false,
    val version: String = "1.0.0",
    val downloadedVersion: String = "",
    val isRequired: Boolean = false
)

@Composable
private fun ModelCard(
    model: AiModelInfo,
    downloadProgress: Float?,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Model icon
            val iconColor = when {
                model.isCorrupt -> MaterialTheme.colorScheme.error
                model.isFailed -> MaterialTheme.colorScheme.error
                model.isDownloaded -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val iconBackground = when {
                model.isCorrupt -> MaterialTheme.colorScheme.errorContainer
                model.isFailed -> MaterialTheme.colorScheme.errorContainer
                model.isDownloaded -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val icon = when {
                model.isCorrupt -> Icons.Default.Warning
                model.isFailed -> Icons.Default.ErrorOutline
                model.isDownloaded -> Icons.Default.CheckCircle
                else -> Icons.Default.Download
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = iconBackground,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Model info
            Column(modifier = Modifier.weight(1f)) {
                Text(model.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${model.sizeMB} MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (model.version.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "v${model.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (model.isRequired) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                stringRes { aiModelRequired },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                // Error / corrupt state message
                if (model.isCorrupt) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "模型文件已损坏，需要重新下载",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (model.isFailed) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "下载失败，请重试",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Download progress + cancel button
                if (isDownloading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val progress = downloadProgress
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.width(72.dp)
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = onCancelDownload,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(stringRes { cancel }, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action button
            val progress = downloadProgress
            if (model.isDownloaded) {
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !model.isRequired
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = stringRes { delete },
                        tint = if (model.isRequired) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.error
                    )
                }
            } else if (isDownloading) {
                Spacer(modifier = Modifier.width(0.dp))
            } else if (model.isCorrupt || model.isFailed) {
                // Retry button for corrupt or failed models
                FilledTonalButton(
                    onClick = onDownload,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("重试", style = MaterialTheme.typography.labelMedium)
                }
            } else if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.width(72.dp).padding(vertical = 4.dp)
                )
            } else {
                FilledTonalButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringRes { download }, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringRes { aiModelDeleteTitle }) },
            text = { Text(stringRes { aiModelDeleteMessage }.format(model.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringRes { delete }) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringRes { cancel }) }
            }
        )
    }
}

@Composable
private fun ModelUsageRow(name: String, desc: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
