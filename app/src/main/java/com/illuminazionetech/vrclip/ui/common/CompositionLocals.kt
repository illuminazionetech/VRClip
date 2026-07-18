package com.illuminazionetech.vrclip.ui.common

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.monet.LocalTonalPalettes
import com.kyant.monet.PaletteStyle
import com.kyant.monet.TonalPalettes.Companion.toTonalPalettes
import com.illuminazionetech.vrclip.ui.theme.DEFAULT_SEED_COLOR
import com.illuminazionetech.vrclip.ui.theme.FixedColorRoles
import com.illuminazionetech.vrclip.util.DarkThemePreference
import com.illuminazionetech.vrclip.util.PreferenceUtil

/**
 * Spatial density (Quest vs. phone) touch-target/type-scale profile, the successor of the old
 * glass system's `GlassDensity`, now consumed directly by M3 components instead of a blur effect.
 */
enum class SpatialDensity(val minTouchTarget: Dp, val typeScale: Float, val shapeScale: Float) {
    Phone(minTouchTarget = 48.dp, typeScale = 1f, shapeScale = 1f),
    SpatialPanel(minTouchTarget = 64.dp, typeScale = 1.2f, shapeScale = 1.25f),
}

val LocalDarkTheme = compositionLocalOf { DarkThemePreference() }
val LocalIsVRMode = compositionLocalOf { false }
val LocalSeedColor = compositionLocalOf { DEFAULT_SEED_COLOR }
val LocalWindowWidthState = staticCompositionLocalOf { WindowWidthSizeClass.Compact }
val LocalDynamicColorSwitch = compositionLocalOf { true }
val LocalPaletteStyleIndex = compositionLocalOf { 0 }
val LocalFixedColorRoles = staticCompositionLocalOf {
    FixedColorRoles.fromColorSchemes(
        lightColors = lightColorScheme(),
        darkColors = darkColorScheme(),
    )
}

/** Resolves to [SpatialDensity.SpatialPanel] whenever the app is running in Quest VR mode. */
@Composable
fun spatialDensity(): SpatialDensity =
    if (LocalIsVRMode.current) SpatialDensity.SpatialPanel else SpatialDensity.Phone

@Composable
fun SettingsProvider(windowWidthSizeClass: WindowWidthSizeClass, content: @Composable () -> Unit) {
    PreferenceUtil.AppSettingsStateFlow.collectAsState().value.run {
        CompositionLocalProvider(
            LocalDarkTheme provides darkTheme,
            LocalSeedColor provides DEFAULT_SEED_COLOR,
            LocalPaletteStyleIndex provides 0,
            LocalWindowWidthState provides windowWidthSizeClass,
            LocalDynamicColorSwitch provides dynamicColor,
            LocalTonalPalettes provides Color(DEFAULT_SEED_COLOR).toTonalPalettes(PaletteStyle.Expressive),
            content = content,
        )
    }
}
