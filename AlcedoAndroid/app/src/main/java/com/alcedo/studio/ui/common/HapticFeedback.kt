package com.alcedo.studio.ui.common

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Haptic feedback helper. Wraps [View.performHapticFeedback] so composables can
 * emit tactile cues on slider commits, selection changes and destructive
 * confirmations without holding a View reference directly.
 *
 * Usage:
 * ```
 * val haptics = rememberHapticFeedback()
 * haptics.click()
 * ```
 */
class HapticFeedback(private val view: View) {
    fun click() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun toggle() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun longPress() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun commit() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun reject() {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }
}

/** Provides a [HapticFeedback] bound to the current composition's host [View]. */
@Composable
fun rememberHapticFeedback(): HapticFeedback {
    val view = LocalView.current
    return remember(view) { HapticFeedback(view) }
}
