package com.alcedo.studio.ui.accessibility

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState

/**
 * Accessibility modifiers for the Alcedo UI. These keep content descriptions,
 * roles and state hints consistent across the editor without duplicating
 * string literals at every call site.
 */

/** Tags a composable as a section heading for screen-reader navigation. */
fun Modifier.heading(): Modifier = this.semantics { heading() }

/** Attaches a content description (used for purely visual elements). */
fun Modifier.contentDesc(description: String): Modifier =
    this.semantics { contentDescription = description }

/** Marks a composable as a slider with a readable state description. */
fun Modifier.sliderRole(state: String): Modifier = this.semantics {
    stateDescription = state
}

/** Marks a composable as a toggle and reports its on/off state. */
fun Modifier.toggleRole(isOn: Boolean): Modifier = this.semantics {
    role = Role.Switch
    toggleableState = if (isOn) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off
    stateDescription = if (isOn) "On" else "Off"
}

/** Marks a composable as a button with an accessible label. */
fun Modifier.buttonRole(label: String): Modifier = this.semantics {
    role = Role.Button
    contentDescription = label
}
