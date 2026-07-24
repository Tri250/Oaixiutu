package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.ImageModel
import com.alcedo.studio.i18n.StringResources
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.theme.AlcedoSpacing

/**
 * Inspector tab types for the integrated inspector panel.
 */
enum class InspectorTab(val labelKey: StringResources.() -> String) {
    HISTOGRAM({ editorHistogram }),
    WAVEFORM({ editorWaveform }),
    VECTORSCOPE({ editorVectorscope }),
    INFO({ inspectorFileInfo })
}

/**
 * Integrated inspector panel with tabs for:
 * - **Histogram**: RGB/Luminance/Parade histogram with clipping indicators
 * - **Waveform**: Luminance/R/G/B/Overlay/Parade waveform scope
 * - **Vectorscope**: Cb-Cr chrominance plot with 75% color bar targets
 * - **Info**: Image metadata (dimensions, format, color space, file size)
 *
 * Each scope updates in real-time when image changes (with debouncing).
 * Uses efficient bitmap sampling for large images and
 * Alcedo Design System colors for styling.
 */
@Composable
fun InspectorPanel(
    histogramData: HistogramData,
    waveformData: WaveformData,
    vectorscopeData: VectorscopeData,
    image: ImageModel?,
    modifier: Modifier = Modifier,
    initialTab: InspectorTab = InspectorTab.HISTOGRAM,
    histogramChannel: HistogramChannel = HistogramChannel.RGB,
    onHistogramChannelChange: (HistogramChannel) -> Unit = {},
    histogramScale: HistogramScale = HistogramScale.LINEAR,
    onHistogramScaleChange: (HistogramScale) -> Unit = {},
    showClippingWarning: Boolean = true,
    onToggleClippingWarning: () -> Unit = {},
    scopeChannel: ScopeChannel = ScopeChannel.RGB_PARADE,
    onScopeChannelChange: (ScopeChannel) -> Unit = {},
    isComputing: Boolean = false
) {
    var selectedTab by remember { mutableStateOf(initialTab) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)
    ) {
        // Tab row
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            divider = {}
        ) {
            InspectorTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = stringRes(tab.labelKey),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }

        // Computing indicator
        if (isComputing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            )
        }

        // Tab content
        when (selectedTab) {
            InspectorTab.HISTOGRAM -> {
                HistogramView(
                    histogramData = histogramData,
                    showChannels = histogramChannel,
                    scale = histogramScale,
                    showClippingWarning = showClippingWarning,
                    modifier = Modifier.fillMaxWidth()
                )
                // Channel selector chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    HistogramChannel.entries.forEach { ch ->
                        val color = when (ch) {
                            HistogramChannel.RGB -> MaterialTheme.colorScheme.onSurface
                            HistogramChannel.RED -> Color.Red
                            HistogramChannel.GREEN -> Color.Green
                            HistogramChannel.BLUE -> Color(0xFF42A5F5)
                            HistogramChannel.LUMINANCE -> MaterialTheme.colorScheme.onSurface
                            HistogramChannel.RGB_PARADE -> MaterialTheme.colorScheme.onSurface
                        }
                        FilterChip(
                            selected = histogramChannel == ch,
                            onClick = { onHistogramChannelChange(ch) },
                            label = {
                                Text(
                                    ch.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (histogramChannel == ch) color
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.height(26.dp)
                        )
                        Spacer(modifier = Modifier.width(AlcedoSpacing.xs))
                    }
                }
                // Scale + clipping warning toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HistogramScale.entries.forEach { sc ->
                        FilterChip(
                            selected = histogramScale == sc,
                            onClick = { onHistogramScaleChange(sc) },
                            label = {
                                Text(
                                    sc.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(26.dp)
                        )
                        Spacer(modifier = Modifier.width(AlcedoSpacing.xs))
                    }
                    Spacer(modifier = Modifier.width(AlcedoSpacing.sm))
                    FilterChip(
                        selected = showClippingWarning,
                        onClick = onToggleClippingWarning,
                        label = {
                            Text(
                                "Clipping",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(26.dp)
                    )
                }
            }

            InspectorTab.WAVEFORM -> {
                WaveformScope(
                    waveformData = waveformData,
                    channel = scopeChannel,
                    modifier = Modifier.fillMaxWidth()
                )
                // Channel selector chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    ScopeChannel.entries.forEach { ch ->
                        FilterChip(
                            selected = scopeChannel == ch,
                            onClick = { onScopeChannelChange(ch) },
                            label = {
                                Text(
                                    ch.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(26.dp)
                        )
                        Spacer(modifier = Modifier.width(AlcedoSpacing.xs))
                    )
                }
            }

            InspectorTab.VECTORSCOPE -> {
                VectorscopeView(
                    vectorscopeData = vectorscopeData,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            InspectorTab.INFO -> {
                ImageInspectorPanel(
                    image = image,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
