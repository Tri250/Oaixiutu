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
import com.alcedo.studio.service.AiService
import com.alcedo.studio.ui.common.EmptyState
import com.alcedo.studio.ui.common.LiquidGlassPanel
import com.alcedo.studio.ui.common.LiquidGlassSurface
import kotlinx.coroutines.Dispatchers
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

    // Model list - CLIP, Rating, etc.
    var models by remember {
        mutableStateOf(getAvailableModels(context))
    }
    var isRefreshing by remember { mutableStateOf(false) }

    fun refreshModels() {
        models = getAvailableModels(context)
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
        }
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
                            Text("本地运行 · 隐私安全", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            "所有 AI 模型均在设备本地运行，不会将您的图片上传到任何服务器。" +
                                "CLIP 模型用于语义搜索，评分模型用于智能图片推荐。",
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
                            "已下载 $downloadedCount/${models.size} 个模型 · ${totalSize}MB",
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
                    onDownload = {
                        // Trigger actual download via AiService
                        val aiService = AiService(context)
                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                            val success = aiService.ensureModelDownloaded(context, model.id)
                            if (success) {
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    models = models.map {
                                        if (it.id == model.id) it.copy(isDownloaded = true)
                                        else it
                                    }
                                    refreshModels()
                                }
                            }
                        }
                    },
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
                        Text("模型使用说明", style = MaterialTheme.typography.titleSmall)
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
    onDownload: () -> Unit,
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
                                "必需",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action button
            if (model.isDownloaded) {
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !model.isRequired
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "删除",
                        tint = if (model.isRequired) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.error
                    )
                }
            } else {
                FilledTonalButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("下载", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除模型") },
            text = { Text("确定要删除 ${model.name} 吗？删除后相关功能将不可用，可随时重新下载。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
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
private suspend fun AiService.ensureModelDownloaded(context: android.content.Context, modelId: String): Boolean {
    return try {
        val modelsDir = File(context.filesDir, "ai_models")
        if (!modelsDir.exists()) modelsDir.mkdirs()
        val modelFile = File(modelsDir, "$modelId.onnx")

        // Already downloaded
        if (modelFile.exists() && modelFile.length() > 0) return true

        // Download from CDN
        val cdnBaseUrl = "https://models.alcedo.studio/"
        val downloadUrl = cdnBaseUrl + modelId + ".onnx"

        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()

            val request = Request.Builder()
                .url(downloadUrl)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.e("AiModelManager", "Download failed: ${response.code} for $downloadUrl")
                return@withContext false
            }

            val body = response.body ?: return@withContext false
            val inputStream = body.byteStream()
            val tempFile = File(modelsDir, "$modelId.onnx.tmp")

            try {
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
                // Atomic rename
                if (tempFile.renameTo(modelFile)) {
                    android.util.Log.i("AiModelManager", "Model $modelId downloaded successfully (${modelFile.length()} bytes)")
                    true
                } else {
                    tempFile.delete()
                    android.util.Log.e("AiModelManager", "Failed to rename temp file for $modelId")
                    false
                }
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            } finally {
                inputStream.close()
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("AiModelManager", "Model download failed for $modelId: ${e.message}", e)
        false
    }
}

private fun AiService.deleteModel(context: android.content.Context, modelId: String) {
    try {
        val modelsDir = File(context.filesDir, "ai_models")
        val modelFile = File(modelsDir, "$modelId.onnx")
        if (modelFile.exists()) modelFile.delete()
    } catch (_: Exception) {}
}
