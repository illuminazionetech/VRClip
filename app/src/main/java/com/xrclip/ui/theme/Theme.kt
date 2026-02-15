package com.xrclip.ui.theme

import android.os.Build
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDirection
import com.google.android.material.color.MaterialColors
import com.xrclip.ui.common.LocalFixedColorRoles

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

private val AppleLightColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F1FF),
    onPrimaryContainer = Color(0xFF007AFF),
    secondary = Color(0xFF5856D6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBEBFF),
    onSecondaryContainer = Color(0xFF5856D6),
    background = Color(0xFFF2F2F7),
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF3C3C43),
    outline = Color(0xFFC7C7CC),
    outlineVariant = Color(0xFFD1D1D6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F2F7),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEBEBEB),
    surfaceContainerHighest = Color(0xFFD1D1D6),
)

private val AppleDarkColorScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF003366),
    onPrimaryContainer = Color(0xFF0A84FF),
    secondary = Color(0xFF5E5CE6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1C1C45),
    onSecondaryContainer = Color(0xFF5E5CE6),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF1C1C1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF3A3A3C),
    onSurfaceVariant = Color(0xFFEBEBF5),
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF3A3A3C),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF2C2C2E),
    surfaceContainerHigh = Color(0xFF3A3A3C),
    surfaceContainerHighest = Color(0xFF48484A),
)

@Composable
fun XRClipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isHighContrastModeEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current

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

    val colorScheme = if (darkTheme) {
        AppleDarkColorScheme.copy(
            surface = Color.Black,
            background = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainer = Color.Black,
            surfaceContainerHigh = Color(0xFF1C1C1E),
            surfaceContainerHighest = Color(0xFF2C2C2E),
            surfaceVariant = Color(0xFF1C1C1E),
        )
    } else {
        AppleLightColorScheme
    }

    val textStyle =
        LocalTextStyle.current.copy(
            lineBreak = LineBreak.Paragraph,
            textDirection = TextDirection.Content,
        )

    CompositionLocalProvider(
        LocalFixedColorRoles provides FixedColorRoles.fromColorSchemes(
            AppleLightColorScheme,
            AppleDarkColorScheme
        ),
        LocalTextStyle provides textStyle,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}

@Composable
@Deprecated("Use XRClipTheme instead", replaceWith = ReplaceWith("XRClipTheme(content)"))
fun PreviewThemeLight(content: @Composable () -> Unit) {
    XRClipTheme(darkTheme = false, content = content)
}
