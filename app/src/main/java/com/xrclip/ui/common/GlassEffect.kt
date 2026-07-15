package com.xrclip.ui.common

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalDensity
import com.xrclip.ui.common.glass.GlassElevation
import com.xrclip.ui.common.glass.GlassTokens

/**
 * Apple-Liquid-Glass-inspired translucent surface. When [blur] is `true` and the device supports
 * it (API 31+), a real GPU blur ([RenderEffect]) is applied to the surface's own fill via
 * [elevation]'s radius; below API 31 (minSdk is 28) it falls back to the flat translucent
 * gradient this modifier always used to draw, so nothing regresses on older devices. Border
 * weight and intensity automatically adapt for Meta Quest's spatial-panel density via
 * [GlassTokens.density] so every existing call site benefits without changes.
 */
@Composable
fun Modifier.glassEffect(
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
    borderColor: Color = Color.White.copy(alpha = 0.25f),
    blur: Boolean = false,
    elevation: GlassElevation = GlassElevation.Level1,
): Modifier {
    val density = GlassTokens.density()
    val supportsRealBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val blurRadiusPx = with(LocalDensity.current) { elevation.blurRadius.toPx() }

    return this.graphicsLayer {
            this.shape = shape
            this.clip = true
            if (blur && supportsRealBlur && blurRadiusPx > 0f) {
                this.renderEffect =
                    RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
                        .asComposeRenderEffect()
            }
        }
        .background(
            Brush.verticalGradient(
                colors =
                    listOf(
                        color.copy(alpha = color.alpha * 0.85f),
                        color.copy(alpha = color.alpha * 1.05f),
                    )
            ),
            shape,
        )
        .border(
            density.strokeWidth,
            Brush.verticalGradient(
                colors =
                    listOf(
                        borderColor.copy(
                            alpha =
                                (borderColor.alpha * 1.2f * density.borderAlphaMultiplier)
                                    .coerceAtMost(1f)
                        ),
                        borderColor.copy(
                            alpha =
                                (borderColor.alpha * 0.6f * density.borderAlphaMultiplier)
                                    .coerceAtMost(1f)
                        ),
                    )
            ),
            shape,
        )
}
