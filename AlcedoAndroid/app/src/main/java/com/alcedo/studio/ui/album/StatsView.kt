package com.alcedo.studio.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alcedo.studio.data.model.*
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
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
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Overview
        Text("Statistics", style = MaterialTheme.typography.headlineSmall)
        Text(
            "$totalImages images in library",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Date Distribution Chart
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Date Distribution", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (dateDistribution.isEmpty()) {
                    Text("No date data available", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val maxCount = dateDistribution.maxOfOrNull { it.count } ?: 1
                    dateDistribution.sortedByDescending { it.year }.take(10).forEach { facet ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${facet.year}${if (facet.month != null) "/${facet.month}" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(56.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(16.dp)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(facet.count.toFloat() / maxCount)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                )
                            }
                            Text(
                                "${facet.count}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }

        // Camera Model Distribution
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Camera Models", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (cameraDistribution.isEmpty()) {
                    Text("No camera data available", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val maxCount = cameraDistribution.maxOfOrNull { it.count } ?: 1
                    cameraDistribution.sortedByDescending { it.count }.take(8).forEach { facet ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${facet.make} ${facet.model}".trim(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.5f)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(0.4f)
                                    .height(14.dp)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(facet.count.toFloat() / maxCount)
                                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f))
                                )
                            }
                            Text(
                                "${facet.count}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }

        // Lens Distribution
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Camera, contentDescription = null, modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lens Distribution", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (lensDistribution.isEmpty()) {
                    Text("No lens data available", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val maxCount = lensDistribution.maxOfOrNull { it.count } ?: 1
                    lensDistribution.sortedByDescending { it.count }.take(8).forEach { facet ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                facet.model,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.5f)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(0.4f)
                                    .height(14.dp)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(facet.count.toFloat() / maxCount)
                                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f))
                                )
                            }
                            Text(
                                "${facet.count}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }

        // Rating Distribution
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(20.dp),
                        tint = Color(0xFFFFD700))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rating Distribution", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (ratingDistribution.isEmpty()) {
                    Text("No rating data available", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val maxCount = ratingDistribution.maxOfOrNull { it.count } ?: 1
                    (5 downTo 0).forEach { rating ->
                        val dist = ratingDistribution.find { it.rating == rating }
                        val count = dist?.count ?: 0
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.width(56.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (rating > 0) {
                                    repeat(rating) {
                                        Icon(Icons.Default.Star, contentDescription = null,
                                            modifier = Modifier.size(10.dp), tint = Color(0xFFFFD700))
                                    }
                                } else {
                                    Text("Unrated", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 9.sp)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(14.dp)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                if (count > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(count.toFloat() / maxCount)
                                            .background(Color(0xFFFFD700).copy(alpha = 0.6f))
                                    )
                                }
                            }
                            Text(
                                "$count",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }

        // Tag Cloud
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tag Cloud", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (tagCloud.isEmpty()) {
                    Text("No tags available. Run semantic analysis to generate tags.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val maxCount = tagCloud.maxOfOrNull { it.count } ?: 1
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tagCloud.sortedByDescending { it.count }.take(30).forEach { tag ->
                            val ratio = tag.count.toFloat() / maxCount
                            val fontSize = (10 + (ratio * 8).toInt()).sp
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f + ratio * 0.3f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        tag.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = fontSize,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        "${tag.count}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = (fontSize.value - 2).sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
    }
}
