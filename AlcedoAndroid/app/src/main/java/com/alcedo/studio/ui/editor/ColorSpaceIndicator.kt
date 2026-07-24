package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.ColorScience
import com.alcedo.studio.data.model.ColorSpace
import com.alcedo.studio.data.model.EOTF
import com.alcedo.studio.data.model.PipelineParams

/**
 * P2-7 色彩空间工作流提示：以 输入 → 工作色彩空间 → 输出 的链路
 * 直观展示当前图片所经历的色彩空间转换流程。
 */
@Composable
fun ColorSpaceIndicator(
    inputSpace: String,    // "RAW Camera" / "sRGB" / "Adobe RGB"
    workingSpace: String,  // "ACEScg" / "ProPhoto RGB" / "Linear sRGB"
    outputSpace: String,   // "sRGB (Display)" / "Rec.709" / "P3"
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 输入色彩空间
        ColorSpaceChip(inputSpace, ColorSpaceType.INPUT)

        // 箭头
        Icon(
            Icons.AutoMirrored.Filled.ArrowRight,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 工作色彩空间
        ColorSpaceChip(workingSpace, ColorSpaceType.WORKING)

        // 箭头
        Icon(
            Icons.AutoMirrored.Filled.ArrowRight,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 输出色彩空间
        ColorSpaceChip(outputSpace, ColorSpaceType.OUTPUT)
    }
}

enum class ColorSpaceType { INPUT, WORKING, OUTPUT }

@Composable
fun ColorSpaceChip(space: String, type: ColorSpaceType) {
    val color = when (type) {
        ColorSpaceType.INPUT -> MaterialTheme.colorScheme.tertiaryContainer
        ColorSpaceType.WORKING -> MaterialTheme.colorScheme.primaryContainer
        ColorSpaceType.OUTPUT -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when (type) {
        ColorSpaceType.INPUT -> MaterialTheme.colorScheme.onTertiaryContainer
        ColorSpaceType.WORKING -> MaterialTheme.colorScheme.onPrimaryContainer
        ColorSpaceType.OUTPUT -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color,
        contentColor = contentColor
    ) {
        Text(
            text = space,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * 根据 [PipelineParams] 与原始 RAW 标志推导色彩空间流程标签。
 */
fun resolveColorSpaceFlow(
    params: PipelineParams,
    isRaw: Boolean
): Triple<String, String, String> {
    val input = if (isRaw) "RAW Camera" else "sRGB"

    val working = when (params.displayTransform.colorScience) {
        ColorScience.ACES20 -> "ACEScg"
        ColorScience.OPENDRT -> "OpenDRT (Linear)"
        ColorScience.LINEAR -> "Linear sRGB"
    }

    val output = when (params.displayTransform.displayColorSpace) {
        ColorSpace.SRGB -> when (params.displayTransform.eotf) {
            EOTF.SRGB -> "sRGB (Display)"
            EOTF.PQ -> "sRGB (PQ)"
            EOTF.HLG -> "sRGB (HLG)"
            EOTF.GAMMA22 -> "sRGB (2.2)"
            EOTF.GAMMA24 -> "sRGB (2.4)"
        }
        ColorSpace.DISPLAY_P3 -> "Display P3"
        ColorSpace.ADOBE_RGB -> "Adobe RGB"
        ColorSpace.PROPHOTO_RGB -> "ProPhoto RGB"
        ColorSpace.REC2020 -> "Rec.2020"
        ColorSpace.ACES -> "ACES"
        ColorSpace.CUSTOM -> "Custom"
    }

    return Triple(input, working, output)
}
