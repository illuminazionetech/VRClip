package com.xrclip.ui.common.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.xrclip.ui.common.glassEffect

/**
 * Reusable Liquid-Glass container, sized to at least [GlassDensity.minTouchTarget] on the current
 * density (phone vs. Meta Quest spatial panel). Prefer this over calling `Modifier.glassEffect`
 * directly for new surfaces (e.g. player controls) so the min-touch-target rule stays consistent.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
    borderColor: Color = Color.White.copy(alpha = 0.25f),
    blur: Boolean = true,
    elevation: GlassElevation = GlassElevation.Level1,
    content: @Composable () -> Unit,
) {
    val density = GlassTokens.density()
    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = density.minTouchTarget)
                .glassEffect(
                    shape = shape,
                    color = color,
                    borderColor = borderColor,
                    blur = blur,
                    elevation = elevation,
                )
    ) {
        content()
    }
}
