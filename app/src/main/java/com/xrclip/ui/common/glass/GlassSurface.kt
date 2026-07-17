package com.xrclip.ui.common.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * M3 Surface-based replacement for the old Liquid-Glass container. Retains the
 * [GlassDensity] min-touch-target sizing so VR spatial panels still get 64 dp
 * minimum height. The [blur], [elevation] and [borderColor] params are kept for
 * call-site compatibility — visual output now follows the M3 tonal surface system.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = Color.Transparent,
    blur: Boolean = true,
    elevation: GlassElevation = GlassElevation.Level1,
    content: @Composable () -> Unit,
) {
    val density = GlassTokens.density()
    Surface(
        modifier = modifier.defaultMinSize(minHeight = density.minTouchTarget),
        shape = shape,
        color = if (color.alpha == 0f) MaterialTheme.colorScheme.surfaceContainerLow else color,
        tonalElevation = elevation.blurRadius * 0.1f,
    ) {
        content()
    }
}
