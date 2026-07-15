package com.xrclip.ui.common.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import com.xrclip.ui.common.glassEffect

/** Clickable [GlassSurface] variant for list rows / menu items (e.g. the player projection menu). */
@Composable
fun GlassCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
    borderColor: Color = Color.White.copy(alpha = 0.25f),
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
                    blur = true,
                    elevation = elevation,
                )
                .clickable(onClick = onClick, role = Role.Button)
    ) {
        content()
    }
}
