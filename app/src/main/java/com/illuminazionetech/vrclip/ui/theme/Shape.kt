package com.illuminazionetech.vrclip.ui.theme

import androidx.compose.foundation.shape.AbsoluteCutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive shape scale: a wider, more varied spread of corner radii than the
 * classic M3 scale, so surfaces read as distinctly "small/medium/large" at a glance rather than
 * all sharing one rounded look.
 */
val Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(40.dp),
    )

/**
 * Expressive accent shape reserved for hero/emphasis surfaces (e.g. a primary FAB or the brand
 * mark on the About screen), an asymmetric cut-corner shape, distinct from the rounded-rect
 * vocabulary used everywhere else, per M3 Expressive's varied-shape guidance for a small number
 * of high-emphasis elements.
 */
val ExpressiveAccentShape =
    AbsoluteCutCornerShape(
        topLeft = 28.dp,
        topRight = 8.dp,
        bottomLeft = 8.dp,
        bottomRight = 28.dp,
    )

/** Scales a [Shapes] set by [factor], used to enlarge corner radii for the Quest spatial panel. */
fun Shapes.scaled(factor: Float): Shapes =
    if (factor == 1f) this
    else
        Shapes(
            extraSmall = RoundedCornerShape((6f * factor).dp),
            small = RoundedCornerShape((10f * factor).dp),
            medium = RoundedCornerShape((18f * factor).dp),
            large = RoundedCornerShape((28f * factor).dp),
            extraLarge = RoundedCornerShape((40f * factor).dp),
        )
