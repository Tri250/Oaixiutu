package com.alcedo.studio.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alcedo.studio.domain.service.AlbumBrowseService
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.AlcedoMonoStyle
import com.alcedo.studio.ui.theme.DesignTokens
import com.alcedo.studio.ui.theme.ThumbnailShape

/**
 * A compact statistics card grid summarising the album: total images, RAW
 * count, picks/rejects, rated/tagged counts, plus breakdowns by camera and
 * lens. Driven by [AlbumBrowseService.AlbumStats] plus the camera/lens lists.
 */
@Composable
fun StatsView(
    stats: AlbumBrowseService.AlbumStats?,
    cameras: List<String>,
    lenses: List<String>,
    ratedCount: Int,
    taggedCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(DesignTokens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
    ) {
        Text(
            text = "STATISTICS",
            style = MaterialTheme.typography.labelMedium,
            color = AlcedoColors.TextTertiary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            StatCard("Total", stats?.totalImages?.toString() ?: "—", Modifier.weight(1f))
            StatCard("RAW", stats?.rawImages?.toString() ?: "—", Modifier.weight(1f))
            StatCard("Picks", stats?.picks?.toString() ?: "—", Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            StatCard("Rejects", stats?.rejects?.toString() ?: "—", Modifier.weight(1f))
            StatCard("Rated", ratedCount.toString(), Modifier.weight(1f))
            StatCard("Tagged", taggedCount.toString(), Modifier.weight(1f))
        }
        StatCard("Folders", stats?.folders?.toString() ?: "—", Modifier.fillMaxWidth())

        if (cameras.isNotEmpty()) {
            BreakdownList("By Camera", cameras)
        }
        if (lenses.isNotEmpty()) {
            BreakdownList("By Lens", lenses)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(AlcedoColors.SurfaceElevated, RoundedCornerShape(DesignTokens.radiusMd))
            .padding(DesignTokens.spacingSm),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = value, style = AlcedoMonoStyle, color = AlcedoColors.TextPrimary)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = AlcedoColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BreakdownList(title: String, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
        Text(text = title.uppercase(), style = MaterialTheme.typography.labelMedium, color = AlcedoColors.TextTertiary)
        items.take(10).forEach { name ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(AlcedoColors.AccentBlue, ThumbnailShape),
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = AlcedoColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
