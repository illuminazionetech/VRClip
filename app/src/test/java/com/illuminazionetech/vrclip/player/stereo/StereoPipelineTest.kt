package com.illuminazionetech.vrclip.player.stereo

import com.illuminazionetech.vrclip.player.formatTime
import com.illuminazionetech.vrclip.player.speedLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StereoPipelineTest {

    @Test
    fun realtimeOutputKeepsEyeResolutionUpTo1080p() {
        assertEquals(3840 to 1080, StereoConversionEffect.outputSize(1920, 1080, realtime = true))
        assertEquals(2560 to 720, StereoConversionEffect.outputSize(1280, 720, realtime = true))
    }

    @Test
    fun realtimeOutputScalesLargeSources() {
        assertEquals(3840 to 1080, StereoConversionEffect.outputSize(3840, 2160, realtime = true))
        val (w, h) = StereoConversionEffect.outputSize(1080, 1920, realtime = true)
        assertTrue(w % 2 == 0 && h % 2 == 0)
        assertEquals(2160 to 1920, w to h)
    }

    @Test
    fun halfPackingKeepsTheSourceSize() {
        assertEquals(
            3840 to 2160,
            StereoConversionEffect.outputSize(
                3840,
                2160,
                realtime = false,
                packing = StereoPacking.Half,
            ),
        )
    }

    @Test
    fun depthNormalizationIgnoresOutliersAndFlatInput() {
        val size = 64
        val processor = DepthPostProcessor(size, mapBlend = 1f, rangeBlend = 1f)
        val out = ByteArray(size * size)

        processor.process(FloatArray(size * size) { 5f }, out)
        assertTrue(out.all { (it.toInt() and 0xFF) == 128 })

        processor.reset()
        // A left-to-right ramp with one huge outlier: the ramp must still use the full range.
        val raw = FloatArray(size * size) { i -> (i % size).toFloat() }
        raw[0] = 10_000f
        processor.process(raw, out)
        val left = out[size * 32 + 4].toInt() and 0xFF
        val right = out[size * 32 + size - 5].toInt() and 0xFF
        assertTrue("left $left right $right", left < 30 && right > 225)
    }

    @Test
    fun temporalSmoothingBlendsButSceneCutsReset() {
        val size = 32
        val processor = DepthPostProcessor(size, mapBlend = 0.5f, rangeBlend = 1f)
        val out = ByteArray(size * size)
        val ramp = FloatArray(size * size) { i -> (i % size).toFloat() }
        processor.process(ramp, out)
        val before = out[size * 16 + 28].toInt() and 0xFF
        // Same scene, slightly different: blended, so it moves only part of the way.
        val shifted =
            FloatArray(size * size) { i -> (i % size).toFloat() + if (i % size > 24) 2f else 0f }
        processor.process(shifted, out)
        val after = out[size * 16 + 28].toInt() and 0xFF
        assertTrue(after >= before)

        // A reversed ramp is a different shot: no blending with the previous map.
        val reversed = FloatArray(size * size) { i -> (size - 1 - i % size).toFloat() }
        processor.process(reversed, out)
        val nearLeft = out[size * 16 + 3].toInt() and 0xFF
        assertTrue("near $nearLeft", nearLeft > 200)
    }

    @Test
    fun timeAndSpeedLabels() {
        assertEquals("0:00", formatTime(0))
        assertEquals("1:05", formatTime(65_000))
        assertEquals("1:00:00", formatTime(3_600_000))
        assertEquals("1", speedLabel(1f))
        assertEquals("1.5", speedLabel(1.5f))
        assertEquals("1.25", speedLabel(1.25f))
    }
}
