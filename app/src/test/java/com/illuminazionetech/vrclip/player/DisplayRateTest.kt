package com.illuminazionetech.vrclip.player

import com.illuminazionetech.vrclip.player.quest.videoDisplayRates
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayRateTest {

    @Test
    fun refreshIsAWholeMultipleOfTheFrameRate() {
        assertEquals(listOf(72f, 120f), videoDisplayRates(23.976f))
        assertEquals(listOf(72f, 120f), videoDisplayRates(24f))
        assertEquals(listOf(90f, 120f, 72f), videoDisplayRates(29.97f))
        assertEquals(listOf(120f, 72f), videoDisplayRates(59.94f))
    }

    @Test
    fun otherRatesStayAtTheDefault() {
        assertEquals(listOf(72f), videoDisplayRates(25f))
        assertEquals(listOf(72f), videoDisplayRates(50f))
        assertEquals(listOf(72f), videoDisplayRates(0f))
    }
}
