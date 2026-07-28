package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.data.model.CurvePoint
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Tone curve panel V2 with Hermite spline interpolation. Supports RGB composite
 * and R/G/B individual channels. Uses [ToneCurveView] as the interactive widget.
 * Draggable control points, long-press to delete, double-tap to reset.
 * The curve is overlaid on a histogram for visual reference.
 */
@Composable
fun CurvePanelV2(
    params: AdjustmentParams,
    onMasterCurveChange: (List<CurvePoint>) -> Unit,
    onRedCurveChange: (List<CurvePoint>) -> Unit,
    onGreenCurveChange: (List<CurvePoint>) -> Unit,
    onBlueCurveChange: (List<CurvePoint>) -> Unit,
    histogramBins: Array<IntArray>? = null,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    var channel by remember { mutableIntStateOf(0) }
    val channelLabels = listOf(s.curveMaster, s.curveRed, s.curveGreen, s.curveBlue)
    val channelColors = listOf(
        AlcedoColors.LumaTrace,
        AlcedoColors.ChannelRed,
        AlcedoColors.ChannelGreen,
        AlcedoColors.ChannelBlue,
    )

    val activeCurve = when (channel) {
        0 -> params.toneCurveMaster
        1 -> params.toneCurveRed
        2 -> params.toneCurveGreen
        else -> params.toneCurveBlue
    }

    val onCurveChange = when (channel) {
        0 -> onMasterCurveChange
        1 -> onRedCurveChange
        2 -> onGreenCurveChange
        else -> onBlueCurveChange
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm)) {
        SectionHeader(title = s.panelToneCurve)

        TabRow(
            selectedTabIndex = channel,
            containerColor = AlcedoColors.Graphite,
            contentColor = AlcedoColors.AccentBlue,
        ) {
            channelLabels.forEachIndexed { index, label ->
                Tab(
                    selected = channel == index,
                    onClick = { channel = index },
                    text = {
                        Text(
                            label,
                            maxLines = 1,
                            color = if (channel == index) channelColors[index] else AlcedoColors.TextTertiary,
                        )
                    },
                )
            }
        }

        ToneCurveView(
            points = activeCurve,
            onPointsChange = onCurveChange,
            curveColor = channelColors[channel],
            histogramBins = histogramBins,
            modifier = Modifier
                .fillMaxWidth()
                .height(DesignTokens.scopeHeight)
                .padding(top = DesignTokens.spacingSm),
        )

        Text(
            text = "Tap to add point · Long-press to delete · Double-tap to reset",
            style = MaterialTheme.typography.labelSmall,
            color = AlcedoColors.TextTertiary,
            modifier = Modifier.fillMaxWidth(),
        )

        // Curve info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Points: ${activeCurve.size}",
                style = MaterialTheme.typography.labelSmall,
                color = AlcedoColors.TextTertiary,
            )
            val isLinear = activeCurve.size == 2 &&
                activeCurve[0].x == 0f && activeCurve[0].y == 0f &&
                activeCurve[1].x == 1f && activeCurve[1].y == 1f
            Text(
                text = if (isLinear) "Linear" else "Modified",
                style = MaterialTheme.typography.labelSmall,
                color = if (isLinear) AlcedoColors.TextTertiary else AlcedoColors.AccentBlue,
            )
        }
    }
}
