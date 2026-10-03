package com.illuminazionetech.vrclip.player.stereo

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns the model's raw relative inverse depth into the 8-bit map used for view synthesis (0
 * farthest .. 255 nearest), steady from one frame to the next:
 * - the scale is the 2nd to 98th percentile of the frame, so a few outliers cannot flatten the
 *   scene, and it follows the video slowly, so the whole picture does not pump in and out;
 * - each pixel is smoothed over time in proportion to how little it changed: small changes are the
 *   model's frame-to-frame jitter and get averaged away, large ones are real motion and pass at
 *   once, so moving objects keep their depth instead of trailing behind;
 * - near objects are grown by [dilation] pixels, so the edge of the picture's foreground stays on
 *   the foreground and the gaps it opens when the views are shifted fill from the background;
 * - a scene cut (most of the picture changing at once) restarts all of this.
 */
class DepthRefiner(
    private val size: Int,
    /** Weight of the newest frame where the depth barely changed (1 = no smoothing). */
    private val stillBlend: Float,
    /** Weight of the newest frame in the scale (percentile) average. */
    private val rangeBlend: Float,
    private val dilation: Int = (size / 260f).roundToInt().coerceAtLeast(1),
) {
    private val count = size * size
    private val normalized = FloatArray(count)
    private val scratch = FloatArray(count)
    private var smoothed: FloatArray? = null
    private var low = Float.NaN
    private var high = Float.NaN
    private val sample = FloatArray((count + SAMPLE_STRIDE - 1) / SAMPLE_STRIDE)

    fun reset() {
        smoothed = null
        low = Float.NaN
        high = Float.NaN
    }

    /** Writes [size] x [size] depth values (0 far .. 255 near) into [out]. */
    fun process(raw: FloatArray, out: ByteArray) {
        require(raw.size >= count && out.size >= count)

        var n = 0
        var i = 0
        while (i < count) {
            sample[n++] = raw[i]
            i += SAMPLE_STRIDE
        }
        sample.sort(0, n)
        val frameLow = sample[(n * 0.02f).toInt().coerceIn(0, n - 1)]
        val frameHigh = sample[(n * 0.98f).toInt().coerceIn(0, n - 1)]

        val previous = smoothed
        val cut =
            previous == null || low.isNaN() || sceneChanged(raw, previous, frameLow, frameHigh)
        if (cut) {
            low = frameLow
            high = frameHigh
        } else {
            low += (frameLow - low) * rangeBlend
            high += (frameHigh - high) * rangeBlend
        }
        for (j in 0 until count) normalized[j] = normalize(raw[j], low, high)

        val state = previous ?: FloatArray(count).also { smoothed = it }
        if (cut) {
            normalized.copyInto(state)
        } else {
            for (j in 0 until count) {
                val old = state[j]
                val change = abs(normalized[j] - old)
                // Below STILL the change is treated as jitter, above MOVING as motion.
                val motion = ((change - STILL) / (MOVING - STILL)).coerceIn(0f, 1f)
                val weight = stillBlend + (1f - stillBlend) * motion
                state[j] = old + (normalized[j] - old) * weight
            }
        }

        dilate(state, normalized)
        smooth3(normalized)
        for (j in 0 until count) {
            out[j] = (normalized[j] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
        }
    }

    private fun normalize(value: Float, lo: Float, hi: Float): Float {
        val range = hi - lo
        if (range <= 1e-6f) return 0.5f
        return ((value - lo) / range).coerceIn(0f, 1f)
    }

    /** True when the new frame, on the old scale, differs from the old map almost everywhere. */
    private fun sceneChanged(raw: FloatArray, old: FloatArray, lo: Float, hi: Float): Boolean {
        var sum = 0f
        var n = 0
        var i = 0
        while (i < count) {
            sum += abs(normalize(raw[i], lo, hi) - old[i])
            n++
            i += SAMPLE_STRIDE
        }
        return n > 0 && sum / n > SCENE_CUT
    }

    /** Separable max filter of radius [dilation]: nearer values spread over farther ones. */
    private fun dilate(source: FloatArray, target: FloatArray) {
        val r = dilation
        for (y in 0 until size) {
            val row = y * size
            for (x in 0 until size) {
                var m = source[row + x]
                for (k in 1..r) {
                    val left = source[row + (x - k).coerceAtLeast(0)]
                    val right = source[row + (x + k).coerceAtMost(size - 1)]
                    if (left > m) m = left
                    if (right > m) m = right
                }
                scratch[row + x] = m
            }
        }
        for (y in 0 until size) {
            for (x in 0 until size) {
                var m = scratch[y * size + x]
                for (k in 1..r) {
                    val up = scratch[(y - k).coerceAtLeast(0) * size + x]
                    val down = scratch[(y + k).coerceAtMost(size - 1) * size + x]
                    if (up > m) m = up
                    if (down > m) m = down
                }
                target[y * size + x] = m
            }
        }
    }

    /**
     * 3-tap binomial blur (1 2 1) in both directions, to soften the steps the max filter leaves.
     */
    private fun smooth3(data: FloatArray) {
        for (y in 0 until size) {
            val row = y * size
            for (x in 0 until size) {
                val a = data[row + (x - 1).coerceAtLeast(0)]
                val b = data[row + x]
                val c = data[row + (x + 1).coerceAtMost(size - 1)]
                scratch[row + x] = (a + 2 * b + c) * 0.25f
            }
        }
        for (y in 0 until size) {
            val up = (y - 1).coerceAtLeast(0) * size
            val mid = y * size
            val down = (y + 1).coerceAtMost(size - 1) * size
            for (x in 0 until size) {
                data[mid + x] = (scratch[up + x] + 2 * scratch[mid + x] + scratch[down + x]) * 0.25f
            }
        }
    }

    companion object {
        private const val SAMPLE_STRIDE = 7
        private const val SCENE_CUT = 0.2f
        private const val STILL = 0.015f
        private const val MOVING = 0.07f
    }
}
