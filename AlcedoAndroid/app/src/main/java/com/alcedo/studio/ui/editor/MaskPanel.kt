package com.alcedo.studio.ui.editor

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LinearScale
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.MaskKind
import com.alcedo.studio.data.model.MaskRecord
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Mask panel. Lists existing masks with enable toggles, per-mask controls
 * (opacity, feather, invert), and remove actions. "Add mask" row includes
 * brush, radial, linear, luminance, plus AI subject/sky masks.
 */
@Composable
fun MaskPanel(
    masks: List<MaskRecord>,
    onAddBrush: () -> Unit,
    onAddRadial: () -> Unit,
    onAddLinear: () -> Unit,
    onAddLuminance: () -> Unit,
    onAddSubject: () -> Unit = {},
    onAddSky: () -> Unit = {},
    onToggle: (MaskRecord) -> Unit,
    onRemove: (String) -> Unit,
    onUpdateMaskParam: (String, String, Float) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm)) {
        SectionHeader(title = s.addMask)

        // Primary mask types
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
        ) {
            MaskAddButton(s.maskBrush, Icons.Outlined.Brush, onAddBrush, Modifier.weight(1f))
            MaskAddButton(s.maskRadial, Icons.Outlined.Circle, onAddRadial, Modifier.weight(1f))
            MaskAddButton(s.maskLinear, Icons.Outlined.LinearScale, onAddLinear, Modifier.weight(1f))
            MaskAddButton(s.maskLuminance, Icons.Outlined.WbSunny, onAddLuminance, Modifier.weight(1f))
        }

        // AI mask types
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
        ) {
            MaskAddButton(s.maskSubject, Icons.Outlined.Tune, onAddSubject, Modifier.weight(1f))
            MaskAddButton(s.maskSky, Icons.Outlined.Tune, onAddSky, Modifier.weight(1f))
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
                        onUpdateParam = { param, value -> onUpdateMaskParam(mask.id, param, value) },
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
    onUpdateParam: (String, Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = AlcedoColors.SurfaceRaised),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                    text = mask.name + " · " + mask.kind.displayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mask.enabled) AlcedoColors.TextPrimary else AlcedoColors.TextTertiary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Switch(
                    checked = mask.enabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.size(32.dp),
                )
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = "Expand",
                        tint = AlcedoColors.TextTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Remove",
                        tint = AlcedoColors.TextTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Expanded detail controls
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.spacingMd)
                        .padding(bottom = DesignTokens.spacingSm),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
                ) {
                    AdjustmentSlider(
                        label = Strings.res.opacity,
                        value = mask.opacity,
                        defaultValue = 1f,
                        range = 0f..1f,
                        valueFormatter = { "%d%%".format((it * 100).toInt()) },
                        onValueChange = { onUpdateParam("opacity", it) },
                    )
                    AdjustmentSlider(
                        label = Strings.res.feather,
                        value = mask.feather,
                        defaultValue = 0f,
                        range = 0f..1f,
                        valueFormatter = { "%d%%".format((it * 100).toInt()) },
                        onValueChange = { onUpdateParam("feather", it) },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = Strings.res.invert,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlcedoColors.TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = mask.invert,
                            onCheckedChange = { onUpdateParam("invert", if (it) 1f else 0f) },
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun MaskKind.displayName(): String = when (this) {
    MaskKind.BRUSH -> Strings.res.maskBrush
    MaskKind.RADIAL -> Strings.res.maskRadial
    MaskKind.LINEAR_GRADIENT -> Strings.res.maskLinear
    MaskKind.RANGE_LUMINANCE -> Strings.res.maskLuminance
    MaskKind.RANGE_COLOR -> "Color"
    MaskKind.SUBJECT -> Strings.res.maskSubject
    MaskKind.BACKGROUND -> "Background"
    MaskKind.SKY -> Strings.res.maskSky
    MaskKind.OBJECT -> "Object"
}
