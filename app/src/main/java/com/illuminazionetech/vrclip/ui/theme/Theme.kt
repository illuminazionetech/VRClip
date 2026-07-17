package com.illuminazionetech.vrclip.ui.theme

import android.os.Build
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDirection
import com.google.android.material.color.MaterialColors
import com.kyant.monet.dynamicColorScheme
import com.meta.spatial.uiset.theme.SpatialTheme
import com.illuminazionetech.vrclip.ui.common.LocalDynamicColorSwitch
import com.illuminazionetech.vrclip.ui.common.LocalFixedColorRoles
import com.illuminazionetech.vrclip.ui.common.spatialDensity

fun Color.applyOpacity(enabled: Boolean): Color {
    return if (enabled) this else this.copy(alpha = 0.62f)
}

@Composable
@ReadOnlyComposable
fun Color.harmonizeWith(other: Color) =
    Color(MaterialColors.harmonize(this.toArgb(), other.toArgb()))

@Composable
@ReadOnlyComposable
fun Color.harmonizeWithPrimary(): Color =
    this.harmonizeWith(other = MaterialTheme.colorScheme.primary)

/**
 * Forces true-black surfaces on top of a derived dark scheme — a deliberate OLED-friendly
 * option (real battery savings and higher contrast on Quest's/phones' OLED panels), applied only
 * when the app is deriving its own HCT/Monet scheme rather than the system's Android 12+ dynamic
 * (wallpaper-based) colors, since forcing pure black would fight the whole point of following the
 * system palette.
 */
private fun ColorScheme.withOledBlack(): ColorScheme =
    copy(
        surface = Color.Black,
        background = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
    )

@Composable
fun VRClipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isHighContrastModeEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val density = spatialDensity()

    LaunchedEffect(darkTheme) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (darkTheme) {
                view.windowInsetsController?.setSystemBarsAppearance(
                    0,
                    APPEARANCE_LIGHT_STATUS_BARS,
                )
            } else {
                view.windowInsetsController?.setSystemBarsAppearance(
                    APPEARANCE_LIGHT_STATUS_BARS,
                    APPEARANCE_LIGHT_STATUS_BARS,
                )
            }
        }
    }

    val useSystemDynamicColor =
        LocalDynamicColorSwitch.current && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme =
        when {
            useSystemDynamicColor ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            darkTheme -> dynamicColorScheme(isLight = false).withOledBlack()
            else -> dynamicColorScheme(isLight = true)
        }

    // FixedColorRoles are brand-fixed accents: always derived from VRClip's own HCT/Monet seed
    // scheme, independent of whether the system's wallpaper-based dynamic color is active, so
    // "fixed" roles genuinely stay fixed rather than drifting with the user's wallpaper.
    val fixedColorRoles =
        FixedColorRoles.fromColorSchemes(
            lightColors = dynamicColorScheme(isLight = true),
            darkColors = dynamicColorScheme(isLight = false),
        )

    val textStyle =
        LocalTextStyle.current.copy(
            lineBreak = LineBreak.Paragraph,
            textDirection = TextDirection.Content,
        )

    CompositionLocalProvider(
        LocalFixedColorRoles provides fixedColorRoles,
        LocalTextStyle provides textStyle,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography.scaled(density.typeScale),
            shapes = Shapes.scaled(density.shapeScale),
        ) {
            if (darkTheme) {
                SpatialTheme { content() }
            } else {
                content()
            }
        }
    }
}
