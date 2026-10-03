package com.illuminazionetech.vrclip.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi

/**
 * VRClip's haptic vocabulary. Each kind of action always feels the same, on every screen: a crisp
 * tick while dragging through steps, a click for buttons, a firmer click when something turns on or
 * a gesture takes hold, a double knock when something fails.
 */
enum class Haptic {
    /** Frequent light ticks while dragging through a range. */
    Tick,
    /** A discrete step: a double-tap seek, a preset, a choice in a list. */
    Step,
    Tap,
    ToggleOn,
    ToggleOff,
    /** A drag or long press has crossed its threshold and is now in control. */
    GestureStart,
    GestureEnd,
    LongPress,
    Confirm,
    Reject,
}

object HapticFeedback {
    fun View.slightHapticFeedback() = haptic(Haptic.Tap)

    fun View.longPressHapticFeedback() = haptic(Haptic.LongPress)

    fun View.confirmHapticFeedback() = haptic(Haptic.Confirm)

    fun View.rejectHapticFeedback() = haptic(Haptic.Reject)

    fun View.toggleOnHapticFeedback() = haptic(Haptic.ToggleOn)

    fun View.toggleOffHapticFeedback() = haptic(Haptic.ToggleOff)

    /**
     * Plays [haptic] on the vibration motor, or through the view's own touch feedback on devices
     * without one (Meta Quest panels, most tablets).
     */
    fun View.haptic(haptic: Haptic) {
        if (!HapticEngine.get(context).play(haptic)) {
            performHapticFeedback(haptic.systemConstant())
        }
    }

    private fun Haptic.systemConstant(): Int =
        when (this) {
            Haptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
            Haptic.Step,
            Haptic.Tap,
            Haptic.ToggleOff,
            Haptic.GestureEnd -> HapticFeedbackConstants.VIRTUAL_KEY
            Haptic.ToggleOn,
            Haptic.GestureStart,
            Haptic.LongPress -> HapticFeedbackConstants.LONG_PRESS
            Haptic.Confirm ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.LONG_PRESS
            Haptic.Reject ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
                else HapticFeedbackConstants.LONG_PRESS
        }
}

/**
 * Plays [Haptic]s with explicit strengths. The system's touch feedback is tuned to be barely there
 * and several of its constants (clock tick, gesture end, segment ticks) can hardly be felt on
 * common phones, so the effects here are built from the same vibration primitives at firmer, fixed
 * scales, with predefined effects and then plain pulses as fallbacks on older or simpler motors.
 *
 * Everything plays as touch feedback, so turning touch vibration off in the system settings
 * silences it, as it does for the system's own feedback.
 */
internal class HapticEngine private constructor(context: Context) {

    private val resolver = context.contentResolver
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
    private val available = vibrator?.hasVibrator() == true
    private val effects = arrayOfNulls<VibrationEffect>(Haptic.entries.size)

    /** False when the device has no vibration motor, so the caller can fall back. */
    fun play(haptic: Haptic): Boolean {
        val vibrator = vibrator?.takeIf { available } ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system applies the touch feedback setting to this usage.
            vibrator.vibrate(
                effect(vibrator, haptic),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH),
            )
        } else if (touchFeedbackEnabled()) {
            @Suppress("DEPRECATION") vibrator.vibrate(effect(vibrator, haptic), SONIFICATION)
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun touchFeedbackEnabled(): Boolean =
        Settings.System.getInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    private fun effect(vibrator: Vibrator, haptic: Haptic): VibrationEffect =
        effects[haptic.ordinal] ?: build(vibrator, haptic).also { effects[haptic.ordinal] = it }

    private fun build(vibrator: Vibrator, haptic: Haptic): VibrationEffect {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vibrator.areAllPrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    VibrationEffect.Composition.PRIMITIVE_TICK,
                )
        ) {
            return composed(haptic)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return predefined(haptic)
        return pulse(haptic, vibrator.hasAmplitudeControl())
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun composed(haptic: Haptic): VibrationEffect {
        val click = VibrationEffect.Composition.PRIMITIVE_CLICK
        val tick = VibrationEffect.Composition.PRIMITIVE_TICK
        val composition = VibrationEffect.startComposition()
        when (haptic) {
            Haptic.Tick -> composition.addPrimitive(tick, 0.75f)
            Haptic.Step -> composition.addPrimitive(tick, 1f)
            Haptic.Tap -> composition.addPrimitive(click, 0.8f)
            Haptic.ToggleOn -> composition.addPrimitive(click, 1f)
            Haptic.ToggleOff -> composition.addPrimitive(click, 0.6f)
            Haptic.GestureStart -> composition.addPrimitive(click, 1f)
            Haptic.GestureEnd -> composition.addPrimitive(tick, 0.9f)
            Haptic.LongPress ->
                composition.addPrimitive(click, 1f).addPrimitive(tick, 0.7f, PAIR_GAP_MS)
            Haptic.Confirm ->
                composition.addPrimitive(tick, 0.8f).addPrimitive(click, 1f, PAIR_GAP_MS)
            Haptic.Reject ->
                composition.addPrimitive(click, 1f).addPrimitive(click, 1f, KNOCK_GAP_MS)
        }
        return composition.compose()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun predefined(haptic: Haptic): VibrationEffect =
        VibrationEffect.createPredefined(
            when (haptic) {
                Haptic.Tick -> VibrationEffect.EFFECT_TICK
                Haptic.Step,
                Haptic.Tap,
                Haptic.ToggleOff,
                Haptic.GestureEnd -> VibrationEffect.EFFECT_CLICK
                Haptic.ToggleOn,
                Haptic.GestureStart,
                Haptic.LongPress,
                Haptic.Confirm -> VibrationEffect.EFFECT_HEAVY_CLICK
                Haptic.Reject -> VibrationEffect.EFFECT_DOUBLE_CLICK
            }
        )

    private fun pulse(haptic: Haptic, amplitudeControl: Boolean): VibrationEffect {
        fun amplitude(value: Int) =
            if (amplitudeControl) value else VibrationEffect.DEFAULT_AMPLITUDE
        return when (haptic) {
            Haptic.Tick -> VibrationEffect.createOneShot(12, amplitude(140))
            Haptic.Step,
            Haptic.Tap,
            Haptic.ToggleOff,
            Haptic.GestureEnd -> VibrationEffect.createOneShot(18, amplitude(200))
            Haptic.ToggleOn,
            Haptic.GestureStart,
            Haptic.LongPress,
            Haptic.Confirm -> VibrationEffect.createOneShot(30, amplitude(255))
            Haptic.Reject ->
                VibrationEffect.createWaveform(
                    longArrayOf(0, 25, KNOCK_GAP_MS.toLong(), 25),
                    intArrayOf(0, amplitude(255), 0, amplitude(255)),
                    -1,
                )
        }
    }

    companion object {
        private const val PAIR_GAP_MS = 50
        private const val KNOCK_GAP_MS = 90

        private val SONIFICATION =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

        @Volatile private var instance: HapticEngine? = null

        fun get(context: Context): HapticEngine =
            instance
                ?: synchronized(this) {
                    instance ?: HapticEngine(context.applicationContext).also { instance = it }
                }
    }
}
