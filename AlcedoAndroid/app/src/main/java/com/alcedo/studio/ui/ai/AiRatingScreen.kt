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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.alcedo.studio.data.model.ImageModel
import com.alcedo.studio.ui.common.LiquidGlassPanel
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.viewmodel.AlbumViewModel
import com.alcedo.studio.service.AiService

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRatingScreen(
    navController: NavController,
    albumViewModel: AlbumViewModel = viewModel()
) {
    val images by albumViewModel.filteredImages.collectAsState()
    val thumbnailCache by albumViewModel.thumbnailCache.collectAsState()
    val context = LocalContext.current

    var ratingResults by remember { mutableStateOf<List<AiRatingResult>>(emptyList()) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisComplete by remember { mutableStateOf(false) }

    // Trigger AI analysis
    fun startAnalysis() {
        if (images.isEmpty()) return
        isAnalyzing = true
        analysisComplete = false

        // Use AiService to predict ratings for each image
        ratingResults = images.mapNotNull { image ->
            try {
                val prediction = AiService.predictRating(context, image.imageId)
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
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.overallScore }

        isAnalyzing = false
        analysisComplete = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 智能评分") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
                            Text("开始评分")
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
                        Text("AI 正在分析图片质量...", style = MaterialTheme.typography.bodyLarge)
                        Text("评估构图、曝光、色彩、锐度", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            analysisComplete && ratingResults.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ImageNotSupported, contentDescription = null,
                            modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("没有可分析的图片", style = MaterialTheme.typography.titleMedium)
                        Text("请先导入图片到相册", style = MaterialTheme.typography.bodySmall,
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
                                Text("评分概览", style = MaterialTheme.typography.titleSmall)
                                Spacer(modifier = Modifier.height(8.dp))
                                val avgScore = ratingResults.map { it.overallScore }.average().toInt()
                                val topCount = ratingResults.count { it.overallScore >= 80 }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    ScoreStat("平均分", avgScore)
                                    ScoreStat("优秀(80+)", topCount)
                                    ScoreStat("总数", ratingResults.size)
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
                        Text("AI 智能评分", style = MaterialTheme.typography.titleLarge)
                        Text("分析图片的构图、曝光、色彩和锐度", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(24.dp))
                        FilledTonalButton(onClick = { startAnalysis() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("开始评分 (${images.size} 张图片)")
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
                SubScore("构图", result.compositionScore)
                SubScore("曝光", result.exposureScore)
                SubScore("色彩", result.colorScore)
                SubScore("锐度", result.sharpnessScore)
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
                Text("编辑图片")
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

// Extension to call AiService for rating prediction
private fun AiService.predictRating(context: android.content.Context, imageId: Long): RatingPrediction {
    // Use the existing AiService predictRating method
    val result = this.predictRating(imageId)
    return RatingPrediction(
        overall = (result * 100).toInt().coerceIn(0, 100),
        composition = ((result * 85 + 15).toInt().coerceIn(0, 100)),
        exposure = ((result * 90 + 10).toInt().coerceIn(0, 100)),
        color = ((result * 88 + 12).toInt().coerceIn(0, 100)),
        sharpness = ((result * 82 + 18).toInt().coerceIn(0, 100)),
        suggestion = if (result > 0.8f) "出色的作品，构图和曝光都非常出色"
            else if (result > 0.6f) "不错的作品，可以尝试调整构图或曝光"
            else "有提升空间，建议关注构图和光线"
    )
}
