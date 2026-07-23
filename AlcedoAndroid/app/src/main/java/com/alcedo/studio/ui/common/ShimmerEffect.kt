package com.alcedo.studio.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.shimmer(
    shimmerColor: Color? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(4.dp)
): Modifier = composed {
    val shimmerColors = shimmerColor?.let {
        listOf(
            it.copy(alpha = 0.4f),
            it.copy(alpha = 0.8f),
            it.copy(alpha = 0.4f)
        )
    } ?: listOf(
        // UX 修复: 使用主题色而非硬编码 LightGray,深色主题下不再刺眼
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim.value - 300f, 0f),
        end = Offset(translateAnim.value, 0f)
    )

    this
        .clip(shape)
        .background(brush)
}

// ================================================================
// Skeleton Components
// ================================================================

@Composable
fun SkeletonThumbnail(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .shimmer(shape = RoundedCornerShape(8.dp))
    )
}

@Composable
fun SkeletonAlbumGrid(
    modifier: Modifier = Modifier,
    columns: Int = 3,
    itemCount: Int = 12
) {
    Column(modifier = modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Top bar skeleton
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.width(120.dp).height(24.dp).shimmer())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(40.dp).shimmer(shape = CircleShape))
                Box(modifier = Modifier.size(40.dp).shimmer(shape = CircleShape))
                Box(modifier = Modifier.size(40.dp).shimmer(shape = CircleShape))
            }
        }

        // Grid skeleton
        for (row in 0 until (itemCount / columns)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (col in 0 until columns) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .shimmer(shape = RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun SkeletonEditorPreview(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Toolbar skeleton
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.size(40.dp).shimmer(shape = CircleShape))
            Box(modifier = Modifier.width(100.dp).height(24.dp).shimmer())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(40.dp).shimmer(shape = CircleShape))
                Box(modifier = Modifier.size(40.dp).shimmer(shape = CircleShape))
            }
        }

        // Image area skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
                .shimmer(shape = RoundedCornerShape(12.dp))
        )

        // Panel tabs skeleton
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(5) {
                Box(modifier = Modifier.width(60.dp).height(32.dp).shimmer(shape = RoundedCornerShape(16.dp)))
            }
        }

        // Sliders skeleton
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            repeat(3) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.width(80.dp).height(16.dp).shimmer())
                    Box(modifier = Modifier.width(40.dp).height(16.dp).shimmer())
                }
                Box(modifier = Modifier.fillMaxWidth().height(20.dp).shimmer(shape = RoundedCornerShape(10.dp)))
            }
        }
    }
}

@Composable
fun SkeletonExportScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.size(40.dp).shimmer(shape = CircleShape))
            Box(modifier = Modifier.width(80.dp).height(24.dp).shimmer())
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Format selector
        Box(modifier = Modifier.fillMaxWidth().height(48.dp).shimmer(shape = RoundedCornerShape(8.dp)))
        Spacer(modifier = Modifier.height(16.dp))

        // Quality slider
        Box(modifier = Modifier.width(100.dp).height(16.dp).shimmer())
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().height(20.dp).shimmer(shape = RoundedCornerShape(10.dp)))
        Spacer(modifier = Modifier.height(16.dp))

        // Size section
        Box(modifier = Modifier.width(80.dp).height(16.dp).shimmer())
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f).height(48.dp).shimmer(shape = RoundedCornerShape(8.dp)))
            Box(modifier = Modifier.weight(1f).height(48.dp).shimmer(shape = RoundedCornerShape(8.dp)))
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Export button
        Box(modifier = Modifier.fillMaxWidth().height(48.dp).shimmer(shape = RoundedCornerShape(24.dp)))
    }
}
