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
import com.meta.spatial.uiset.theme.SpatialTheme
import com.xrclip.ui.common.LocalDarkTheme
import com.xrclip.ui.common.LocalFixedColorRoles
import com.xrclip.ui.common.LocalIsVRMode

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

// Material You – VRClip Blue seed (#0A6EFF)
private val VRClipLightColorScheme = lightColorScheme(
    primary = Color(0xFF0059D8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E3FF),
    onPrimaryContainer = Color(0xFF001647),
    secondary = Color(0xFF545F71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF111C2C),
    tertiary = Color(0xFF6A5679),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251432),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF1A1B23),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF1A1B23),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEEEEF5),
    surfaceContainerHigh = Color(0xFFE8E8EF),
    surfaceContainerHighest = Color(0xFFE2E2EA),
)

private val VRClipDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA3C4FF),
    onPrimary = Color(0xFF002B75),
    primaryContainer = Color(0xFF0043A5),
    onPrimaryContainer = Color(0xFFD1E3FF),
    secondary = Color(0xFFBBC6DD),
    onSecondary = Color(0xFF263040),
    secondaryContainer = Color(0xFF3C4758),
    onSecondaryContainer = Color(0xFFD8E3F8),
    tertiary = Color(0xFFD8BCE6),
    onTertiary = Color(0xFF3B2848),
    tertiaryContainer = Color(0xFF523E60),
    onTertiaryContainer = Color(0xFFF2DAFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF12131A),
    onBackground = Color(0xFFE2E2EA),
    surface = Color(0xFF12131A),
    onSurface = Color(0xFFE2E2EA),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
    surfaceContainerLowest = Color(0xFF0D0E15),
    surfaceContainerLow = Color(0xFF1A1B23),
    surfaceContainer = Color(0xFF1E1F27),
    surfaceContainerHigh = Color(0xFF282A32),
    surfaceContainerHighest = Color(0xFF333540),
)

@Composable
fun XRClipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isHighContrastModeEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val isVRMode = LocalIsVRMode.current

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

    val colorScheme = if (darkTheme) VRClipDarkColorScheme else VRClipLightColorScheme

    val textStyle =
        LocalTextStyle.current.copy(
            lineBreak = LineBreak.Paragraph,
            textDirection = TextDirection.Content,
        )

    CompositionLocalProvider(
        LocalFixedColorRoles provides
            FixedColorRoles.fromColorSchemes(VRClipLightColorScheme, VRClipDarkColorScheme),
        LocalTextStyle provides textStyle,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
        ) {
            if (isVRMode) {
                SpatialTheme { content() }
            } else {
                content()
            }
        }
    }
}

@Composable
@Deprecated("Use XRClipTheme instead", replaceWith = ReplaceWith("XRClipTheme(content)"))
fun PreviewThemeLight(content: @Composable () -> Unit) {
    XRClipTheme(darkTheme = false, content = content)
}
