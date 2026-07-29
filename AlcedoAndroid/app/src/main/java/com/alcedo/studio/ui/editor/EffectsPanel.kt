package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/** Film simulation LUT preset entries for the LUT browser grid. */
private data class FilmLutPreset(val name: String, val category: String)

private val BUILT_IN_LUTS = listOf(
    FilmLutPreset("Kodak Portra 160", "Kodak"),
    FilmLutPreset("Kodak Portra 400", "Kodak"),
    FilmLutPreset("Kodak Ektar 100", "Kodak"),
    FilmLutPreset("Kodak Gold 200", "Kodak"),
    FilmLutPreset("Fuji Pro 400H", "Fuji"),
    FilmLutPreset("Fuji Superia 400", "Fuji"),
    FilmLutPreset("Fuji Velvia 50", "Fuji"),
    FilmLutPreset("Agfa Vista 200", "Agfa"),
    FilmLutPreset("Agfa APX 400", "Agfa"),
    FilmLutPreset("Ilford HP5 Plus", "B&W"),
    FilmLutPreset("Ilford Delta 3200", "B&W"),
    FilmLutPreset("Kodak Tri-X 400", "B&W"),
    FilmLutPreset("Kodak T-Max 400", "B&W"),
    FilmLutPreset("Cinestill 800T", "Cine"),
    FilmLutPreset("Kodak 2383", "Cine"),
)

/**
 * Effects panel matching desktop: Film Grain (amount, size, roughness),
 * Halation (amount, radius, threshold), and LUT browser (grid of film
 * simulation LUTs).
 */
@Composable
fun EffectsPanel(
    params: AdjustmentParams,
    onUpdate: (String, Float) -> Unit,
    modifier: Modifier = Modifier,
    onLoadLut: () -> Unit = {},
    onClearLut: () -> Unit = {},
    onSelectBuiltInLut: (String) -> Unit = {},
) {
    val s = Strings.res
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.controlSpacing)) {
        SectionHeader(title = s.filmGrain)
        AdjustmentSlider(
            label = s.grainAmount,
            value = params.filmGrainAmount,
            defaultValue = 0f,
            range = 0f..100f,
            valueFormatter = { "%.0f".format(it) },
            onValueChange = { onUpdate("filmGrainAmount", it) },
        )
        AdjustmentSlider(
            label = s.grainSize,
            value = params.filmGrainSize,
            defaultValue = 1f,
            range = 0.5f..4f,
            valueFormatter = { "%.1f".format(it) },
            onValueChange = { onUpdate("filmGrainSize", it) },
        )
        AdjustmentSlider(
            label = s.grainRoughness,
            value = 0.5f,
            defaultValue = 0.5f,
            range = 0f..1f,
            valueFormatter = { "%.2f".format(it) },
            onValueChange = { onUpdate("grainRoughness", it) },
        )

        SectionHeader(title = s.halation)
        AdjustmentSlider(
            label = s.halationIntensity,
            value = params.halationAmount,
            defaultValue = 0f,
            range = 0f..100f,
            valueFormatter = { "%.0f".format(it) },
            onValueChange = { onUpdate("halationAmount", it) },
        )
        AdjustmentSlider(
            label = s.halationSpread,
            value = 0.5f,
            defaultValue = 0.5f,
            range = 0f..1f,
            valueFormatter = { "%.2f".format(it) },
            onValueChange = { onUpdate("halationRadius", it) },
        )
        AdjustmentSlider(
            label = s.threshold,
            value = 0.8f,
            defaultValue = 0.8f,
            range = 0f..1f,
            valueFormatter = { "%.2f".format(it) },
            onValueChange = { onUpdate("halationThreshold", it) },
        )

        SectionHeader(title = s.luts)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            OutlinedButton(onClick = onLoadLut, modifier = Modifier.weight(1f)) {
                Text(s.loadLut)
            }
            if (params.lutPath != null) {
                OutlinedButton(onClick = onClearLut, modifier = Modifier.weight(1f)) {
                    Text("✕")
                }
            }
        }
        AdjustmentSlider(
            label = s.lutIntensity,
            value = params.lutIntensity,
            defaultValue = 1f,
            range = 0f..1f,
            valueFormatter = { "%d%%".format((it * 100).toInt()) },
            onValueChange = { onUpdate("lutIntensity", it) },
            enabled = params.lutPath != null,
        )
        params.lutPath?.let { path ->
            Text(
                text = path.substringAfterLast('/'),
                color = AlcedoColors.TextTertiary,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Built-in film LUT browser
        SectionHeader(title = s.filmSimulations)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
            modifier = Modifier.fillMaxWidth().size(height = 200.dp, width = androidx.compose.ui.unit.Dp.Unspecified),
        ) {
            items(BUILT_IN_LUTS, key = { it.name }) { lut ->
                Card(
                    onClick = { onSelectBuiltInLut(lut.name) },
                    colors = CardDefaults.cardColors(containerColor = AlcedoColors.SurfaceElevated),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(DesignTokens.spacingXs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = lut.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = AlcedoColors.TextPrimary,
                            maxLines = 2,
                        )
                        Text(
                            text = lut.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = AlcedoColors.TextTertiary,
                        )
                    }
                }
            }
        }
    }
}
