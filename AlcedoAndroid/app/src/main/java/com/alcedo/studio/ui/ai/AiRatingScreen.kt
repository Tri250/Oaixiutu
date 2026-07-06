package com.alcedo.studio.ui.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.alcedo.studio.data.model.ImageModel
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.LiquidGlassPanel
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.viewmodel.AlbumViewModel
import com.alcedo.studio.domain.service.AiService
import com.alcedo.studio.di.AppModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AiRatingResult(
    val imageId: Long,
    val imageName: String,
    val overallScore: Int,
    val compositionScore: Int,
    val exposureScore: Int,
    val colorScore: Int,
    val sharpnessScore: Int,
    val suggestion: String
)

data class AnalysisProgress(val current: Int, val total: Int)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AiRatingScreen(
    navController: NavController,
    albumViewModel: AlbumViewModel = viewModel()
) {
    val images by albumViewModel.filteredImages.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val aiService = remember { AppModule.aiService }
    val coroutineScope = rememberCoroutineScope()
    val analysisProgress = remember { MutableStateFlow(AnalysisProgress(0, 0)) }

    var ratingResults by remember { mutableStateOf<List<AiRatingResult>>(emptyList()) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisComplete by remember { mutableStateOf(false) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var analysisJob by remember { mutableStateOf<Job?>(null) }

    // Trigger AI analysis
    fun startAnalysis() {
        if (!isAnalyzing) {
            isAnalyzing = true
            analysisComplete = false
            analysisError = null
            ratingResults = emptyList()
            analysisJob = coroutineScope.launch {
                try {
                    val results = images.mapIndexed { index, image ->
                        ensureActive()
                        analysisProgress.value = AnalysisProgress(index + 1, images.size)
                        val prediction = predictRatingSafely(
                            context = context,
                            aiService = aiService,
                            imageId = image.imageId,
                            imagePath = image.imagePath
                        )
                        AiRatingResult(
                            imageId = image.imageId,
                            imageName = image.imageName,
                            overallScore = prediction.overall,
                            compositionScore = prediction.composition,
                            exposureScore = prediction.exposure,
                            colorScore = prediction.color,
                            sharpnessScore = prediction.sharpness,
                            suggestion = prediction.suggestion
                        )
                    }.sortedByDescending { it.overallScore }
                    ratingResults = results
                    analysisComplete = true
                } catch (e: CancellationException) {
                    // 用户取消，不显示错误
                } catch (e: Exception) {
                    analysisError = "分析失败：${e.message}"
                } finally {
                    isAnalyzing = false
                    analysisJob = null
                }
            }
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        isAnalyzing = false
        analysisProgress.value = AnalysisProgress(0, 0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes { aiRatingTitle }.format("")) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringRes { back })
                    }
                },
                actions = {
                    if (!isAnalyzing && !analysisComplete) {
                        FilledTonalButton(
                            onClick = { startAnalysis() },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringRes { aiStartRating })
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            isAnalyzing -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringRes { aiRatingEvaluating }, style = MaterialTheme.typography.bodyLarge)
                        Text(stringRes { aiRatingAnalyze }, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val progress by analysisProgress.collectAsStateWithLifecycle()
                        if (progress.total > 0) {
                            Text("${progress.current} / ${progress.total}", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        // 取消按钮
                        TextButton(onClick = { cancelAnalysis() }) {
                            Text("取消")
                        }
                    }
                }
            }
            analysisError != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("分析出错", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(analysisError!!, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            analysisError = null
                            startAnalysis()
                        }) { Text("重试") }
                    }
                }
            }
            analysisComplete && ratingResults.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ImageNotSupported, contentDescription = null,
                            modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringRes { aiRatingNoImages }, style = MaterialTheme.typography.titleMedium)
                        Text(stringRes { aiRatingNoImagesDesc }, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            analysisComplete -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Summary card
                    item {
                        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(stringRes { aiRatingScoreOverview }, style = MaterialTheme.typography.titleSmall)
                                Spacer(modifier = Modifier.height(8.dp))
                                val avgScore = ratingResults.map { it.overallScore }.average().toInt()
                                val topCount = ratingResults.count { it.overallScore >= 80 }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    ScoreStat(stringRes { aiRatingAverageScore }, avgScore)
                                    ScoreStat(stringRes { aiRatingExcellent }, topCount)
                                    ScoreStat(stringRes { aiRatingTotal }, ratingResults.size)
                                }
                            }
                        }
                    }

                    // Rating cards
                    items(ratingResults, key = { it.imageId }) { result ->
                        RatingCard(
                            result = result,
                            onClick = {
                                navController.navigate("editor/${result.imageId}")
                            },
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            }
            else -> {
                // Initial state
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null,
                            modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringRes { aiRatingTitle }.format(""), style = MaterialTheme.typography.titleLarge)
                        Text(stringRes { aiRatingAnalyzeDesc }, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(24.dp))
                        FilledTonalButton(onClick = { startAnalysis() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringRes { aiRatingStartWithCount }.format(images.size))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreStat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RatingCard(result: AiRatingResult, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LiquidGlassSurface(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(result.imageName, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = when {
                        result.overallScore >= 80 -> Color(0xFF4CAF50)
                        result.overallScore >= 60 -> Color(0xFFFFC107)
                        else -> Color(0xFFFF5722)
                    }
                ) {
                    Text(
                        "${result.overallScore}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-scores
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SubScore(stringRes { aiRatingComposition }, result.compositionScore)
                SubScore(stringRes { aiRatingExposure }, result.exposureScore)
                SubScore(stringRes { aiRatingColorScore }, result.colorScore)
                SubScore(stringRes { aiRatingSharpness }, result.sharpnessScore)
            }

            if (result.suggestion.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    result.suggestion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringRes { aiRatingEditImage })
            }
        }
    }
}

@Composable
private fun SubScore(label: String, score: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$score", style = MaterialTheme.typography.bodyLarge,
            color = when {
                score >= 80 -> Color(0xFF4CAF50)
                score >= 60 -> Color(0xFFFFC107)
                else -> Color(0xFFFF5722)
            }
        )
    }
}

