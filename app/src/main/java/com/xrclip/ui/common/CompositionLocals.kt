package com.xrclip.ui.common

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.xrclip.ui.theme.DEFAULT_SEED_COLOR
import com.xrclip.ui.theme.FixedColorRoles
import com.xrclip.util.DarkThemePreference
import com.xrclip.util.PreferenceUtil

val LocalDarkTheme = compositionLocalOf { DarkThemePreference() }
val LocalSeedColor = compositionLocalOf { DEFAULT_SEED_COLOR }
val LocalWindowWidthState = staticCompositionLocalOf { WindowWidthSizeClass.Compact }
val LocalDynamicColorSwitch = compositionLocalOf { false }
val LocalPaletteStyleIndex = compositionLocalOf { 0 }
val LocalFixedColorRoles = staticCompositionLocalOf {
    FixedColorRoles.fromColorSchemes(
        lightColors = lightColorScheme(),
        darkColors = darkColorScheme(),
    )
}

@Composable
fun SettingsProvider(windowWidthSizeClass: WindowWidthSizeClass, content: @Composable () -> Unit) {
    PreferenceUtil.AppSettingsStateFlow.collectAsState().value.run {
        CompositionLocalProvider(
            LocalDarkTheme provides darkTheme,
            LocalSeedColor provides DEFAULT_SEED_COLOR,
            LocalPaletteStyleIndex provides 0,
            LocalWindowWidthState provides windowWidthSizeClass,
            LocalDynamicColorSwitch provides false,
            content = content,
        )
    }
}
