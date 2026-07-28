package com.alcedo.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.LinearScale
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.MaskKind
import com.alcedo.studio.data.model.MaskRecord
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Mask panel. Lists existing masks with enable toggles and remove actions, plus
 * a row of "add mask" buttons (brush, radial, linear, luminance). AI subject/sky
 * masks are added through the AI sidecar and appear in the same list.
 */
@Composable
fun MaskPanel(
    masks: List<MaskRecord>,
    onAddBrush: () -> Unit,
    onAddRadial: () -> Unit,
    onAddLinear: () -> Unit,
    onAddLuminance: () -> Unit,
    onToggle: (MaskRecord) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm)) {
        SectionHeader(title = s.addMask)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
        ) {
            MaskAddButton(s.maskBrush, Icons.Outlined.Brush, onAddBrush, Modifier.weight(1f))
            MaskAddButton(s.maskRadial, Icons.Outlined.Circle, onAddRadial, Modifier.weight(1f))
            MaskAddButton(s.maskLinear, Icons.Outlined.LinearScale, onAddLinear, Modifier.weight(1f))
            MaskAddButton(s.maskLuminance, Icons.Outlined.WbSunny, onAddLuminance, Modifier.weight(1f))
        }

        SectionHeader(title = s.masks + " (${masks.size})")
        if (masks.isEmpty()) {
            Text(
                text = "—",
                color = AlcedoColors.TextTertiary,
                modifier = Modifier.padding(DesignTokens.spacingSm),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(masks, key = { it.id }) { mask ->
                    MaskRow(
                        mask = mask,
                        onToggle = { onToggle(mask) },
                        onRemove = { onRemove(mask.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MaskAddButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = AlcedoColors.SurfaceElevated),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.spacingSm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = AlcedoColors.AccentBlue, modifier = Modifier.size(20.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = AlcedoColors.TextSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun MaskRow(
    mask: MaskRecord,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlcedoColors.SurfaceRaised, MaterialTheme.shapes.small)
            .padding(horizontal = DesignTokens.spacingMd, vertical = DesignTokens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    if (mask.enabled) AlcedoColors.AccentBlue else AlcedoColors.TextDisabled,
                    CircleShape,
                ),
        )
        Text(
            text = mask.name + " · " + mask.kind.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (mask.enabled) AlcedoColors.TextPrimary else AlcedoColors.TextTertiary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        TextButton(onClick = onToggle) {
            Text(if (mask.enabled) "ON" else "OFF", color = AlcedoColors.AccentBlue)
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Outlined.Tune, contentDescription = "Remove", tint = AlcedoColors.TextTertiary)
        }
    }
}
