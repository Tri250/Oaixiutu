package com.alcedo.studio.ui.album

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alcedo.studio.data.model.*
import com.alcedo.studio.i18n.stringRes
import androidx.navigation.NavController

// Chart color palette
private val ChartColors = listOf(
    Color(0xFF6750A4),  // Primary
    Color(0xFF625B71),  // Secondary
    Color(0xFF7D5260),  // Tertiary
    Color(0xFFB3261E),  // Error
    Color(0xFF006C4C),  // Green
    Color(0xFF0061A4),  // Blue
    Color(0xFF7C5800),  // Amber
    Color(0xFF6B4FA2),  // Purple
)

private val BarGradientPrimary = listOf(Color(0xFF6750A4), Color(0xFF9A82DB))
private val BarGradientSecondary = listOf(Color(0xFF625B71), Color(0xFF908A9E))
private val BarGradientTertiary = listOf(Color(0xFF7D5260), Color(0xFFB08897))
private val BarGradientGold = listOf(Color(0xFFFFD700), Color(0xFFFFEB99))
private val BarGradientGreen = listOf(Color(0xFF006C4C), Color(0xFF4DA882))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatsView(
    navController: NavController,
    dateDistribution: List<DateFacet> = emptyList(),
    cameraDistribution: List<CameraFacet> = emptyList(),
    lensDistribution: List<LensFacet> = emptyList(),
    ratingDistribution: List<RatingDistribution> = emptyList(),
    tagCloud: List<LabelFrequency> = emptyList(),
    totalImages: Int = 0,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes { settingsStatistics }) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringRes { back })
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Overview header ──────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "图库概览",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "图库共 $totalImages 张图片",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // ── Date Distribution Chart ──────────────────────────────
            DateDistributionChart(dateDistribution)

            // ── Camera Model Distribution ────────────────────────────
            CameraDistributionChart(cameraDistribution)

            // ── Lens Distribution ────────────────────────────────────
            LensDistributionChart(lensDistribution)

            // ── Rating Distribution ──────────────────────────────────
            RatingDistributionChart(ratingDistribution, totalImages)

            // ── Tag Cloud ────────────────────────────────────────────
            TagCloudChart(tagCloud)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// Animated Horizontal Bar
// ════════════════════════════════════════════════════════════════════

@Composable
private fun AnimatedHorizontalBar(
    fraction: Float,
    gradientColors: List<Color>,
    barHeight: Dp = 18.dp,
    cornerRadius: Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    val animatedFraction = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        animatedFraction.animateTo(
            targetValue = fraction.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 800)
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedFraction.value)
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    Brush.horizontalGradient(gradientColors)
                )
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// Date Distribution Chart
// ════════════════════════════════════════════════════════════════════

