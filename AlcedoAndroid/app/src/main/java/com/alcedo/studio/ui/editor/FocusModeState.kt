package com.alcedo.studio.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Focus peaking state management. Controls enable/disable, sensitivity
 * threshold, and the colour overlay used for focus peaking visualization.
 * Focus peaking highlights high-contrast edges (indicating sharp focus)
 * in the viewport overlay.
 */
@Stable
class FocusModeState(
    initialEnabled: Boolean = false,
    initialSensitivity: Float = 0.5f,
    initialColor: Color = Color(0xFF00FF00),
) {
    var enabled by mutableStateOf(initialEnabled)
    var sensitivity by mutableStateOf(initialSensitivity)
    var overlayColor by mutableStateOf(initialColor)

    /** Sensitivity in the 0..1 range maps to a threshold: lower = more edges shown. */
    val threshold: Float
        get() = 1f - sensitivity

    fun toggle() { enabled = !enabled }

    fun reset() {
        enabled = false
        sensitivity = 0.5f
        overlayColor = Color(0xFF00FF00)
    }
}

/**
 * Remember a [FocusModeState] instance that survives recomposition.
 */
@Composable
fun rememberFocusModeState(
    initialEnabled: Boolean = false,
    initialSensitivity: Float = 0.5f,
    initialColor: Color = Color(0xFF00FF00),
): FocusModeState {
    return remember {
        FocusModeState(initialEnabled, initialSensitivity, initialColor)
    }
}
