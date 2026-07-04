package com.alcedo.studio.ui.common

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.os.VibrationEffect
import android.os.Vibrator

object HapticFeedback {
    private var vibrator: Vibrator? = null

    fun initialize(context: Context) {
        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    // Light tick - for slider value changes
    fun tick(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    // Medium click - for button presses
    fun click(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    // Heavy click - for important actions
    fun heavyClick(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    // Success - for completed actions
    fun success(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.EFFECT_TICK))
        }
    }

    // Error - for failed actions
    fun error(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.EFFECT_DOUBLE_CLICK))
        }
    }

    // Slider stop - when slider reaches end of range
    fun sliderStop(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}
