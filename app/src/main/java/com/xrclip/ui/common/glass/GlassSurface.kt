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
 * Material You surface container — replaces the previous liquid-glass surface.
 * Sized to at least [GlassDensity.minTouchTarget] for consistent touch targets
 * across phone and Meta Quest spatial panels.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    borderColor: Color = Color.Transparent,
    blur: Boolean = false,
    elevation: GlassElevation = GlassElevation.Level0,
    content: @Composable () -> Unit,
) {
    val density = GlassTokens.density()
    Surface(
        modifier = modifier.defaultMinSize(minHeight = density.minTouchTarget),
        shape = shape,
        color = color,
    ) {
        content()
    }
}
