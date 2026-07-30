package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.CollapsibleSection
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Tone (Basic) panel matching desktop UI. Hosts exposure (-5 to +5),
 * contrast, highlights, shadows, whites, blacks sliders plus
 * clarity/sharpen pair. Each slider pushes a live value to the pipeline
 * via [onUpdate] and commits on release via [onCommit].
 *
 * 参考 RapidRAW 的 ControlsPanel：将调节项分组到可折叠分区中，
 * 用户可折叠暂不使用的分区以释放屏幕空间，专注当前调整。
 * 分区默认展开，状态在面板生命周期内保持。
 */
@Composable
fun BasicPanel(
    params: AdjustmentParams,
    onUpdate: (String, Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    // 分区折叠状态（参考 RapidRAW collapsibleSectionsState）
    var toneExpanded by remember { mutableStateOf(true) }
    var detailExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
    ) {
        // ---- 基础色调分区 ----
        CollapsibleSection(
            title = s.panelTone,
            expanded = toneExpanded,
            onToggleExpand = { toneExpanded = !toneExpanded },
        ) {
            AdjustmentSlider(
                label = s.exposure, value = params.exposure, defaultValue = 0f,
                range = -5f..5f, valueFormatter = { "%+.2f".format(it) },
                onValueChange = { onUpdate("exposure", it) },
                onValueChangeFinished = onCommit,
            )
            AdjustmentSlider(
                label = s.contrast, value = params.contrast, defaultValue = 0f,
                range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
                onValueChange = { onUpdate("contrast", it) },
                onValueChangeFinished = onCommit,
            )
            AdjustmentSlider(
                label = s.highlights, value = params.highlights, defaultValue = 0f,
                range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
                onValueChange = { onUpdate("highlights", it) },
                onValueChangeFinished = onCommit,
            )
            AdjustmentSlider(
                label = s.shadows, value = params.shadows, defaultValue = 0f,
                range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
                onValueChange = { onUpdate("shadows", it) },
                onValueChangeFinished = onCommit,
            )
            AdjustmentSlider(
                label = s.whites, value = params.whites, defaultValue = 0f,
                range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
                onValueChange = { onUpdate("whites", it) },
                onValueChangeFinished = onCommit,
            )
            AdjustmentSlider(
                label = s.blacks, value = params.blacks, defaultValue = 0f,
                range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
                onValueChange = { onUpdate("blacks", it) },
                onValueChangeFinished = onCommit,
            )
        }

        // ---- 清晰度/锐化分区 ----
        CollapsibleSection(
            title = s.clarity + " / " + s.sharpen,
            expanded = detailExpanded,
            onToggleExpand = { detailExpanded = !detailExpanded },
        ) {
            AdjustmentSlider(
                label = s.clarity, value = params.clarity, defaultValue = 0f,
                range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
                onValueChange = { onUpdate("clarity", it) },
                onValueChangeFinished = onCommit,
            )
            AdjustmentSlider(
                label = s.sharpen, value = params.sharpen, defaultValue = 0f,
                range = 0f..100f, valueFormatter = { "%.0f".format(it) },
                onValueChange = { onUpdate("sharpen", it) },
                onValueChangeFinished = onCommit,
            )
        }
    }
}
