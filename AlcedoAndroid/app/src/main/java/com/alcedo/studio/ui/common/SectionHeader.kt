package com.alcedo.studio.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.theme.*

@Composable
fun SectionHeader(
    title: String,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "chevron"
    )
    val alcedoColors = LocalAlcedoColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AlcedoRadius.md),
        tonalElevation = AlcedoElevation.level1.dp,
        color = alcedoColors.bgPanel
    ) {
        Column(modifier = Modifier.padding(vertical = AlcedoSpacing.xs)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = AlcedoSpacing.lg, vertical = AlcedoSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    title,
                    style = AlcedoFontRoles.uiTitle,
                    color = alcedoColors.text,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = CircleShape,
                    color = alcedoColors.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(AlcedoIconSize.xl)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = if (expanded) stringRes { sectionCollapse } else stringRes { sectionExpand },
                            modifier = Modifier
                                .size(AlcedoIconSize.md)
                                .rotate(rotation),
                            tint = alcedoColors.icon
                        )
                    }
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(horizontal = AlcedoSpacing.lg, vertical = AlcedoSpacing.sm),
                    content = content
                )
            }
        }
    }
}
