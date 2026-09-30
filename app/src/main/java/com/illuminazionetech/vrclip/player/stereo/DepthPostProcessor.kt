package com.illuminazionetech.vrclip.player.stereo

import kotlin.math.abs

/**
 * Turns the model's raw relative inverse depth into an 8-bit map ready for view synthesis:
 * robust normalization (2nd to 98th percentile, so a few outliers cannot flatten the scene), a
 * light Gaussian blur (soft depth edges hide warping seams), and temporal smoothing of both the
 * range and the map itself so the 3D effect does not flicker between frames. A scene cut resets
 * the smoothing instead of blending two unrelated shots.
 */
class DepthPostProcessor(
    private val size: Int,
    /** Weight of the newest frame in the temporal blend (1 = no smoothing). */
    private val mapBlend: Float,
    /** Weight of the newest frame in the normalization range blend. */
    private val rangeBlend: Float,
) {
    private val count = size * size
    private val normalized = FloatArray(count)
    private val scratch = FloatArray(count)
    private var previous: FloatArray? = null
    private var low = Float.NaN
    private var high = Float.NaN
    private val sample = FloatArray((count + SAMPLE_STRIDE - 1) / SAMPLE_STRIDE)

    fun reset() {
        previous = null
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

        for (j in 0 until count) {
            normalized[j] = normalize(raw[j], frameLow, frameHigh)
        }
        blur(normalized)

        val last = previous
        val sceneCut = last == null || meanAbsoluteDifference(normalized, last) > SCENE_CUT
        if (sceneCut || low.isNaN()) {
            low = frameLow
            high = frameHigh
        } else {
            low += (frameLow - low) * rangeBlend
            high += (frameHigh - high) * rangeBlend
        }
        // Normalize again with the smoothed range so that brightness-like jumps in the model's
        // scale do not pump the whole scene back and forth.
        for (j in 0 until count) {
            normalized[j] = normalize(raw[j], low, high)
        }
        blur(normalized)

        val current = last?.takeUnless { sceneCut }
        val keep = previous ?: FloatArray(count).also { previous = it }
        for (j in 0 until count) {
            val value =
                if (current != null) current[j] + (normalized[j] - current[j]) * mapBlend
                else normalized[j]
            keep[j] = value
            out[j] = (value * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
        }
    }

    private fun normalize(value: Float, lo: Float, hi: Float): Float {
        val range = hi - lo
        if (range <= 1e-6f) return 0.5f
        return ((value - lo) / range).coerceIn(0f, 1f)
    }

    /** Separable 5-tap binomial blur (1 4 6 4 1) with clamped edges. */
    private fun blur(data: FloatArray) {
        for (y in 0 until size) {
            val row = y * size
            for (x in 0 until size) {
                val a = data[row + (x - 2).coerceAtLeast(0)]
                val b = data[row + (x - 1).coerceAtLeast(0)]
                val c = data[row + x]
                val d = data[row + (x + 1).coerceAtMost(size - 1)]
                val e = data[row + (x + 2).coerceAtMost(size - 1)]
                scratch[row + x] = (a + 4 * b + 6 * c + 4 * d + e) / 16f
            }
        }
        for (y in 0 until size) {
            val up2 = (y - 2).coerceAtLeast(0) * size
            val up1 = (y - 1).coerceAtLeast(0) * size
            val mid = y * size
            val dn1 = (y + 1).coerceAtMost(size - 1) * size
            val dn2 = (y + 2).coerceAtMost(size - 1) * size
            for (x in 0 until size) {
                data[mid + x] =
                    (scratch[up2 + x] +
                        4 * scratch[up1 + x] +
                        6 * scratch[mid + x] +
                        4 * scratch[dn1 + x] +
                        scratch[dn2 + x]) / 16f
            }
        }
    }

    private fun meanAbsoluteDifference(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        var n = 0
        var i = 0
        while (i < count) {
            sum += abs(a[i] - b[i])
            n++
            i += SAMPLE_STRIDE
        }
        return if (n == 0) 0f else sum / n
    }

    companion object {
        private const val SAMPLE_STRIDE = 7
        private const val SCENE_CUT = 0.18f
    }
}
