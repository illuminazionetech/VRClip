package com.xrclip.ui.common.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xrclip.ui.common.LocalIsVRMode

/** Blur radius tiers for [com.xrclip.ui.common.glassEffect]; `Level0` disables blur entirely. */
enum class GlassElevation(val blurRadius: Dp) {
    Level0(0.dp),
    Level1(16.dp),
    Level2(24.dp),
    Level3(32.dp),
}

/**
 * Density/interaction profile for glass surfaces. The same token set and composables are used
 * on phone and on Meta Quest; only sizing, stroke weight and type scale shift for the "10-foot",
 * controller-ray-focused spatial panel context.
 */
enum class GlassDensity(
    val minTouchTarget: Dp,
    val strokeWidth: Dp,
    val borderAlphaMultiplier: Float,
    val typeScale: Float,
) {
    Phone(minTouchTarget = 48.dp, strokeWidth = 0.5.dp, borderAlphaMultiplier = 1f, typeScale = 1f),
    SpatialPanel(
        minTouchTarget = 64.dp,
        strokeWidth = 1.dp,
        borderAlphaMultiplier = 1.5f,
        typeScale = 1.15f,
    ),
}

object GlassTokens {
    /** Resolves to [GlassDensity.SpatialPanel] whenever the app is running in Quest VR mode. */
    @Composable
    @ReadOnlyComposable
    fun density(): GlassDensity =
        if (LocalIsVRMode.current) GlassDensity.SpatialPanel else GlassDensity.Phone
}
