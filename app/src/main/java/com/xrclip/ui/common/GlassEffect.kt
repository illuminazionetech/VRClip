package com.xrclip.ui.common

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import com.xrclip.ui.common.glass.GlassElevation

/**
 * Material You surface modifier — replaces the previous liquid-glass effect.
 * All existing call sites compile unchanged; visually the frosted-glass blur and
 * translucent border are gone, replaced by the correct M3 tonal surface color.
 */
@Composable
fun Modifier.glassEffect(
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    borderColor: Color = Color.Transparent,
    blur: Boolean = false,
    elevation: GlassElevation = GlassElevation.Level0,
): Modifier {
    return this
        .graphicsLayer {
            this.shape = shape
            this.clip = true
        }
        .background(color, shape)
}
