package com.xrclip.ui.common

import android.view.HapticFeedbackConstants
import android.view.View

import android.os.Build

object HapticFeedback {
    fun View.slightHapticFeedback() = this.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    fun View.longPressHapticFeedback() =
        this.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

    fun View.confirmHapticFeedback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            this.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun View.rejectHapticFeedback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            this.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun View.toggleOnHapticFeedback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            this.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    fun View.toggleOffHapticFeedback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        } else {
            this.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}
