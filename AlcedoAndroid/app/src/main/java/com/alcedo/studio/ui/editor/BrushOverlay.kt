package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.alcedo.studio.data.model.BrushStroke

/**
 * 画笔覆盖层的整体状态。
 *
 * [strokes] 保存所有已落定的笔触（[BrushStroke] 使用归一化坐标，
 * 这样在屏幕尺寸变化时仍能正确显示）。
 * [currentStroke] 是用户当前正在绘制的笔触，会被实时预览。
 * [isDrawingMode] 为 true 时单指拖动用于绘制；为 false 时单指/双指用于
 * 缩放与平移（透传到下层的 ZoomableImageView）。
 */
data class BrushState(
    val strokes: List<BrushStroke> = emptyList(),
    val currentStroke: BrushStroke? = null,
    val brushSize: Float = 0.05f,
    val brushHardness: Float = 0.5f,
    val brushOpacity: Float = 1f,
    val isEraser: Boolean = false,
    val isDrawingMode: Boolean = true
)

/**
 * 叠加在 ZoomableImageView 上方的画笔绘制覆盖层。
 *
 * 关键实现要点：
 * - 触摸坐标通过 [imageRect] 从屏幕空间转换到图片归一化空间
 *   `(screenPos - imageRect.topLeft) / imageRect.size`，确保笔触与图片对齐。
 * - 笔触路径使用平滑的三次贝塞尔曲线连接（mid-point 算法）。
 * - 硬度通过多层半透明同心圆叠加模拟边缘羽化：硬度高时圆盘边缘锐利，
 *   硬度低时圆盘边缘向透明渐变。
 * - 橡皮擦模式以 [BlendMode.Clear] 擦除已绘制区域。
 * - 在绘制模式下捕获单指拖动；在导航模式下使用 detectTransformGestures
 *   透传缩放/平移到下层（这里仅消费事件以避免误触，实际缩放由
 *   ZoomableImageView 在自身 pointerInput 中处理 — 因此导航模式下我们
 *   主动不消费事件，让事件冒泡到下层）。
 */
