package com.alcedo.studio.ui.accessibility

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

// Minimum touch target size (Material Design: 48dp)
object AccessibilityDefaults {
    const val MIN_TOUCH_SIZE_DP = 48
}

// Modifier to ensure minimum touch target size
fun Modifier.accessibleTouchTarget(): Modifier = this.sizeIn(
    minWidth = AccessibilityDefaults.MIN_TOUCH_SIZE_DP.dp,
    minHeight = AccessibilityDefaults.MIN_TOUCH_SIZE_DP.dp
)

// Modifier to add content description
fun Modifier.accessibilityDescription(description: String): Modifier = this.semantics {
    contentDescription = description
}

// Modifier to mark a composable as a heading
fun Modifier.accessibilityHeading(): Modifier = this.semantics {
    heading()
}

// Modifier to set state description (e.g., "Selected", "Not selected")
fun Modifier.accessibilityState(stateDescription: String): Modifier = this.semantics {
    this.stateDescription = stateDescription
}

// Clear semantics and set new ones (for decorative images)
fun Modifier.decorativeElement(): Modifier = this.clearAndSetSemantics { }
