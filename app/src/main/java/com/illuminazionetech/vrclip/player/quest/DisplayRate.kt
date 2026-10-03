package com.illuminazionetech.vrclip.player.quest

import kotlin.math.abs

/**
 * Headset refresh rates for a video, best first. A rate that is a whole multiple of the frame rate
 * shows every frame for the same number of refreshes, so motion does not judder (24 fps at 72 Hz,
 * 30 at 90, 60 at 120). Rates without such a match, and unknown ones, get 72 Hz, the lowest refresh
 * every supported Quest offers and the one that costs the least battery.
 */
internal fun videoDisplayRates(frameRate: Float): List<Float> {
    fun near(target: Float) = abs(frameRate - target) < 0.6f
    val matching =
        when {
            near(24f) -> listOf(72f, 120f)
            near(30f) -> listOf(90f, 120f)
            near(60f) -> listOf(120f)
            near(90f) -> listOf(90f)
            near(120f) -> listOf(120f)
            else -> emptyList()
        }
    return (matching + DEFAULT_DISPLAY_RATE).distinct()
}

internal const val DEFAULT_DISPLAY_RATE = 72f
