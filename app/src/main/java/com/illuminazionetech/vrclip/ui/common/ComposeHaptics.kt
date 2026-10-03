package com.illuminazionetech.vrclip.ui.common

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.hapticfeedback.HapticFeedback as ComposeHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.illuminazionetech.vrclip.ui.common.HapticFeedback.haptic

/**
 * Compose's haptic types in VRClip's [Haptic] vocabulary. Null for text handle movement, which
 * fires once per character and stays with the system's faint feedback.
 */
fun HapticFeedbackType.toHaptic(): Haptic? =
    when (this) {
        HapticFeedbackType.SegmentFrequentTick -> Haptic.Tick
        HapticFeedbackType.SegmentTick -> Haptic.Step
        HapticFeedbackType.ToggleOn -> Haptic.ToggleOn
        HapticFeedbackType.ToggleOff -> Haptic.ToggleOff
        HapticFeedbackType.GestureThresholdActivate -> Haptic.GestureStart
        HapticFeedbackType.GestureEnd -> Haptic.GestureEnd
        HapticFeedbackType.LongPress -> Haptic.LongPress
        HapticFeedbackType.Confirm -> Haptic.Confirm
        HapticFeedbackType.Reject -> Haptic.Reject
        HapticFeedbackType.TextHandleMove -> null
        else -> Haptic.Tap
    }

/** The app's `LocalHapticFeedback`: every Compose haptic goes through [HapticEngine]. */
internal class AppHapticFeedback(private val view: View) : ComposeHapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        val haptic = hapticFeedbackType.toHaptic()
        if (haptic != null) view.haptic(haptic)
        else view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
    }
}
