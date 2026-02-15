package com.xrclip.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.glassEffect(
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
    borderColor: Color = Color.White.copy(alpha = 0.25f),
    blur: Boolean = false
): Modifier = this
    .background(
        Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = color.alpha * 0.85f),
                color.copy(alpha = color.alpha * 1.05f)
            )
        ),
        shape
    )
    .border(
        0.5.dp,
        Brush.verticalGradient(
            colors = listOf(
                borderColor.copy(alpha = borderColor.alpha * 1.2f),
                borderColor.copy(alpha = borderColor.alpha * 0.6f)
            )
        ),
        shape
    )
    .clip(shape)
