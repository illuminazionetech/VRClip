@file:OptIn(ExperimentalTextApi::class, ExperimentalTextApi::class, ExperimentalTextApi::class)

package com.illuminazionetech.vrclip.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

val Typography =
    Typography().run {
        copy(
            displayLarge = displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp).applyTextDirection(),
            displayMedium = displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp).applyTextDirection(),
            displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold).applyTextDirection(),
            headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp).applyTextDirection(),
            headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold).applyTextDirection(),
            headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold).applyTextDirection(),
            titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp).applyTextDirection(),
            titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp).applyTextDirection(),
            titleSmall = titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp).applyTextDirection(),
            bodyLarge = bodyLarge.copy(fontSize = 17.sp).applyLinebreak().applyTextDirection(),
            bodyMedium = bodyMedium.copy(fontSize = 15.sp).applyLinebreak().applyTextDirection(),
            bodySmall = bodySmall.copy(fontSize = 13.sp).applyLinebreak().applyTextDirection(),
            labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp).applyTextDirection(),
            labelMedium = labelMedium.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp).applyTextDirection(),
            labelSmall = labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp).applyTextDirection(),
        )
    }

private fun TextStyle.applyLinebreak(): TextStyle = this.copy(lineBreak = LineBreak.Paragraph)

private fun TextStyle.applyTextDirection(): TextStyle =
    this.copy(textDirection = TextDirection.Content)

/**
 * Scales every text style's font size by [factor], used to bump type size up on the Meta Quest
 * spatial panel, where text is read from roughly 10 feet away instead of held in the hand.
 */
fun Typography.scaled(factor: Float): Typography =
    if (factor == 1f) this
    else
        copy(
            displayLarge = displayLarge.scaled(factor),
            displayMedium = displayMedium.scaled(factor),
            displaySmall = displaySmall.scaled(factor),
            headlineLarge = headlineLarge.scaled(factor),
            headlineMedium = headlineMedium.scaled(factor),
            headlineSmall = headlineSmall.scaled(factor),
            titleLarge = titleLarge.scaled(factor),
            titleMedium = titleMedium.scaled(factor),
            titleSmall = titleSmall.scaled(factor),
            bodyLarge = bodyLarge.scaled(factor),
            bodyMedium = bodyMedium.scaled(factor),
            bodySmall = bodySmall.scaled(factor),
            labelLarge = labelLarge.scaled(factor),
            labelMedium = labelMedium.scaled(factor),
            labelSmall = labelSmall.scaled(factor),
        )

/** Scales size and line height together: scaling only the size makes lines overlap. */
private fun TextStyle.scaled(factor: Float): TextStyle =
    copy(
        fontSize = fontSize * factor,
        lineHeight = if (lineHeight.isSpecified) lineHeight * factor else lineHeight,
    )
