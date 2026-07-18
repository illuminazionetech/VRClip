package com.illuminazionetech.vrclip.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.illuminazionetech.vrclip.ui.common.motion.ExpressiveMotion

/**
 * Flat Material 3 tonal-surface modifier, replaces the old "Liquid Glass" `glassEffect()`
 * (GPU blur + translucent gradient border). No blur, no border: a clipped shape filled with a
 * real M3 surface-container tone, animating in with an expressive spring rather than popping in
 * instantly when the color changes (e.g. scroll-driven app bar tone shifts).
 */
@Composable
fun Modifier.tonalSurface(
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
): Modifier {
    val animatedColor by
        animateColorAsState(
            targetValue = color,
            animationSpec = ExpressiveMotion.effects(),
            label = "tonalSurfaceColor",
        )
    return this.clip(shape).background(animatedColor, shape)
}
