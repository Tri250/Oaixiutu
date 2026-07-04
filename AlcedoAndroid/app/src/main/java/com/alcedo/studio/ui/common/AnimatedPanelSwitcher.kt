package com.alcedo.studio.ui.common

import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AnimatedPanelSwitcher(
    currentPanel: Any,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<Any>.() -> AnimatedContentTransitionScope<Any>.SlideDirection = {
        if (targetState > initialState) AnimatedContentTransitionScope.SlideDirection.Left
        else AnimatedContentTransitionScope.SlideDirection.Right
    },
    content: @Composable (Any) -> Unit
) {
    AnimatedContent(
        targetState = currentPanel,
        transitionSpec = {
            val direction = transitionSpec()
            slideIntoContainer(direction, tween(300)) + fadeIn(tween(200, delayMillis = 50)) with
            slideOutOfContainer(direction, tween(300)) + fadeOut(tween(150))
        },
        modifier = modifier,
        label = "panelSwitcher"
    ) { panel ->
        content(panel)
    }
}
