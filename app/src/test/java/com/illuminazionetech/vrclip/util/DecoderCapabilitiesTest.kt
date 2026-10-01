package com.illuminazionetech.vrclip.util

import com.illuminazionetech.vrclip.util.DecoderCapabilities.Codec
import com.illuminazionetech.vrclip.util.DecoderCapabilities.Decoder
import org.junit.Assert.assertEquals
import org.junit.Test

/** The high-resolution download preset follows what the device can decode. */
class DecoderCapabilitiesTest {

    private fun sort(vararg decoders: Decoder) =
        DecoderCapabilities.highResolutionSort(decoders.toList())

    @Test
    fun aDeviceWithAv1At8kGetsEverything() {
        assertEquals(
            "res:4320,vcodec:av01",
            sort(Decoder(Codec.Av1, 4320), Decoder(Codec.Vp9, 2160), Decoder(Codec.Avc, 1080)),
        )
    }

    @Test
    fun withoutAv1TheBestRemainingCodecLeads() {
        // Quest 2: no AV1, VP9 and HEVC up to 5.7K 360.
        assertEquals(
            "res:2880,vcodec:vp9",
            sort(Decoder(Codec.Vp9, 2880), Decoder(Codec.Hevc, 2880), Decoder(Codec.Avc, 2160)),
        )
    }

    @Test
    fun theCodecThatReachesTheHighestResolutionWins() {
        // AV1 only in 1080p but VP9 in 4K: 4K VP9 is the better download.
        assertEquals(
            "res:2160,vcodec:vp9",
            sort(Decoder(Codec.Av1, 1080), Decoder(Codec.Vp9, 2160)),
        )
    }

    @Test
    fun noHardwareDecodersFallsBackTo1080pH264() {
        assertEquals("res:1080,vcodec:h264", sort())
    }
}
