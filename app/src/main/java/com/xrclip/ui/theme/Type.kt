@file:OptIn(ExperimentalTextApi::class)

package com.xrclip.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDirection

val Typography =
    Typography().run {
        copy(
            displayLarge = displayLarge.copy(fontWeight = FontWeight.Bold).applyTextDirection(),
            displayMedium = displayMedium.copy(fontWeight = FontWeight.Bold).applyTextDirection(),
            displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold).applyTextDirection(),
            headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold).applyTextDirection(),
            headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold).applyTextDirection(),
            headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold).applyTextDirection(),
            titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold).applyTextDirection(),
            titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold).applyTextDirection(),
            titleSmall = titleSmall.copy(fontWeight = FontWeight.Medium).applyTextDirection(),
            bodyLarge = bodyLarge.copy().applyLinebreak().applyTextDirection(),
            bodyMedium = bodyMedium.copy().applyLinebreak().applyTextDirection(),
            bodySmall = bodySmall.copy().applyLinebreak().applyTextDirection(),
            labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium).applyTextDirection(),
            labelMedium = labelMedium.copy(fontWeight = FontWeight.Medium).applyTextDirection(),
            labelSmall = labelSmall.copy(fontWeight = FontWeight.Medium).applyTextDirection(),
        )
    }

private fun TextStyle.applyLinebreak(): TextStyle = this.copy(lineBreak = LineBreak.Paragraph)

private fun TextStyle.applyTextDirection(): TextStyle =
    this.copy(textDirection = TextDirection.Content)
