package com.alcedo.studio.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Comparison mode for the before/after view.
 */
enum class CompareMode {
    SPLIT, SIDE_BY_SIDE, OVERLAY
}

/**
 * Before/after comparison view with three modes:
 * - **Split**: A draggable vertical divider clips the before image on the left
 *   and shows the after image on the right.
 * - **Side by Side**: Both images are shown in a row with labels.
 * - **Overlay**: The after image is shown at adjustable opacity over the before.
 *
 * If either bitmap is null the available one is shown full-bleed.
 */
@Composable
fun CompareView(
    beforeBitmap: Bitmap?,
    afterBitmap: Bitmap?,
    modifier: Modifier = Modifier,
    initialMode: CompareMode = CompareMode.SPLIT,
) {
    val s = Strings.res
    var mode by remember { mutableStateOf(initialMode) }
    var split by remember { mutableFloatStateOf(0.5f) }
    var overlayOpacity by remember { mutableFloatStateOf(0.5f) }

    Column(modifier = modifier) {
        // Mode selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.spacingSm),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = mode == CompareMode.SPLIT,
                onClick = { mode = CompareMode.SPLIT },
                label = { Text(s.compareSplit, maxLines = 1) },
            )
            FilterChip(
                selected = mode == CompareMode.SIDE_BY_SIDE,
                onClick = { mode = CompareMode.SIDE_BY_SIDE },
                label = { Text(s.compareSideBySide, maxLines = 1) },
            )
            FilterChip(
                selected = mode == CompareMode.OVERLAY,
                onClick = { mode = CompareMode.OVERLAY },
                label = { Text(s.compareOverlay, maxLines = 1) },
            )
        }

        when (mode) {
            CompareMode.SPLIT -> SplitCompare(
                beforeBitmap = beforeBitmap,
                afterBitmap = afterBitmap,
                split = split,
                onSplitChange = { split = it },
                modifier = Modifier.weight(1f),
            )
            CompareMode.SIDE_BY_SIDE -> SideBySideCompare(
                beforeBitmap = beforeBitmap,
                afterBitmap = afterBitmap,
                modifier = Modifier.weight(1f),
            )
            CompareMode.OVERLAY -> OverlayCompare(
                beforeBitmap = beforeBitmap,
                afterBitmap = afterBitmap,
                opacity = overlayOpacity,
                onOpacityChange = { overlayOpacity = it },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SplitCompare(
    beforeBitmap: Bitmap?,
    afterBitmap: Bitmap?,
    split: Float,
    onSplitChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AlcedoColors.PureBlack)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onSplitChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSplitChange((offset.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        // After (full)
        if (afterBitmap != null) {
            Image(
                bitmap = afterBitmap.asImageBitmap(),
                contentDescription = "After",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Before (clipped to left of divider)
        if (beforeBitmap != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width * split
                clipRect(left = 0f, top = 0f, right = w, bottom = size.height, clipOp = ClipOp.Intersect) {
                    drawImage(
                        image = beforeBitmap.asImageBitmap(),
                        dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                    )
                }
                // Divider line
                drawLine(
                    color = AlcedoColors.AccentBlue,
                    start = Offset(w, 0f),
                    end = Offset(w, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
                // Handle
                drawCircle(
                    color = AlcedoColors.AccentBlue,
                    radius = 10.dp.toPx(),
                    center = Offset(w, size.height / 2),
                )
            }
        }
    }
}

@Composable
private fun SideBySideCompare(
    beforeBitmap: Bitmap?,
    afterBitmap: Bitmap?,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXxs),
    ) {
        // Before
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (beforeBitmap != null) {
                Image(
                    bitmap = beforeBitmap.asImageBitmap(),
                    contentDescription = s.before,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = s.before,
                style = MaterialTheme.typography.labelSmall,
                color = AlcedoColors.TextOnAccent,
                modifier = Modifier
                    .padding(DesignTokens.spacingXs)
                    .background(AlcedoColors.SurfaceScrim, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = DesignTokens.spacingXs, vertical = DesignTokens.spacingXxs),
            )
        }
        // After
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (afterBitmap != null) {
                Image(
                    bitmap = afterBitmap.asImageBitmap(),
                    contentDescription = s.after,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = s.after,
                style = MaterialTheme.typography.labelSmall,
                color = AlcedoColors.TextOnAccent,
                modifier = Modifier
                    .padding(DesignTokens.spacingXs)
                    .background(AlcedoColors.SurfaceScrim, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = DesignTokens.spacingXs, vertical = DesignTokens.spacingXxs),
            )
        }
    }
}

@Composable
private fun OverlayCompare(
    beforeBitmap: Bitmap?,
    afterBitmap: Bitmap?,
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AlcedoColors.PureBlack)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onOpacityChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onOpacityChange((offset.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        // Before (base layer)
        if (beforeBitmap != null) {
            Image(
                bitmap = beforeBitmap.asImageBitmap(),
                contentDescription = "Before",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // After (overlay at adjustable opacity)
        if (afterBitmap != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawImage(
                    image = afterBitmap.asImageBitmap(),
                    dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                    alpha = opacity,
                )
            }
        }
        // Opacity indicator at bottom
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barY = size.height - 24.dp.toPx()
            val barW = size.width * 0.6f
            val barX = (size.width - barW) / 2f
            // Background track
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(barX, barY),
                size = Size(barW, 4.dp.toPx()),
            )
            // Active track
            drawRect(
                color = AlcedoColors.AccentBlue,
                topLeft = Offset(barX, barY),
                size = Size(barW * opacity, 4.dp.toPx()),
            )
            // Thumb
            drawCircle(
                color = AlcedoColors.AccentBlue,
                radius = 6.dp.toPx(),
                center = Offset(barX + barW * opacity, barY + 2.dp.toPx()),
            )
        }
    }
}
