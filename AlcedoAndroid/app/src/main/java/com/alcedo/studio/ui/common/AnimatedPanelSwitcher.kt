package com.alcedo.studio.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Animated horizontal panel switcher for the editor's bottom panel tabs.
 * Slides the incoming panel in from the direction of the tab the user selected
 * relative to the current one, with a short fade and proper easing for a
 * premium, fluid feel.
 *
 * @param key       Identifier of the currently active panel.
 * @param direction +1 to slide left (forward), -1 to slide right (back).
 * @param content   Lambda rendering the panel for the given key.
 */
@Composable
fun <T> AnimatedPanelSwitcher(
    key: T,
    modifier: Modifier = Modifier,
    direction: Int = 1,
    content: @Composable (T) -> Unit,
) {
    val enter = if (direction >= 0) {
        slideInHorizontally(tween(DesignTokens.motionPanel, easing = FastOutSlowInEasing)) { w -> w } +
            fadeIn(tween(DesignTokens.motionPanel, easing = FastOutSlowInEasing))
    } else {
        slideInHorizontally(tween(DesignTokens.motionPanel, easing = FastOutSlowInEasing)) { w -> -w } +
            fadeIn(tween(DesignTokens.motionPanel, easing = FastOutSlowInEasing))
    }
    val exit = if (direction >= 0) {
        slideOutHorizontally(tween(DesignTokens.motionPanel, easing = FastOutSlowInEasing)) { w -> -w } +
            fadeOut(tween(DesignTokens.motionPanel, easing = FastOutSlowInEasing))
    } else {
        slideOutHorizontally(tween(DesignTokens.motionPanel, easing = FastOutSlowInEasing)) { w -> w } +
            fadeOut(tween(DesignTokens.motionPanel, easing = FastOutSlowInEasing))
    }
    AnimatedContent(
        targetState = key,
        transitionSpec = { enter togetherWith exit },
        contentKey = { it },
        modifier = modifier,
        label = "panelSwitch",
    ) { current ->
        content(current)
    }
}
