package com.xrclip.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.glassEffect(
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
    borderColor: Color = Color.White.copy(alpha = 0.3f)
): Modifier = this
    .background(color, shape)
    .border(1.dp, borderColor, shape)
    .clip(shape)
