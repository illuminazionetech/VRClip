package com.illuminazionetech.vrclip.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The player's haptic vocabulary, so the same kind of action always feels the same: a light key
 * tick for buttons, toggle clicks for on/off controls, segment ticks while scrubbing or stepping, a
 * threshold bump when a gesture takes hold and a soft end when it lets go. The system's touch
 * feedback setting still decides whether any of it is felt.
 */
@Stable
internal class PlayerHaptics(private val feedback: HapticFeedback) {
    fun tap() = feedback.performHapticFeedback(HapticFeedbackType.VirtualKey)

    fun toggle(on: Boolean) =
        feedback.performHapticFeedback(
            if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
        )

    /** A discrete step: a double-tap seek, a speed preset, a level hitting its end. */
    fun step() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** Frequent light ticks while dragging through a range. */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)

    /** A drag or long press has crossed its threshold and is now in control. */
    fun gestureStart() = feedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

    fun gestureEnd() = feedback.performHapticFeedback(HapticFeedbackType.GestureEnd)

    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)
}

@Composable
internal fun rememberPlayerHaptics(): PlayerHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { PlayerHaptics(feedback) }
}
