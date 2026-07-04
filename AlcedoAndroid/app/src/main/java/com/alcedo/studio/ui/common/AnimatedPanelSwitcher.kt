package com.alcedo.studio.ui.common

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedPanelSwitcher(
    currentPanel: Any,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<Any>.() -> AnimatedContentTransitionScope.SlideDirection = {
        if ((targetState as Enum<*>).ordinal > (initialState as Enum<*>).ordinal) AnimatedContentTransitionScope.SlideDirection.Left
        else AnimatedContentTransitionScope.SlideDirection.Right
    },
    content: @Composable (Any) -> Unit
) {
    AnimatedContent(
        targetState = currentPanel,
        transitionSpec = {
            val direction = transitionSpec()
            slideIntoContainer(direction, animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f)) +
                    fadeIn(animationSpec = tween(180, delayMillis = 40, easing = AlcedoEasing.EmphasizedDecelerate)) with
            slideOutOfContainer(direction, animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f)) +
                    fadeOut(animationSpec = tween(120, easing = AlcedoEasing.Accelerate))
        },
        modifier = modifier,
        label = "panelSwitcher"
    ) { panel ->
        content(panel)
    }
}
