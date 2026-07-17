package com.xrclip.ui.common.glass

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * Material You card — replaces the previous liquid-glass clickable container.
 * Uses M3 Card with tonal elevation; min-touch-target sizing follows
 * [GlassDensity] for both phone and Meta Quest spatial panel contexts.
 */
@Composable
fun GlassCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    borderColor: Color = Color.Transparent,
    elevation: GlassElevation = GlassElevation.Level0,
    content: @Composable () -> Unit,
) {
    val density = GlassTokens.density()
    Card(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = density.minTouchTarget),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        content()
    }
}