// Rating prediction result model
data class RatingPrediction(
    val overall: Int,
    val composition: Int,
    val exposure: Int,
    val color: Int,
    val sharpness: Int,
    val suggestion: String
)

// 挂起函数，在 IO 线程执行真实评分推理
suspend fun predictRatingSafely(
    context: android.content.Context,
    aiService: AiService,
    imageId: Long,
    imagePath: String
): RatingPrediction = withContext(Dispatchers.Default) {
    // 加载图片（采样到合理尺寸）
    val bitmap = try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(imagePath, this)
            inSampleSize = calculateInSampleSize(outWidth, outHeight, 512, 512)
            inJustDecodeBounds = false
        }
        BitmapFactory.decodeFile(imagePath, options)
    } catch (e: Exception) {
        null
    }

    // 调用真实 AI 评分服务
    val (score, description) = if (bitmap != null) {
        try {
            aiService.rateImage(imageId.toUInt(), bitmap)
        } catch (e: Exception) {
            3 to "评分失败：${e.message}"
        }
    } else {
        3 to "无法加载图片"
    }

    // 基于评分结果生成多维度分数
    val overallScore = (score * 20).coerceIn(0, 100)

    // 从位图计算真实的子维度分数
    val analysisBitmap = bitmap?.let { bmp ->
        // 降采样到 64x64 用于分析
        try {
            Bitmap.createScaledBitmap(bmp, 64, 64, false)
        } catch (e: Exception) {
            null
        }
    }

    val (composition, exposure, color, sharpness) = if (analysisBitmap != null) {
        analyzeImageDimensions(analysisBitmap)
    } else {
        listOf(overallScore, overallScore, overallScore, overallScore)
    }

    // 释放缩放位图（如果与原图不同）
    if (analysisBitmap != null && analysisBitmap !== bitmap) {
        analysisBitmap.recycle()
    }

    RatingPrediction(
        overall = overallScore,
        composition = composition,
        exposure = exposure,
        color = color,
        sharpness = sharpness,
        suggestion = description
    )
}

