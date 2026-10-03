package com.illuminazionetech.vrclip.player.stereo

import com.illuminazionetech.vrclip.player.formatTime
import com.illuminazionetech.vrclip.player.speedLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StereoPipelineTest {

    private val sbs = StereoConversionEffect.Output.LiveSideBySide
    private val screen = StereoConversionEffect.Output.LiveColorAndDepth
    private val conversion = StereoConversionEffect.Output.Conversion

    @Test
    fun liveSideBySideKeepsEyeResolutionUpToTheHeadsetWidth() {
        assertEquals(3840 to 1080, StereoConversionEffect.outputSize(1920, 1080, sbs))
        assertEquals(2560 to 720, StereoConversionEffect.outputSize(1280, 720, sbs))
        assertEquals(4096 to 1152, StereoConversionEffect.outputSize(3840, 2160, sbs))
    }

    @Test
    fun liveScreenOutputStacksThePictureOverItsDepth() {
        assertEquals(1920 to 2160, StereoConversionEffect.outputSize(1920, 1080, screen))
        assertEquals(1920 to 2160, StereoConversionEffect.outputSize(3840, 2160, screen))
        // Portrait video stays within the texture limit.
        val (w, h) = StereoConversionEffect.outputSize(1080, 1920, screen)
        assertTrue(w % 2 == 0 && h % 4 == 0 && h <= 4096)
        assertEquals(1080 to 3840, w to h)
    }

    @Test
    fun conversionPackingKeepsTheSourceResolution() {
        assertEquals(7680 to 2160, StereoConversionEffect.outputSize(3840, 2160, conversion))
        assertEquals(
            3840 to 2160,
            StereoConversionEffect.outputSize(3840, 2160, conversion, StereoPacking.Half),
        )
    }

    private fun value(out: ByteArray, index: Int) = out[index].toInt() and 0xFF

    @Test
    fun depthNormalizationIgnoresOutliersAndFlatInput() {
        val size = 64
        val refiner = DepthRefiner(size, stillBlend = 1f, rangeBlend = 1f, dilation = 1)
        val out = ByteArray(size * size)

        refiner.process(FloatArray(size * size) { 5f }, out)
        assertTrue(out.all { (it.toInt() and 0xFF) == 128 })

        refiner.reset()
        // A left-to-right ramp with one huge outlier: the ramp must still use the full range.
        val raw = FloatArray(size * size) { i -> (i % size).toFloat() }
        raw[size * 60] = 10_000f
        refiner.process(raw, out)
        val left = value(out, size * 32 + 4)
        val right = value(out, size * 32 + size - 5)
        assertTrue("left $left right $right", left < 30 && right > 225)
    }

    @Test
    fun smallChangesAreSmoothedButMotionPassesAtOnce() {
        val size = 32
        val refiner = DepthRefiner(size, stillBlend = 0.25f, rangeBlend = 1f, dilation = 1)
        val out = ByteArray(size * size)
        val ramp = FloatArray(size * size) { i -> (i % size).toFloat() }
        refiner.process(ramp, out)
        val probe = size * 16 + 10
        val before = value(out, probe)

        // Jitter of about 1% of the range: only a quarter of it gets through.
        refiner.process(FloatArray(size * size) { i -> ramp[i] + 0.3f }, out)
        val jittered = value(out, probe)
        assertTrue("before $before jittered $jittered", jittered - before in 0..2)

        // A jump of 30% of the range in one area is motion: it is taken as is.
        val moved = FloatArray(size * size) { i -> ramp[i] + if (i % size in 8..12) 9f else 0f }
        refiner.process(moved, out)
        val after = value(out, probe)
        assertTrue("before $before after $after", after - before > 60)
    }

    @Test
    fun nearObjectsGrowByTheDilationRadius() {
        val size = 40
        val refiner = DepthRefiner(size, stillBlend = 1f, rangeBlend = 1f, dilation = 2)
        val out = ByteArray(size * size)
        // A near square (columns and rows 15..24) in front of a far background.
        val raw =
            FloatArray(size * size) { i ->
                val x = i % size
                val y = i / size
                if (x in 15..24 && y in 15..24) 1f else 0f
            }
        refiner.process(raw, out)
        assertTrue(value(out, 20 * size + 13) > 120)
        assertTrue(value(out, 20 * size + 10) < 10)
    }

    @Test
    fun aSceneCutRestartsTheSmoothing() {
        val size = 32
        val refiner = DepthRefiner(size, stillBlend = 0.1f, rangeBlend = 0.1f, dilation = 1)
        val out = ByteArray(size * size)
        refiner.process(FloatArray(size * size) { i -> (i % size).toFloat() }, out)
        // A reversed ramp is a different shot: no blending with the previous map.
        refiner.process(FloatArray(size * size) { i -> (size - 1 - i % size).toFloat() }, out)
        val nearLeft = value(out, size * 16 + 3)
        assertTrue("near $nearLeft", nearLeft > 200)
    }

    @Test
    fun selfTestGridAndCorrelation() {
        val size = 28
        val depth = FloatArray(size * size) { i -> (i % size).toFloat() }
        val grid = DepthEstimator.patchGrid(depth, size)
        assertEquals(4, grid.size)
        assertEquals(6.5f, grid[0], 1e-4f)
        assertEquals(20.5f, grid[1], 1e-4f)
        assertEquals(1f, DepthEstimator.correlation(grid, grid), 1e-6f)
        val inverted = FloatArray(grid.size) { -grid[it] }
        assertEquals(-1f, DepthEstimator.correlation(grid, inverted), 1e-6f)
        assertEquals(0f, DepthEstimator.correlation(grid, FloatArray(grid.size) { 1f }), 0f)
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