@Composable
fun BrushOverlay(
    brushState: BrushState,
    onBrushStateChanged: (BrushState) -> Unit,
    imageRect: Rect,
    modifier: Modifier = Modifier
) {
    // 当前正在绘制的笔触点（屏幕坐标），落定时转换为归一化坐标
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var isDrawing by remember { mutableStateOf(false) }

    // 当外部状态切换离开绘制模式或撤销/清空时，重置本地绘制状态
    LaunchedEffect(brushState.isDrawingMode, brushState.strokes.size) {
        if (!brushState.isDrawingMode) {
            currentPoints = emptyList()
            isDrawing = false
        }
    }

    val drawingModifier = if (brushState.isDrawingMode) {
        // 绘制模式：捕获单指拖动
        Modifier.pointerInput(brushState.isDrawingMode, brushState.isEraser) {
            detectDragGestures(
                onDragStart = { offset ->
                    // 仅当起点落在图片显示区域内时才开始绘制
                    if (imageRect.contains(offset)) {
                        isDrawing = true
                        currentPoints = listOf(offset)
                    }
                },
                onDrag = { change, _ ->
                    change.consume()
                    if (isDrawing) {
                        // 限制新点必须在图片区域内，避免笔触溢出
                        val clamped = clampToRect(change.position, imageRect)
                        currentPoints = currentPoints + clamped
                        // 实时预览：以 currentStroke 形式回写
                        onBrushStateChanged(
                            brushState.copy(
                                currentStroke = BrushStroke(
                                    points = currentPoints.map { screenToNormalized(it, imageRect) },
                                    size = brushState.brushSize,
                                    hardness = brushState.brushHardness,
                                    opacity = brushState.brushOpacity,
                                    isEraser = brushState.isEraser
                                )
                            )
                        )
                    }
                },
                onDragEnd = {
                    if (isDrawing && currentPoints.isNotEmpty()) {
                        val stroke = BrushStroke(
                            points = currentPoints.map { screenToNormalized(it, imageRect) },
                            size = brushState.brushSize,
                            hardness = brushState.brushHardness,
                            opacity = brushState.brushOpacity,
                            isEraser = brushState.isEraser
                        )
                        onBrushStateChanged(
                            brushState.copy(
                                strokes = brushState.strokes + stroke,
                                currentStroke = null
                            )
                        )
                    }
                    currentPoints = emptyList()
                    isDrawing = false
                },
                onDragCancel = {
                    onBrushStateChanged(brushState.copy(currentStroke = null))
                    currentPoints = emptyList()
                    isDrawing = false
                }
            )
        }
    } else {
        // 导航模式：消费 transform 事件以避免笔触覆盖层阻挡下层缩放/平移。
        // 注意：这里仅消费但不改变状态，下层 ZoomableImageView 自身的
        // pointerInput 仍会接收到这些事件并完成缩放/平移（Compose 的
        // pointerInput 是并行的，事件不会被此处"吞掉"）。
        Modifier.pointerInput(brushState.isDrawingMode) {
            detectTransformGestures { _, _, _, _ ->
                // 不做任何事 — 仅消费事件以避免冲突
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(drawingModifier)
    ) {
        // ── 1. 已落定笔触 ──
        brushState.strokes.forEach { stroke -> drawStroke(stroke, imageRect) }

        // ── 2. 实时预览的当前笔触 ──
        brushState.currentStroke?.let { stroke -> drawStroke(stroke, imageRect) }
    }
}

// ──────────────────────────────────────────────────────────────────────
// 绘制原语
// ──────────────────────────────────────────────────────────────────────

/**
 * 绘制单条笔触。蒙版预览模式下使用红色半透明，橡皮擦使用 BlendMode.Clear。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    stroke: BrushStroke,
    imageRect: Rect
) {
    if (stroke.points.isEmpty()) return

    // 将归一化坐标映射回屏幕坐标
    val screenPoints = stroke.points.map { normalizedToScreen(it, imageRect) }
    // 笔触半径（屏幕像素）= 归一化大小 * 图片显示宽度
    val radiusPx = (stroke.size * imageRect.width).coerceAtLeast(1f)

    val baseColor = if (stroke.isEraser) Color.Transparent
    else Color.Red.copy(alpha = stroke.opacity.coerceIn(0f, 1f))

    when {
        stroke.isEraser -> {
            // 橡皮擦：清除已绘制区域
            drawStrokePath(screenPoints, radiusPx, Color.Transparent, stroke.hardness, BlendMode.Clear)
        }
        else -> {
            // 普通笔触：多层叠加模拟边缘羽化
            drawStrokePath(screenPoints, radiusPx, baseColor, stroke.hardness, BlendMode.SrcOver)
        }
    }
}

/**
 * 沿笔触路径绘制圆盘连接的线条，使用贝塞尔曲线平滑路径。
 * 硬度通过多圈同心圆叠加实现：硬度=1 时单圈实心；硬度=0 时多圈渐隐。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePath(
    points: List<Offset>,
    radiusPx: Float,
    color: Color,
    hardness: Float,
    blendMode: BlendMode
) {
    if (points.isEmpty()) return

    val safeHardness = hardness.coerceIn(0f, 1f)
    // 越低硬度 → 越多圈外发光层
    val layers = if (safeHardness >= 0.99f) 1 else (1 + ((1f - safeHardness) * 6f).toInt())
    val coreAlpha = color.alpha

    for (layer in 0 until layers) {
        val t = if (layers == 1) 0f else layer.toFloat() / (layers - 1)
        // 外层 t=0 → 半径放大、透明度低；内层 t=1 → 半径正常、透明度高
        val layerRadius = radiusPx * (1f + (1f - t) * 0.6f * (1f - safeHardness))
        val layerAlpha = coreAlpha * (0.15f + 0.85f * t)
        val layerColor = color.copy(alpha = layerAlpha)

        // 1) 沿路径绘制平滑曲线作为带状主体
        val path = buildSmoothPath(points)
        drawPath(
            path = path,
            color = layerColor,
            style = Stroke(
                width = layerRadius * 2f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            ),
            blendMode = blendMode
        )

        // 2) 在每个点上画圆盘，确保端点与稀疏点也填充完整
        for (p in points) {
            drawCircle(
                color = layerColor,
                radius = layerRadius,
                center = p,
                blendMode = blendMode
            )
        }
    }

    // 橡皮擦时绘制虚线轮廓，让用户能看清擦除范围
    if (blendMode == BlendMode.Clear) {
        val path = buildSmoothPath(points)
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.5f),
            style = Stroke(
                width = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            )
        )
    }
}

/**
 * 用三次贝塞尔曲线（mid-point 算法）把折线点平滑连接为 Path。
 * 单点时退化为不绘制路径（仅靠圆盘填充）。
 */
private fun buildSmoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) return path
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }
    for (i in 1 until points.size - 1) {
        val p0 = points[i - 1]
        val p1 = points[i]
        val p2 = points[i + 1]
        // 控制点取相邻点的中点，端点切线方向平滑
        val mid1 = Offset((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
        val mid2 = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
        path.cubicTo(mid1.x, mid1.y, p1.x, p1.y, mid2.x, mid2.y)
    }
    // 末段
    val last = points.last()
    path.lineTo(last.x, last.y)
    return path
}

// ──────────────────────────────────────────────────────────────────────
// 坐标变换
// ──────────────────────────────────────────────────────────────────────

/** 屏幕坐标 → 图片归一化坐标 (0-1)。点可能在图片区域外（钳制后为负或 >1）。 */
private fun screenToNormalized(screen: Offset, imageRect: Rect): Offset {
    val size = imageRect.size
    if (size.width <= 0f || size.height <= 0f) return Offset.Zero
    val nx = (screen.x - imageRect.left) / size.width
    val ny = (screen.y - imageRect.top) / size.height
    return Offset(nx, ny)
}

/** 图片归一化坐标 (0-1) → 屏幕坐标。 */
private fun normalizedToScreen(normalized: Offset, imageRect: Rect): Offset {
    return Offset(
        x = imageRect.left + normalized.x * imageRect.width,
        y = imageRect.top + normalized.y * imageRect.height
    )
}

/** 把屏幕坐标钳制到图片矩形内。 */
private fun clampToRect(point: Offset, rect: Rect): Offset {
    return Offset(
        x = point.x.coerceIn(rect.left, rect.right),
        y = point.y.coerceIn(rect.top, rect.bottom)
    )
}
