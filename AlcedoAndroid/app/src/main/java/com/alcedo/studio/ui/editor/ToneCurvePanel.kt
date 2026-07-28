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
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.data.model.CurvePoint
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Tone curve panel. Wraps the interactive [ToneCurveView] with a channel
 * selector (Master/Red/Green/Blue) and routes edited control points back to the
 * host via [onPointsChange]. The active channel determines which curve in
 * [AdjustmentParams] is edited.
 */
@Composable
fun ToneCurvePanel(
    params: AdjustmentParams,
    onPointsChange: (List<CurvePoint>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    var channel by remember { mutableIntStateOf(0) }
    val channels = listOf(s.curveMaster, s.curveRed, s.curveGreen, s.curveBlue)
    val activeCurve = when (channel) {
        0 -> params.toneCurveMaster
        1 -> params.toneCurveRed
        2 -> params.toneCurveGreen
        else -> params.toneCurveBlue
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm)) {
        SectionHeader(title = s.panelToneCurve)
        TabRow(
            selectedTabIndex = channel,
            containerColor = AlcedoColors.Graphite,
            contentColor = AlcedoColors.AccentBlue,
        ) {
            channels.forEachIndexed { index, label ->
                Tab(
                    selected = channel == index,
                    onClick = { channel = index },
                    text = { Text(label, maxLines = 1) },
                )
            }
        }
        ToneCurveView(
            points = activeCurve,
            onPointsChange = onPointsChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(DesignTokens.scopeHeight)
                .padding(top = DesignTokens.spacingSm),
        )
        Text(
            text = s.addPoint + " · " + s.removePoint,
            style = MaterialTheme.typography.labelSmall,
            color = AlcedoColors.TextTertiary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
