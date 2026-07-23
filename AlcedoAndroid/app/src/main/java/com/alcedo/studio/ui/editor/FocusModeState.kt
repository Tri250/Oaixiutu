package com.alcedo.studio.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.alcedo.studio.ui.theme.AlcedoSpacing

/**
 * Holds the Focus Mode state for the editor.
 *
 * Inspired by RapidRAW's "Focus Mode": when enabled, every adjustment section
 * auto-collapses except the currently active one, leaving a clean,
 * distraction-free editing surface. Tapping a section chip selects it as the
 * active section while the rest stay collapsed.
 */
class FocusModeState {
    var enabled: Boolean by mutableStateOf(false)
        private set

    var activeSection: String? by mutableStateOf(null)
        private set

    fun toggle() {
        enabled = !enabled
        if (!enabled) activeSection = null
    }

    fun selectSection(section: String) {
        if (enabled) activeSection = section
    }

    fun shouldShowSection(section: String): Boolean {
        return !enabled || activeSection == section
    }
}

/**
 * Renders a compact row of chips used to pick the active section while Focus
 * Mode is on. When Focus Mode is off this composable renders nothing.
 *
 * It also guarantees that one of [sections] is always selected as the active
 * section while Focus Mode is on — falling back to the first section when the
 * currently active section doesn't belong to this group (e.g. after switching
 * editor panels).
 *
 * @param focusMode the shared [FocusModeState] for the editor.
 * @param sections  the sections exposed by the current panel, as (id, label).
 */
@Composable
fun FocusSectionChips(
    focusMode: FocusModeState,
    sections: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    if (!focusMode.enabled || sections.isEmpty()) return

    val sectionIds = sections.map { it.first }
    LaunchedEffect(focusMode.enabled, sectionIds) {
        if (focusMode.activeSection == null || focusMode.activeSection !in sectionIds) {
            focusMode.selectSection(sectionIds.first())
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.md)
    ) {
        sections.forEach { (id, label) ->
            FilterChip(
                selected = focusMode.activeSection == id,
                onClick = { focusMode.selectSection(id) },
                label = {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            )
        }
    }
}