@Composable
private fun DateDistributionChart(dateDistribution: List<DateFacet>) {
    ChartCard(
        title = "时间分布",
        icon = Icons.Default.DateRange,
        iconTint = MaterialTheme.colorScheme.primary
    ) {
        if (dateDistribution.isEmpty()) {
            EmptyChartMessage("暂无时间数据")
        } else {
            val maxCount = dateDistribution.maxOfOrNull { it.count } ?: 1
            val total = dateDistribution.sumOf { it.count }
            dateDistribution
                .sortedByDescending { it.year }
                .take(10)
                .forEachIndexed { index, facet ->
                    val fraction = facet.count.toFloat() / maxCount
                    val percentage = if (total > 0) (facet.count * 100f / total) else 0f
                    val label = "${facet.year}${if (facet.month != null) "/${facet.month}" else ""}"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(56.dp)
                        )
                        AnimatedHorizontalBar(
                            fraction = fraction,
                            gradientColors = BarGradientPrimary,
                            barHeight = 16.dp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${facet.count}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(28.dp),
                            textAlign = TextAlign.End
                        )
                        Text(
                            String.format("%.0f%%", percentage),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(34.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// Camera Model Distribution Chart
// ════════════════════════════════════════════════════════════════════

@Composable
private fun CameraDistributionChart(cameraDistribution: List<CameraFacet>) {
    ChartCard(
        title = "相机型号",
        icon = Icons.Default.PhotoCamera,
        iconTint = MaterialTheme.colorScheme.secondary
    ) {
        if (cameraDistribution.isEmpty()) {
            EmptyChartMessage("暂无相机数据")
        } else {
            val maxCount = cameraDistribution.maxOfOrNull { it.count } ?: 1
            val total = cameraDistribution.sumOf { it.count }
            cameraDistribution
                .sortedByDescending { it.count }
                .take(8)
                .forEachIndexed { index, facet ->
                    val fraction = facet.count.toFloat() / maxCount
                    val percentage = if (total > 0) (facet.count * 100f / total) else 0f
                    val label = "${facet.make} ${facet.model}".trim()
                    val gradient = when (index % 3) {
                        0 -> BarGradientSecondary
                        1 -> BarGradientPrimary
                        else -> BarGradientGreen
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.42f)
                        )
                        AnimatedHorizontalBar(
                            fraction = fraction,
                            gradientColors = gradient,
                            barHeight = 14.dp,
                            modifier = Modifier.weight(0.38f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${facet.count}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(28.dp),
                            textAlign = TextAlign.End
                        )
                        Text(
                            String.format("%.0f%%", percentage),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(34.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// Lens Distribution Chart
// ════════════════════════════════════════════════════════════════════

@Composable
private fun LensDistributionChart(lensDistribution: List<LensFacet>) {
    ChartCard(
        title = "镜头分布",
        icon = Icons.Default.Camera,
        iconTint = MaterialTheme.colorScheme.tertiary
    ) {
        if (lensDistribution.isEmpty()) {
            EmptyChartMessage("暂无镜头数据")
        } else {
            val maxCount = lensDistribution.maxOfOrNull { it.count } ?: 1
            val total = lensDistribution.sumOf { it.count }
            lensDistribution
                .sortedByDescending { it.count }
                .take(8)
                .forEachIndexed { index, facet ->
                    val fraction = facet.count.toFloat() / maxCount
                    val percentage = if (total > 0) (facet.count * 100f / total) else 0f
                    val gradient = when (index % 3) {
                        0 -> BarGradientTertiary
                        1 -> BarGradientPrimary
                        else -> BarGradientGreen
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            facet.model,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.42f)
                        )
                        AnimatedHorizontalBar(
                            fraction = fraction,
                            gradientColors = gradient,
                            barHeight = 14.dp,
                            modifier = Modifier.weight(0.38f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${facet.count}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(28.dp),
                            textAlign = TextAlign.End
                        )
                        Text(
                            String.format("%.0f%%", percentage),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(34.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// Rating Distribution Chart (Stars + Bar)
// ════════════════════════════════════════════════════════════════════

@Composable
private fun RatingDistributionChart(
    ratingDistribution: List<RatingDistribution>,
    totalImages: Int
) {
    ChartCard(
        title = "评分分布",
        icon = Icons.Default.Star,
        iconTint = Color(0xFFFFD700)
    ) {
        if (ratingDistribution.isEmpty()) {
            EmptyChartMessage("暂无评分数据")
        } else {
            val maxCount = ratingDistribution.maxOfOrNull { it.count } ?: 1

            (5 downTo 0).forEach { rating ->
                val dist = ratingDistribution.find { it.rating == rating }
                val count = dist?.count ?: 0
                val fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f
                val percentage = if (totalImages > 0) count * 100f / totalImages else 0f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Star icons or "Unrated" label
                    Box(modifier = Modifier.width(64.dp)) {
                        if (rating > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                repeat(rating) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = Color(0xFFFFD700)
                                    )
                                }
                            }
                        } else {
                            Text(
                                "未评分",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Animated bar
                    AnimatedHorizontalBar(
                        fraction = fraction,
                        gradientColors = BarGradientGold,
                        barHeight = 14.dp,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Count
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(28.dp),
                        textAlign = TextAlign.End
                    )

                    // Percentage
                    Text(
                        String.format("%.0f%%", percentage),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(34.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// Tag Cloud Chart
// ════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagCloudChart(tagCloud: List<LabelFrequency>) {
    ChartCard(
        title = "标签云",
        icon = Icons.Default.Label,
        iconTint = MaterialTheme.colorScheme.primary
    ) {
        if (tagCloud.isEmpty()) {
            EmptyChartMessage("暂无标签。运行语义分析以生成标签。")
        } else {
            val maxCount = tagCloud.maxOfOrNull { it.count } ?: 1

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tagCloud
                    .sortedByDescending { it.count }
                    .take(30)
                    .forEachIndexed { index, tag ->
                        val ratio = tag.count.toFloat() / maxCount
                        val fontSize = (11 + (ratio * 7).toInt()).sp
                        val color = ChartColors[index % ChartColors.size]
                        val alpha = 0.12f + ratio * 0.25f

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = color.copy(alpha = alpha),
                            modifier = Modifier
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    tag.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = fontSize,
                                    fontWeight = if (ratio > 0.5f) FontWeight.SemiBold else FontWeight.Normal,
                                    color = color.copy(alpha = 0.85f + ratio * 0.15f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = color.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        "${tag.count}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = (fontSize.value - 2).sp,
                                        fontWeight = FontWeight.Medium,
                                        color = color.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// Shared Chart Components
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ChartCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Card header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = iconTint.copy(alpha = 0.12f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = iconTint
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun EmptyChartMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.BarChart,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