/**
 * 从位图像素计算真实的子维度评分（构图、曝光、色彩、锐度）。
 * 在 IO 线程执行，避免阻塞 UI。
 */
private fun analyzeImageDimensions(bitmap: Bitmap): List<Int> {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    // 计算亮度直方图
    val histogram = IntArray(256)
    var totalR = 0L; var totalG = 0L; var totalB = 0L
    var totalBrightness = 0L

    for (pixel in pixels) {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val brightness = (r + g + b) / 3
        histogram[brightness]++
        totalR += r; totalG += g; totalB += b
        totalBrightness += brightness
    }

    val count = pixels.size
    val avgBrightness = (totalBrightness / count).toInt()
    val avgR = (totalR / count).toInt()
    val avgG = (totalG / count).toInt()
    val avgB = (totalB / count).toInt()

    // 饱和度：RGB 通道平均差异
    val saturation = (kotlin.math.abs(avgR - avgG) +
        kotlin.math.abs(avgG - avgB) +
        kotlin.math.abs(avgR - avgB)) / 3

    // 构图：三分法评估 — 中心区域与边缘的亮度差异
    val centerStart = width / 3
    val centerEnd = 2 * width / 3
    val rowStart = height / 3
    val rowEnd = 2 * height / 3
    var centerBrightness = 0L
    var edgeBrightness = 0L
    var centerCount = 0; var edgeCount = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = pixels[y * width + x]
            val brightness = (((pixel shr 16) and 0xFF) + ((pixel shr 8) and 0xFF) + (pixel and 0xFF)) / 3
            if (x in centerStart until centerEnd && y in rowStart until rowEnd) {
                centerBrightness += brightness; centerCount++
            } else {
                edgeBrightness += brightness; edgeCount++
            }
        }
    }
    val centerAvg = if (centerCount > 0) centerBrightness / centerCount else 0L
    val edgeAvg = if (edgeCount > 0) edgeBrightness / edgeCount else 0L
    val compositionScore = (100 - kotlin.math.abs(centerAvg - edgeAvg)).coerceIn(0, 100)

    // 锐度：拉普拉斯算子近似
    var sharpnessSum = 0L
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val center = (pixels[y * width + x] shr 16) and 0xFF
            val top = (pixels[(y - 1) * width + x] shr 16) and 0xFF
            val bottom = (pixels[(y + 1) * width + x] shr 16) and 0xFF
            val left = (pixels[y * width + (x - 1)] shr 16) and 0xFF
            val right = (pixels[y * width + (x + 1)] shr 16) and 0xFF
            sharpnessSum += kotlin.math.abs(4 * center - top - bottom - left - right)
        }
    }
    val sharpnessScore = (sharpnessSum / (width * height) * 2).toInt().coerceIn(0, 100)

    // 曝光：偏离中灰 (128) 越远分数越低
    val exposureScore = (100 - kotlin.math.abs(avgBrightness - 128) * 2).coerceIn(0, 100)

    // 色彩：饱和度越高分数越高
    val colorScore = (saturation * 3).coerceIn(0, 100)

    return listOf(compositionScore.toInt(), exposureScore.toInt(), colorScore.toInt(), sharpnessScore.toInt())
}

private fun calculateInSampleSize(outWidth: Int, outHeight: Int, reqWidth: Int, reqHeight: Int): Int {
    var inSampleSize = 1
    if (outHeight > reqHeight || outWidth > reqWidth) {
        val halfHeight = outHeight / 2
        val halfWidth = outWidth / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
