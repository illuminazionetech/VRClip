package com.xrclip.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.xrclip.ui.common.glass.GlassElevation

/**
 * Material You surface modifier replacing the previous Liquid-Glass effect.
 * Uses [MaterialTheme] container colors for cohesive tonal layering. The [blur],
 * [elevation] and [borderColor] parameters are retained for API compatibility but
 * have no visual effect — all rendering now follows the M3 surface system.
 */
@Composable
fun Modifier.glassEffect(
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = Color.Transparent,
    blur: Boolean = false,
    elevation: GlassElevation = GlassElevation.Level1,
): Modifier {
    val effectiveColor = if (color == Color.Transparent || color.alpha == 0f)
        MaterialTheme.colorScheme.surfaceContainerLow
    else color
    return this
        .clip(shape)
        .background(effectiveColor, shape)
}
