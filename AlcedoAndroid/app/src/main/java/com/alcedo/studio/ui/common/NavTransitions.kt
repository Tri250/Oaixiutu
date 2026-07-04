package com.alcedo.studio.ui.common

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

object NavTransitions {
    private const val DURATION_MS = 350

    // Standard horizontal slide (for forward navigation)
    fun slideInRight(): ContentTransform = ContentTransform(
        initialContentExit = slideOutHorizontally(
            targetOffsetX = { -it / 3 },
            animationSpec = tween(DURATION_MS)
        ) + fadeOut(
            animationSpec = tween(DURATION_MS / 2, delayMillis = DURATION_MS / 4)
        ),
        targetContentEnter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(DURATION_MS)
        ) + fadeIn(
            animationSpec = tween(DURATION_MS / 2, delayMillis = DURATION_MS / 4)
        )
    )

    // Standard horizontal slide (for back navigation)
    fun slideInLeft(): ContentTransform = ContentTransform(
        initialContentExit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(DURATION_MS)
        ) + fadeOut(
            animationSpec = tween(DURATION_MS / 2, delayMillis = DURATION_MS / 4)
        ),
        targetContentEnter = slideInHorizontally(
            initialOffsetX = { -it / 3 },
            animationSpec = tween(DURATION_MS)
        ) + fadeIn(
            animationSpec = tween(DURATION_MS / 2, delayMillis = DURATION_MS / 4)
        )
    )

    // Fade transition (for tab switching)
    fun fadeTransition(): ContentTransform = ContentTransform(
        initialContentExit = fadeOut(animationSpec = tween(DURATION_MS / 2)),
        targetContentEnter = fadeIn(animationSpec = tween(DURATION_MS / 2))
    )

    // Scale + fade (for dialog/modal transitions)
    fun scaleTransition(): ContentTransform = ContentTransform(
        initialContentExit = fadeOut(animationSpec = tween(DURATION_MS / 2)) +
                scaleOut(targetScale = 0.9f, animationSpec = tween(DURATION_MS / 2)),
        targetContentEnter = fadeIn(animationSpec = tween(DURATION_MS / 2)) +
                scaleIn(initialScale = 0.9f, animationSpec = tween(DURATION_MS / 2))
    )

    // Slide up (for editor panels)
    fun slideUpTransition(): ContentTransform = ContentTransform(
        initialContentExit = fadeOut(animationSpec = tween(DURATION_MS / 3)),
        targetContentEnter = slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = tween(DURATION_MS)
        ) + fadeIn(animationSpec = tween(DURATION_MS / 2))
    )
}
