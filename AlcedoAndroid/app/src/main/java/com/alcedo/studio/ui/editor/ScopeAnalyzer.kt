package com.alcedo.studio.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Scope analyzer widget that wraps HistogramView, WaveformView, VectorscopeView
 * and ChromaticityView with tab switching. The active tab determines which scope
 * is rendered; all share the same preview bitmap source.
 */
@Composable
fun ScopeAnalyzer(
    bitmap: Bitmap?,
    histogramBins: Array<IntArray>? = null,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(s.histogram, s.waveform, s.vectorscope, s.chromaticity)

    Column(modifier = modifier) {
        SectionHeader(title = s.scopes)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = AlcedoColors.Graphite,
            contentColor = AlcedoColors.AccentBlue,
        ) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        androidx.compose.material3.Text(
                            label,
                            maxLines = 1,
                            color = if (selectedTab == index) AlcedoColors.AccentBlue else AlcedoColors.TextTertiary,
                        )
                    },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DesignTokens.scopeHeight)
                .padding(top = DesignTokens.spacingSm),
        ) {
            when (selectedTab) {
                0 -> HistogramView(
                    bitmap = bitmap,
                    modifier = Modifier.fillMaxSize(),
                )
                1 -> WaveformView(
                    bitmap = bitmap,
                    paradeMode = true,
                    modifier = Modifier.fillMaxSize(),
                )
                2 -> VectorscopeView(
                    bitmap = bitmap,
                    modifier = Modifier.fillMaxSize(),
                )
                3 -> ChromaticityView(
                    bitmap = bitmap,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
