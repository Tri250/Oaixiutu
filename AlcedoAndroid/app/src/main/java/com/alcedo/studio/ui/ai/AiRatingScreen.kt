package com.alcedo.studio.ui.ai

import android.graphics.BitmapFactory
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.alcedo.studio.data.model.ImageModel
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.LiquidGlassPanel
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.viewmodel.AlbumViewModel
import com.alcedo.studio.service.AiService
import com.alcedo.studio.di.AppModule
import kotlinx.coroutines.Dispatchers
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRatingScreen(
    navController: NavController,
    albumViewModel: AlbumViewModel = viewModel()
) {
    val images by albumViewModel.filteredImages.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val aiService = remember { AppModule.appAiService }
    val coroutineScope = rememberCoroutineScope()
    val analysisProgress = remember { MutableStateFlow(AnalysisProgress(0, 0)) }

    var ratingResults by remember { mutableStateOf<List<AiRatingResult>>(emptyList()) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisComplete by remember { mutableStateOf(false) }

    // Trigger AI analysis
    fun startAnalysis() {
        if (!isAnalyzing) {
            isAnalyzing = true
            analysisComplete = false
            ratingResults = emptyList()
            coroutineScope.launch {
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
                withContext(Dispatchers.Main) {
                    ratingResults = results
                    isAnalyzing = false
                    analysisComplete = true
                }
            }
        }
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
                        RatingCard(result = result, onClick = {
                            navController.navigate("editor/${result.imageId}")
                        })
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
private fun RatingCard(result: AiRatingResult, onClick: () -> Unit) {
    LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
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
            aiService.predictRating(imageId.toUInt(), bitmap)
        } catch (e: Exception) {
            3 to "评分失败：${e.message}"
        }
    } else {
        3 to "无法加载图片"
    }

    // 基于评分结果生成多维度分数
    val overallScore = (score * 20).coerceIn(0, 100)
    RatingPrediction(
        overall = overallScore,
        composition = (overallScore * 0.9 + 5).toInt().coerceIn(0, 100),
        exposure = (overallScore * 0.95 + 3).toInt().coerceIn(0, 100),
        color = (overallScore * 0.88 + 8).toInt().coerceIn(0, 100),
        sharpness = (overallScore * 0.92 + 4).toInt().coerceIn(0, 100),
        suggestion = description
    )
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
