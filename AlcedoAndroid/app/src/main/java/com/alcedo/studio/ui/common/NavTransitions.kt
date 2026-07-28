package com.alcedo.studio.ui.common

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Navigation transition helpers. Provides consistent slide+fade transitions
 * between top-level destinations and a no-op transition for tab swaps that
 * should feel instantaneous.
 */
object NavTransitions {

    /** Forward slide-in for pushing a destination. */
    fun slideIntoContainer(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition = {
        slideInHorizontally(tween(DesignTokens.motionNormal)) { w -> w } + fadeIn(tween(DesignTokens.motionNormal))
    }

    /** Forward slide-out for the outgoing destination. */
    fun slideOutOfContainer(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition = {
        slideOutHorizontally(tween(DesignTokens.motionNormal)) { w -> -w / 4 } + fadeOut(tween(DesignTokens.motionNormal))
    }

    /** Pop (back) slide-in. */
    fun popSlideInto(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition = {
        slideInHorizontally(tween(DesignTokens.motionNormal)) { w -> -w / 4 } + fadeIn(tween(DesignTokens.motionNormal))
    }

    /** Pop (back) slide-out. */
    fun popSlideOutOf(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition = {
        slideOutHorizontally(tween(DesignTokens.motionNormal)) { w -> w } + fadeOut(tween(DesignTokens.motionNormal))
    }
}

/**
 * Convenience [NavGraphBuilder.composable] wrapper that applies the standard
 * push/pop slide transitions to a destination.
 */
fun NavGraphBuilder.composableWithTransitions(
    route: String,
    content: @Composable androidx.compose.animation.AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        enterTransition = NavTransitions.slideIntoContainer(),
        exitTransition = NavTransitions.slideOutOfContainer(),
        popEnterTransition = NavTransitions.popSlideInto(),
        popExitTransition = NavTransitions.popSlideOutOf(),
        content = content,
    )
}
