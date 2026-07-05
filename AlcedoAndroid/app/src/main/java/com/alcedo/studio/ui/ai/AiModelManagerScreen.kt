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
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.security.SecureHttpClient
import com.alcedo.studio.service.AiService
import com.alcedo.studio.ui.common.EmptyState
import com.alcedo.studio.ui.common.LiquidGlassPanel
import com.alcedo.studio.ui.common.LiquidGlassSurface
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class AiModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val sizeMB: Int,
    val isDownloaded: Boolean,
    val isRequired: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelManagerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Model list - CLIP, Rating, etc.
    var models by remember {
        mutableStateOf(getAvailableModels(context))
    }
    var isRefreshing by remember { mutableStateOf(false) }

    // 下载进度状态 — 使用 mutableStateMapOf 避免每次更新重新分配整个 Map
    val downloadProgress = remember { mutableStateMapOf<String, Float>() }
    val downloadErrors = remember { mutableStateMapOf<String, String>() }
    // 下载协程任务，用于支持取消
    val downloadJobs = remember { mutableStateMapOf<String, Job>() }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(downloadErrors.toMap()) {
        downloadErrors.values.lastOrNull()?.let { error ->
            snackbarHostState.showSnackbar(error)
            // 已通过 snackbar 提示，移除错误
            downloadErrors.clear()
        }
    }

    fun refreshModels() {
        models = getAvailableModels(context)
    }

    fun downloadModel(model: AiModelInfo) {
        // 已在下载中则忽略
        if (downloadJobs.containsKey(model.id)) return
        val aiService = AiService(context)
        val job = scope.launch(Dispatchers.IO) {
            try {
                downloadProgress[model.id] = 0f
                var retryCount = 0
                val maxRetries = 3
                var success = false
                while (!success && retryCount < maxRetries) {
                    try {
                        success = aiService.ensureModelDownloaded(context, model.id) { progress ->
                            downloadProgress[model.id] = progress
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        retryCount++
                        if (retryCount >= maxRetries) throw e
                        delay(2000L * retryCount)  // 指数退避
                    }
                }
                if (success) {
                    withContext(Dispatchers.Main) {
                        models = models.map {
                            if (it.id == model.id) it.copy(isDownloaded = true) else it
                        }
                        refreshModels()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户主动取消，不显示错误
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    downloadErrors[model.id] = e.message ?: "下载失败"
                }
            } finally {
                downloadProgress.remove(model.id)
                downloadJobs.remove(model.id)
            }
        }
        downloadJobs[model.id] = job
    }

    fun cancelDownload(modelId: String) {
        downloadJobs.remove(modelId)?.cancel()
        downloadProgress.remove(modelId)
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
                    onDownload = { downloadModel(model) },
                    onCancelDownload = { cancelDownload(model.id) },
                    onDelete = {
                        // Delete model files
                        AiService.deleteModel(context, model.id)
                        refreshModels()
                    }
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
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (model.isDownloaded) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (model.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                        contentDescription = null,
                        tint = if (model.isDownloaded) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
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
                // 下载中显示进度条 + 取消按钮
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

            // Action button / 下载进度
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
                // 已在信息区显示进度+取消按钮，此处不重复显示
                Spacer(modifier = Modifier.width(0.dp))
            } else if (progress != null) {
                // 兼容性兜底：显示下载进度条
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

// ── Model list builder ──────────────────────────────────────
private fun getAvailableModels(context: android.content.Context): List<AiModelInfo> {
    val modelsDir = File(context.filesDir, "ai_models")
    return listOf(
        AiModelInfo(
            id = "clip_image_encoder",
            name = "CLIP 图像编码器",
            description = "将图片编码为语义向量，用于智能语义搜索",
            sizeMB = 89,
            isDownloaded = File(modelsDir, "clip_image_encoder.onnx").exists(),
            isRequired = true
        ),
        AiModelInfo(
            id = "clip_text_encoder",
            name = "CLIP 文本编码器",
            description = "将文本描述编码为向量，与图像向量进行语义匹配",
            sizeMB = 62,
            isDownloaded = File(modelsDir, "clip_text_encoder.onnx").exists(),
            isRequired = true
        ),
        AiModelInfo(
            id = "rating_model",
            name = "评分推荐模型",
            description = "分析图片构图、曝光、色彩等维度，智能评分推荐",
            sizeMB = 15,
            isDownloaded = File(modelsDir, "rating_model.onnx").exists(),
            isRequired = false
        )
    )
}

// ── AiService extensions for model management ───────────────
private suspend fun AiService.ensureModelDownloaded(
    context: android.content.Context,
    modelId: String,
    onProgress: (Float) -> Unit = {}
): Boolean {
    val modelsDir = File(context.filesDir, "ai_models")
    if (!modelsDir.exists()) modelsDir.mkdirs()
    val modelFile = File(modelsDir, "$modelId.onnx")

    // Already downloaded
    if (modelFile.exists() && modelFile.length() > 0) {
        onProgress(1f)
        return true
    }

    // Download from CDN
    val cdnBaseUrl = "https://models.alcedo.studio/"
    val downloadUrl = cdnBaseUrl + modelId + ".onnx"

    withContext(Dispatchers.IO) {
        val client = SecureHttpClient.getClient(context)

        val request = Request.Builder()
            .url(downloadUrl)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            android.util.Log.e("AiModelManager", "Download failed: ${response.code} for $downloadUrl")
            throw java.io.IOException("Download failed: HTTP ${response.code}")
        }

        val body = response.body ?: throw java.io.IOException("Empty response body for $downloadUrl")
        val inputStream = body.byteStream()
        val tempFile = File(modelsDir, "$modelId.onnx.tmp")

        try {
            val totalBytes = body.contentLength()
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var downloaded = 0L
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    if (totalBytes > 0) {
                        onProgress((downloaded.toFloat() / totalBytes).coerceIn(0f, 1f))
                    }
                }
            }
            // Atomic rename
            if (tempFile.renameTo(modelFile)) {
                android.util.Log.i("AiModelManager", "Model $modelId downloaded successfully (${modelFile.length()} bytes)")
                onProgress(1f)
                true
            } else {
                tempFile.delete()
                android.util.Log.e("AiModelManager", "Failed to rename temp file for $modelId")
                throw java.io.IOException("Failed to rename temp file for $modelId")
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        } finally {
            inputStream.close()
        }
    }
}

private fun AiService.deleteModel(context: android.content.Context, modelId: String) {
    try {
        val modelsDir = File(context.filesDir, "ai_models")
        val modelFile = File(modelsDir, "$modelId.onnx")
        if (modelFile.exists()) modelFile.delete()
    } catch (_: Exception) {}
}
