package com.alcedo.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/** The eight hue bands of the HSL tool, each with a representative colour. */
private val HslBands = listOf(
    Triple(Strings.res.bandRed, 0, Color(0xFFFF5C5C)),
    Triple(Strings.res.bandOrange, 1, Color(0xFFFFA040)),
    Triple(Strings.res.bandYellow, 2, Color(0xFFFFD24C)),
    Triple(Strings.res.bandGreen, 3, Color(0xFF4CD08C)),
    Triple(Strings.res.bandAqua, 4, Color(0xFF40E0D0)),
    Triple(Strings.res.bandBlue, 5, Color(0xFF4A9EFF)),
    Triple(Strings.res.bandPurple, 6, Color(0xFFB05CFF)),
    Triple(Strings.res.bandMagenta, 7, Color(0xFFFF5CC8)),
)

/**
 * HSL profile panel: eight hue bands, each with hue/saturation/luminance
 * sliders. A colour-tab selector picks the active band; the three sliders edit
 * the corresponding entries in [AdjustmentParams.hslHueShift],
 * [AdjustmentParams.hslSaturation] and [AdjustmentParams.hslLuminance].
 *
 * Because the params store the bands as [FloatArray]s (not individually named
 * fields), the panel mutates a local copy and reports the full array back via
 * [onUpdate] using field names `hslHueShift`, `hslSaturation`, `hslLuminance`
 * encoded as a single concatenated value is impractical; instead the host is
 * expected to read the live array. For simplicity here we expose the sliders
 * and call [onUpdate] with a synthetic index-tagged field so the ViewModel can
 * route the change.
 */
@Composable
fun HlsProfilePanel(
    params: AdjustmentParams,
    onUpdate: (String, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    var activeBand by remember { mutableIntStateOf(0) }
    val band = HslBands[activeBand]
    val index = band.second

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.controlSpacing)) {
        SectionHeader(title = s.hsl)
        // Band selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
        ) {
            HslBands.forEachIndexed { i, triple ->
                val selected = i == activeBand
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .background(
                            if (selected) AlcedoColors.AccentBlue else AlcedoColors.SurfaceElevated,
                            CircleShape,
                        )
                        .padding(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(triple.third, CircleShape),
                    )
                }
            }
        }
        Text(text = band.first, style = MaterialTheme.typography.labelMedium, color = AlcedoColors.TextSecondary)

        AdjustmentSlider(
            label = s.hue, value = params.hslHueShift[index], defaultValue = 0f,
            range = -180f..180f, valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("hslHueShift[$index]", it) },
        )
        AdjustmentSlider(
            label = s.saturationBand, value = params.hslSaturation[index], defaultValue = 0f,
            range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("hslSaturation[$index]", it) },
        )
        AdjustmentSlider(
            label = s.luminance, value = params.hslLuminance[index], defaultValue = 0f,
            range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("hslLuminance[$index]", it) },
        )
    }
}
