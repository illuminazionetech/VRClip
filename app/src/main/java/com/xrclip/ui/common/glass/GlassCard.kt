package com.xrclip.ui.common.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/** M3 Card-based replacement for the old Liquid-Glass card surface. */
@Composable
fun GlassCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = Color.Transparent,
    elevation: GlassElevation = GlassElevation.Level1,
    content: @Composable () -> Unit,
) {
    val density = GlassTokens.density()
    Card(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = density.minTouchTarget),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.blurRadius * 0.1f),
    ) {
        content()
    }
}
