package com.illuminazionetech.vrclip.ui.common.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec

/**
 * Spring tokens for component-level motion (selection states, press feedback, expanding rows).
 * Page transitions live in `ui/common/AnimatedComposable.kt`; components that take a
 * `MaterialTheme.motionScheme` spec should prefer that.
 */
object ExpressiveMotion {
    /** Bouncy spring for playful, attention-grabbing motion (FAB press, selection state changes). */
    fun <T> spatial(): SpringSpec<T> =
        SpringSpec(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

    /** Snappier, low-bounce spring for structural motion (sheets, card expansion). */
    fun <T> standard(): SpringSpec<T> =
        SpringSpec(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Fast, no-bounce spring for opacity/color fades where overshoot would look wrong. */
    fun <T> effects(): SpringSpec<T> =
        SpringSpec(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
}
