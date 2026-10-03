package com.illuminazionetech.vrclip.player.stereo

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Runs a [DepthModel] with LiteRT: on the GPU when the device supports it (the compiled program
 * cached on disk so later sessions start quickly), on the CPU otherwise. Input is an RGB image of
 * [size] x [size] pixels as RGBA bytes; output is the relative inverse depth for each pixel (larger
 * means closer). Not thread safe: use from one thread.
 */
class DepthEstimator
private constructor(
    private val compiled: CompiledModel,
    val model: DepthModel,
    val accelerator: Accelerator,
) : AutoCloseable {

    val size = model.inputSize

    /** How long one inference took during the self-test, in milliseconds. */
    var inferenceMillis = 0L
        private set

    private val inputs: List<TensorBuffer> = compiled.createInputBuffers()
    private val outputs: List<TensorBuffer> = compiled.createOutputBuffers()
    private val input = FloatArray(size * size * 3)

    /**
     * [rgba] holds size x size pixels, 4 bytes each, rows top to bottom, or bottom to top when
     * [bottomUp] is set (as glReadPixels returns them). Returns size x size depth values, rows top
     * to bottom.
     */
    fun estimate(rgba: ByteArray, bottomUp: Boolean = false): FloatArray {
        var j = 0
        for (y in 0 until size) {
            var i = (if (bottomUp) size - 1 - y else y) * size * 4
            repeat(size) {
                input[j++] = (rgba[i].toInt() and 0xFF) * INV_255
                input[j++] = (rgba[i + 1].toInt() and 0xFF) * INV_255
                input[j++] = (rgba[i + 2].toInt() and 0xFF) * INV_255
                i += 4
            }
        }
        inputs[0].writeFloat(input)
        compiled.run(inputs, outputs)
        return outputs[0].readFloat()
    }

    override fun close() {
        inputs.forEach { it.close() }
        outputs.forEach { it.close() }
        compiled.close()
    }

    companion object {
        private const val TAG = "DepthEstimator"
        private const val INV_255 = 1f / 255f

        /** Below this, a reduced precision GPU run has drifted too far from the reference. */
        private const val MIN_SELF_TEST_CORRELATION = 0.985f

        /**
         * Loads [model], preferring the GPU in half precision. Each GPU candidate runs the bundled
         * self-test image and must reproduce the reference depth (computed on the desktop at full
         * precision): some drivers overflow in half precision without producing NaNs, which would
         * otherwise show up as a wobbly, wrong 3D. It then retries in full precision, and finally
         * falls back to the CPU.
         */
        fun create(context: Context, model: DepthModel, modelFile: File): DepthEstimator {
            val cacheDir = File(context.cacheDir, "litert").apply { mkdirs() }
            val selfTest = SelfTest.load(context, model)
            val attempts =
                listOf(
                    Accelerator.GPU to CompiledModel.GpuOptions.Precision.FP16,
                    Accelerator.GPU to CompiledModel.GpuOptions.Precision.FP32,
                    Accelerator.CPU to null,
                )
            var lastError: Throwable? = null
            for ((accelerator, precision) in attempts) {
                val estimator =
                    runCatching {
                        val options = CompiledModel.Options(accelerator)
                        if (precision != null) {
                            options.gpuOptions =
                                CompiledModel.GpuOptions(
                                    precision = precision,
                                    infiniteFloatCapping = true,
                                    serializationDir = cacheDir.absolutePath,
                                    modelCacheKey =
                                        "${model.name}_${model.sha256.take(12)}_${precision.name}",
                                    serializeProgramCache = true,
                                )
                        } else {
                            options.cpuOptions =
                                CompiledModel.CpuOptions(
                                    numThreads =
                                        Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
                                )
                        }
                        DepthEstimator(
                            CompiledModel.create(modelFile.absolutePath, options),
                            model,
                            accelerator,
                        )
                    }
                        .onFailure {
                            lastError = it
                            Log.w(TAG, "Cannot load $model on $accelerator/$precision", it)
                        }
                        .getOrNull() ?: continue
                val correlation = runCatching { estimator.runSelfTest(selfTest) }.getOrNull()
                if (
                    accelerator == Accelerator.CPU && correlation != null ||
                        correlation != null && correlation >= MIN_SELF_TEST_CORRELATION
                ) {
                    Log.i(
                        TAG,
                        "$model on $accelerator ${precision ?: ""}: " +
                            "${estimator.inferenceMillis} ms, self-test correlation $correlation",
                    )
                    return estimator
                }
                Log.w(TAG, "$model on $accelerator/$precision failed the self-test ($correlation)")
                estimator.close()
            }
            throw IllegalStateException("The depth model cannot run on this device", lastError)
        }

        /** Runs the self-test twice (the first run warms up) and returns its correlation. */
        private fun DepthEstimator.runSelfTest(test: SelfTest): Float {
            estimate(test.rgba)
            val start = SystemClock.elapsedRealtime()
            val depth = estimate(test.rgba)
            inferenceMillis = SystemClock.elapsedRealtime() - start
            for (v in depth) if (v.isNaN() || v.isInfinite()) return 0f
            return correlation(patchGrid(depth, size), test.expected)
        }

        /** Mean of each 14 x 14 patch, the grid the reference was stored at. */
        internal fun patchGrid(depth: FloatArray, size: Int): FloatArray {
            val g = size / 14
            val grid = FloatArray(g * g)
            for (y in 0 until g * 14) {
                val row = y * size
                val gy = (y / 14) * g
                for (x in 0 until g * 14) grid[gy + x / 14] += depth[row + x]
            }
            for (i in grid.indices) grid[i] /= 196f
            return grid
        }

        internal fun correlation(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size || a.isEmpty()) return 0f
            val meanA = a.average()
            val meanB = b.average()
            var ab = 0.0
            var aa = 0.0
            var bb = 0.0
            for (i in a.indices) {
                val da = a[i] - meanA
                val db = b[i] - meanB
                ab += da * db
                aa += da * da
                bb += db * db
            }
            if (aa <= 0.0 || bb <= 0.0) return 0f
            return (ab / sqrt(aa * bb)).toFloat()
        }
    }

    /** The bundled self-test image at the model's input size, and its reference depth grid. */
    private class SelfTest(val rgba: ByteArray, val expected: FloatArray) {
        companion object {
            fun load(context: Context, model: DepthModel): SelfTest {
                val size = model.inputSize
                val bitmap =
                    context.assets.open(model.selfTestImage).use { BitmapFactory.decodeStream(it) }
                check(bitmap.width == size && bitmap.height == size)
                val pixels = IntArray(size * size)
                bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
                val rgba = ByteArray(size * size * 4)
                for (i in pixels.indices) {
                    val p = pixels[i]
                    rgba[i * 4] = (p shr 16).toByte()
                    rgba[i * 4 + 1] = (p shr 8).toByte()
                    rgba[i * 4 + 2] = p.toByte()
                    rgba[i * 4 + 3] = -1
                }
                val bytes = context.assets.open(model.selfTestExpected).use { it.readBytes() }
                val expected = FloatArray(bytes.size / 4)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(expected)
                return SelfTest(rgba, expected)
            }
        }
    }
}
