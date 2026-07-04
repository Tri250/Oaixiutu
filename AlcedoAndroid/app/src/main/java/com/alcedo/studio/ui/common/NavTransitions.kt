package com.alcedo.studio.ui.common

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

// ═══════════════════════════════════════════════════════════════════
// 2026 Flagship Animation & Motion System
// ═══════════════════════════════════════════════════════════════════
// Easing curves inspired by iOS 26 / Material 3 Expressive:
//   Emphasized   – long deceleration, slight overshoot feel
//   Standard     – balanced ease-in-out for most transitions
//   Decelerate   – quick start, gentle settle
//   Accelerate   – gentle start, quick exit
// ═══════════════════════════════════════════════════════════════════

object AlcedoEasing {
    /** Emphasized – iOS-spring-like long tail (0.2, 0.0, 0.0, 1.0) */
    val Emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    /** Emphasized accelerate – for exiting elements */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    /** Emphasized decelerate – for entering elements */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    /** Standard – balanced curve */
    val Standard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    /** Decelerate – quick entry */
    val Decelerate = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
    /** Accelerate – quick exit */
    val Accelerate = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)
}

object AlcedoDuration {
    const val INSTANT = 50       // hover/press feedback
    const val QUICK = 120        // micro-interactions
    const val SHORT = 200        // small transitions
    const val MEDIUM = 350       // standard page transitions
    const val LONG = 500         // hero transitions
    const val EXTRA_LONG = 700   // elaborate onboarding
}

object NavTransitions {
    // ── Forward navigation (push) ────────────────────────────────────
    fun slideInRight(): ContentTransform = ContentTransform(
        initialContentExit = slideOutHorizontally(
            targetOffsetX = { -it / 3 },
            animationSpec = tween(AlcedoDuration.MEDIUM, easing = AlcedoEasing.EmphasizedAccelerate)
        ) + fadeOut(
            animationSpec = tween(AlcedoDuration.SHORT, delayMillis = 50)
        ),
        targetContentEnter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(AlcedoDuration.MEDIUM, easing = AlcedoEasing.EmphasizedDecelerate)
        ) + fadeIn(
            animationSpec = tween(AlcedoDuration.SHORT, delayMillis = 80)
        )
    )

    // ── Backward navigation (pop) ────────────────────────────────────
    fun slideInLeft(): ContentTransform = ContentTransform(
        initialContentExit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(AlcedoDuration.MEDIUM, easing = AlcedoEasing.Standard)
        ) + fadeOut(
            animationSpec = tween(AlcedoDuration.SHORT, delayMillis = 50)
        ),
        targetContentEnter = slideInHorizontally(
            initialOffsetX = { -it / 3 },
            animationSpec = tween(AlcedoDuration.MEDIUM, easing = AlcedoEasing.EmphasizedDecelerate)
        ) + fadeIn(
            animationSpec = tween(AlcedoDuration.SHORT, delayMillis = 80)
        )
    )

    // ── Fade (tab switching) ─────────────────────────────────────────
    fun fadeTransition(): ContentTransform = ContentTransform(
        initialContentExit = fadeOut(
            animationSpec = tween(AlcedoDuration.SHORT, easing = AlcedoEasing.Standard)
        ),
        targetContentEnter = fadeIn(
            animationSpec = tween(AlcedoDuration.SHORT, easing = AlcedoEasing.EmphasizedDecelerate)
        )
    )

    // ── Scale + fade (dialog / modal) ────────────────────────────────
    fun scaleTransition(): ContentTransform = ContentTransform(
        initialContentExit = fadeOut(
            animationSpec = tween(AlcedoDuration.SHORT, easing = AlcedoEasing.Accelerate)
        ) + scaleOut(
            targetScale = 0.92f,
            animationSpec = tween(AlcedoDuration.MEDIUM, easing = AlcedoEasing.Accelerate)
        ),
        targetContentEnter = fadeIn(
            animationSpec = tween(AlcedoDuration.SHORT, delayMillis = 60, easing = AlcedoEasing.EmphasizedDecelerate)
        ) + scaleIn(
            initialScale = 0.92f,
            animationSpec = tween(AlcedoDuration.MEDIUM, easing = AlcedoEasing.EmphasizedDecelerate)
        )
    )

    // ── Slide up (editor panels) ─────────────────────────────────────
    fun slideUpTransition(): ContentTransform = ContentTransform(
        initialContentExit = fadeOut(
            animationSpec = tween(AlcedoDuration.SHORT, easing = AlcedoEasing.Accelerate)
        ),
        targetContentEnter = slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = tween(AlcedoDuration.MEDIUM, easing = AlcedoEasing.EmphasizedDecelerate)
        ) + fadeIn(
            animationSpec = tween(AlcedoDuration.SHORT, delayMillis = 60)
        )
    )

    // ── Spring-based panel switch (elastic, premium feel) ────────────
    fun springSlideTransition(): ContentTransform = ContentTransform(
        initialContentExit = slideOutHorizontally(
            targetOffsetX = { -it / 4 },
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 350f)
        ) + fadeOut(
            animationSpec = tween(AlcedoDuration.QUICK)
        ),
        targetContentEnter = slideInHorizontally(
            initialOffsetX = { it / 4 },
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 350f)
        ) + fadeIn(
            animationSpec = tween(AlcedoDuration.SHORT, delayMillis = 40)
        )
    )
}
