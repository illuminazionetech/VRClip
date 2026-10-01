package com.illuminazionetech.vrclip.player.stereo

import android.content.Context
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import java.io.File

/**
 * Runs the depth model with LiteRT: on the GPU when the device supports it (half precision with
 * infinity capping, the compiled program cached on disk so later sessions start quickly), on the
 * CPU otherwise. Input is an RGB image of [size] x [size] pixels as bytes; output is the relative
 * inverse depth for each pixel (larger means closer). Not thread safe: use from one thread.
 */
class DepthEstimator
private constructor(
    private val model: CompiledModel,
    val accelerator: Accelerator,
) : AutoCloseable {

    val size = DepthModelManager.INPUT_SIZE

    private val inputs: List<TensorBuffer> = model.createInputBuffers()
    private val outputs: List<TensorBuffer> = model.createOutputBuffers()
    private val input = FloatArray(size * size * 3)

    /**
     * [rgba] holds size x size pixels, 4 bytes each, rows top to bottom (as produced by
     * glReadPixels on a flipped render). Returns size x size depth values, rows top to bottom.
     */
    fun estimate(rgba: ByteArray): FloatArray {
        var j = 0
        var i = 0
        val count = size * size
        repeat(count) {
            input[j++] = (rgba[i].toInt() and 0xFF) * INV_255
            input[j++] = (rgba[i + 1].toInt() and 0xFF) * INV_255
            input[j++] = (rgba[i + 2].toInt() and 0xFF) * INV_255
            i += 4
        }
        inputs[0].writeFloat(input)
        model.run(inputs, outputs)
        return outputs[0].readFloat()
    }

    override fun close() {
        inputs.forEach { it.close() }
        outputs.forEach { it.close() }
        model.close()
    }

    companion object {
        private const val TAG = "DepthEstimator"
        private const val INV_255 = 1f / 255f

        /**
         * Loads the model, preferring the GPU. The first GPU inference is checked: a result with
         * NaN/infinite values or no variation at all (half precision overflow on some drivers)
         * makes it retry in full precision, and then on the CPU.
         */
        fun create(context: Context, modelFile: File): DepthEstimator {
            val cacheDir = File(context.cacheDir, "litert").apply { mkdirs() }
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
                                    modelCacheKey = "depth_anything_v2_small_518_${precision.name}",
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
                            accelerator,
                        )
                    }
                        .onFailure {
                            lastError = it
                            Log.w(TAG, "Cannot load the depth model on $accelerator/$precision", it)
                        }
                        .getOrNull() ?: continue
                if (accelerator == Accelerator.CPU || estimator.producesValidOutput()) {
                    Log.i(TAG, "Depth model running on $accelerator ${precision ?: ""}")
                    return estimator
                }
                Log.w(TAG, "Depth model output on $accelerator/$precision is invalid, retrying")
                estimator.close()
            }
            throw IllegalStateException("The depth model cannot run on this device", lastError)
        }

        private fun DepthEstimator.producesValidOutput(): Boolean {
            // A simple gradient with a bright square gives the model something to find.
            val test = ByteArray(size * size * 4)
            for (y in 0 until size) for (x in 0 until size) {
                val o = (y * size + x) * 4
                val inside = x in size / 3 until size * 2 / 3 && y in size / 3 until size * 2 / 3
                val v = if (inside) 230 else (y * 200 / size)
                test[o] = v.toByte()
                test[o + 1] = v.toByte()
                test[o + 2] = v.toByte()
                test[o + 3] = -1
            }
            val out = runCatching { estimate(test) }.getOrNull() ?: return false
            var min = Float.MAX_VALUE
            var max = -Float.MAX_VALUE
            for (v in out) {
                if (v.isNaN() || v.isInfinite()) return false
                if (v < min) min = v
                if (v > max) max = v
            }
            return max - min > 1e-4f
        }
    }
}
