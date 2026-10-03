package com.illuminazionetech.vrclip.player.quest

import android.os.SystemClock
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.illuminazionetech.vrclip.ui.common.Haptic
import com.illuminazionetech.vrclip.ui.common.toHaptic
import com.meta.spatial.core.Hand
import com.meta.spatial.core.Query
import com.meta.spatial.core.SpatialInterface
import com.meta.spatial.toolkit.AvatarAttachment
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.ControllerType

/**
 * The app's haptic vocabulary on the Touch controllers, as `LocalHapticFeedback` of the immersive
 * player's panels: the controller that pointed at a button and pressed it feels the click, and a
 * light tick marks the ray entering a control, as in Horizon OS's own panels. With hand tracking
 * there is nothing to vibrate and the requests are dropped.
 *
 * The Spatial SDK drives the controllers from its frame loop, so requests from the UI are queued
 * and played by [tick], which the activity calls every frame.
 */
internal class ControllerHaptics : HapticFeedback {

    private class Pulse(val atMs: Long, val amplitude: Float, val durationMs: Long)

    private val pending = ArrayList<Pulse>()

    /** The controller that last pressed a button: the one pointing at the panel. */
    @Volatile private var hand = Hand.RIGHT

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        play(hapticFeedbackType.toHaptic() ?: return)
    }

    fun play(haptic: Haptic) {
        when (haptic) {
            Haptic.Tick -> queue(0.3f, 10)
            Haptic.Step -> queue(0.5f, 18)
            Haptic.Tap -> queue(0.6f, 25)
            Haptic.ToggleOn -> queue(0.75f, 30)
            Haptic.ToggleOff -> queue(0.5f, 25)
            Haptic.GestureStart -> queue(0.75f, 30)
            Haptic.GestureEnd -> queue(0.4f, 15)
            Haptic.LongPress -> queue(0.85f, 40)
            Haptic.Confirm -> {
                queue(0.45f, 20)
                queue(0.85f, 35, delayMs = PAIR_GAP_MS)
            }
            Haptic.Reject -> {
                queue(0.8f, 30)
                queue(0.8f, 30, delayMs = KNOCK_GAP_MS)
            }
        }
    }

    /** The pointer ray has entered a control. */
    fun hover() = queue(0.15f, 12)

    private fun queue(amplitude: Float, durationMs: Long, delayMs: Long = 0) {
        synchronized(pending) {
            pending += Pulse(SystemClock.uptimeMillis() + delayMs, amplitude, durationMs)
        }
    }

    /** Called on every scene tick, on the thread that drives the scene. */
    fun tick(spatial: SpatialInterface) {
        trackHand()
        val now = SystemClock.uptimeMillis()
        // Pulses due in the same frame would cut each other off: the strongest one plays.
        var strongest: Pulse? = null
        synchronized(pending) {
            val iterator = pending.iterator()
            while (iterator.hasNext()) {
                val pulse = iterator.next()
                if (pulse.atMs > now) continue
                if (pulse.amplitude > (strongest?.amplitude ?: 0f)) strongest = pulse
                iterator.remove()
            }
        }
        val pulse = strongest ?: return
        spatial.applyHapticFeedback(hand, pulse.amplitude, pulse.durationMs * NS_PER_MS, FREQUENCY)
    }

    private fun trackHand() {
        Query.where { has(Controller.id, AvatarAttachment.id) }
            .eval()
            .forEach { entity ->
                val controller = entity.getComponent<Controller>()
                if (!controller.isActive || controller.type != ControllerType.CONTROLLER) {
                    return@forEach
                }
                if (controller.buttonState and controller.changedButtons == 0) return@forEach
                when (entity.getComponent<AvatarAttachment>().type) {
                    "left_controller" -> hand = Hand.LEFT
                    "right_controller" -> hand = Hand.RIGHT
                }
            }
    }

    private companion object {
        const val NS_PER_MS = 1_000_000L

        /** The frequency Meta's own panel haptics use. */
        const val FREQUENCY = 200f
        const val PAIR_GAP_MS = 50L
        const val KNOCK_GAP_MS = 90L
    }
}

/** The immersive player's controller haptics, for the hover tick on its controls. */
internal val LocalControllerHaptics = staticCompositionLocalOf<ControllerHaptics?> { null }

/** A light controller tick when the pointer ray enters this control. */
internal fun Modifier.hoverHaptic(haptics: ControllerHaptics?): Modifier =
    if (haptics == null) this
    else
        pointerInput(haptics) {
            awaitPointerEventScope {
                while (true) {
                    if (awaitPointerEvent().type == PointerEventType.Enter) haptics.hover()
                }
            }
        }
