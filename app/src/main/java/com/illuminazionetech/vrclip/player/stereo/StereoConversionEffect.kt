@file:OptIn(UnstableApi::class)

package com.illuminazionetech.vrclip.player.stereo

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.content.Context
import android.opengl.GLES20
import android.util.Log
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.google.ai.edge.litert.Accelerator
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the two synthesized views share the output frame. */
enum class StereoPacking {
    /** Each eye keeps the full source resolution: the output is twice as wide. */
    Full,

    /** Each eye is squeezed into half of a frame the size of the source. */
    Half,
}

/** What the live conversion is doing, for the player UI. */
object LiveStereoStatus {
    sealed interface Status {
        data object Idle : Status

        data object Loading : Status

        data class Running(val accelerator: Accelerator) : Status

        data object Failed : Status
    }

    private val mutableState = MutableStateFlow<Status>(Status.Idle)
    val state: StateFlow<Status> = mutableState.asStateFlow()

    internal fun update(status: Status) {
        mutableState.value = status
    }
}

/**
 * Media3 effect that turns a flat video into side-by-side 3D. Each frame is scaled down to the
 * depth model's input on the GPU, depth is estimated with [DepthEstimator], and a fragment shader
 * synthesizes the left and right views from the frame and its depth map. The same effect serves
 * the player (with [realtime] set: inference runs on its own thread and the newest depth map is
 * reused until the next one is ready, so playback never waits) and the permanent conversion
 * (every frame gets its own depth map, with temporal smoothing, at a higher quality search).
 */
class StereoConversionEffect(
    private val model: DepthModelManager,
    private val settings: StereoSettings,
    private val realtime: Boolean,
    private val packing: StereoPacking = StereoPacking.Full,
    private val maxOutputWidth: Int = if (realtime) REALTIME_MAX_EYE_WIDTH * 2 else Int.MAX_VALUE,
    /** Exact output size, when the caller already fitted it to the encoder. */
    private val targetSize: Pair<Int, Int>? = null,
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        StereoShaderProgram(
            context = context.applicationContext,
            useHdr = useHdr,
            modelFile = model.modelFile,
            settings = settings,
            realtime = realtime,
            packing = packing,
            maxOutputWidth = maxOutputWidth,
            targetSize = targetSize,
        )

    companion object {
        const val REALTIME_MAX_EYE_WIDTH = 1920

        /** Output frame size for an input of [width] x [height]; both values are even. */
        fun outputSize(
            width: Int,
            height: Int,
            realtime: Boolean,
            packing: StereoPacking = StereoPacking.Full,
            maxOutputWidth: Int = if (realtime) REALTIME_MAX_EYE_WIDTH * 2 else Int.MAX_VALUE,
        ): Pair<Int, Int> {
            val fullWidth = if (packing == StereoPacking.Full) width * 2 else width
            val scale = if (fullWidth > maxOutputWidth) maxOutputWidth.toFloat() / fullWidth else 1f
            val outWidth = even((fullWidth * scale).roundToInt())
            val outHeight = even((height * scale).roundToInt())
            return outWidth to outHeight
        }

        private fun even(value: Int) = max(2, value - value % 2)
    }
}

private class StereoShaderProgram(
    private val context: Context,
    useHdr: Boolean,
    private val modelFile: File,
    private val settings: StereoSettings,
    private val realtime: Boolean,
    private val packing: StereoPacking,
    private val maxOutputWidth: Int,
    private val targetSize: Pair<Int, Int>?,
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val size = DepthModelManager.INPUT_SIZE
    private val synthesis =
        GlProgram(VERTEX_SHADER, synthesisShader(if (realtime) REALTIME_STEPS else OFFLINE_STEPS))
    private val downscale = GlProgram(VERTEX_SHADER, DOWNSCALE_SHADER)

    private var downscaleTexture = 0
    private var downscaleFbo = 0
    private var depthTexture = 0

    private val readBuffer = ByteBuffer.allocateDirect(size * size * 4).order(ByteOrder.nativeOrder())
    private val depthUpload = ByteBuffer.allocateDirect(size * size).order(ByteOrder.nativeOrder())
    private val postProcessor =
        DepthPostProcessor(
            size,
            mapBlend = if (realtime) 0.65f else 0.7f,
            rangeBlend = if (realtime) 0.3f else 0.2f,
        )

    // Offline: everything happens on the GL thread.
    private var estimator: DepthEstimator? = null

    // Realtime: inference on its own thread, results handed over through [pendingDepth].
    private val executor: ExecutorService? =
        if (realtime)
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "VRClip-depth").apply { isDaemon = true }
            }
        else null
    private val busy = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    private val resetRequested = AtomicBoolean(false)
    private val pendingDepth = AtomicReference<ByteArray?>(null)
    private var hasDepth = false
    private var ramp = 0f

    private val savedFramebuffer = IntArray(1)
    private val savedViewport = IntArray(4)

    init {
        if (realtime) LiveStereoStatus.update(LiveStereoStatus.Status.Loading)
        val bounds = GlUtil.getNormalizedCoordinateBounds()
        synthesis.setBufferAttribute(
            "aFramePosition",
            bounds,
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
        downscale.setBufferAttribute(
            "aFramePosition",
            bounds,
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        if (downscaleTexture == 0) {
            try {
                downscaleTexture = GlUtil.createTexture(size, size, /* useHighPrecisionColorComponents= */ false)
                downscaleFbo = GlUtil.createFboForTexture(downscaleTexture)
                depthTexture = createDepthTexture()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
        }
        val (width, height) =
            targetSize
                ?: StereoConversionEffect.outputSize(
                inputWidth,
                inputHeight,
                realtime,
                packing,
                maxOutputWidth,
            )
        return Size(width, height)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, savedFramebuffer, 0)
            GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, savedViewport, 0)

            if (realtime) updateDepthRealtime(inputTexId) else updateDepthOffline(inputTexId)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, savedFramebuffer[0])
            GLES20.glViewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3])

            if (realtime && hasDepth && ramp < 1f) ramp = (ramp + RAMP_STEP).coerceAtMost(1f)
            val strength =
                when {
                    !hasDepth -> 0f
                    realtime -> settings.strength * ramp
                    else -> settings.strength
                }
            synthesis.use()
            synthesis.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            synthesis.setSamplerTexIdUniform("uDepth", depthTexture, 1)
            synthesis.setFloatUniform("uStrength", strength)
            synthesis.setFloatUniform("uConvergence", settings.convergence)
            synthesis.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    /** Renders the frame at the model's input size, upside down so rows read back top first. */
    private fun readDownscaledFrame(inputTexId: Int): ByteArray {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, downscaleFbo)
        GLES20.glViewport(0, 0, size, size)
        downscale.use()
        downscale.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        downscale.setFloatsUniform("uTapOffset", floatArrayOf(0.25f / size, 0.25f / size))
        downscale.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        readBuffer.clear()
        GLES20.glReadPixels(0, 0, size, size, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, readBuffer)
        val bytes = ByteArray(size * size * 4)
        readBuffer.position(0)
        readBuffer.get(bytes)
        return bytes
    }

    private fun updateDepthOffline(inputTexId: Int) {
        val rgba = readDownscaledFrame(inputTexId)
        val model =
            estimator
                ?: runCatching { DepthEstimator.create(context, modelFile) }
                    .getOrElse { throw VideoFrameProcessingException(it) }
                    .also { estimator = it }
        val depth = ByteArray(size * size)
        postProcessor.process(model.estimate(rgba), depth)
        uploadDepth(depth)
        hasDepth = true
    }

    private fun updateDepthRealtime(inputTexId: Int) {
        val worker = executor ?: return
        if (!failed.get() && busy.compareAndSet(false, true)) {
            val rgba = readDownscaledFrame(inputTexId)
            worker.execute {
                try {
                    val model =
                        estimator
                            ?: DepthEstimator.create(context, modelFile).also {
                                estimator = it
                                LiveStereoStatus.update(LiveStereoStatus.Status.Running(it.accelerator))
                            }
                    if (resetRequested.getAndSet(false)) postProcessor.reset()
                    val depth = ByteArray(size * size)
                    postProcessor.process(model.estimate(rgba), depth)
                    pendingDepth.set(depth)
                } catch (t: Throwable) {
                    Log.e(TAG, "Live depth estimation failed", t)
                    failed.set(true)
                    LiveStereoStatus.update(LiveStereoStatus.Status.Failed)
                } finally {
                    busy.set(false)
                }
            }
        }
        pendingDepth.getAndSet(null)?.let {
            uploadDepth(it)
            hasDepth = true
        }
    }

    private fun createDepthTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        // Mid gray until the first depth map arrives: every pixel on the screen plane.
        depthUpload.clear()
        repeat(size * size) { depthUpload.put(128.toByte()) }
        depthUpload.position(0)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_LUMINANCE,
            size,
            size,
            0,
            GLES20.GL_LUMINANCE,
            GLES20.GL_UNSIGNED_BYTE,
            depthUpload,
        )
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4)
        return ids[0]
    }

    /** Rows arrive top first, so the map ends up upside down in GL terms; the shader flips it. */
    private fun uploadDepth(depth: ByteArray) {
        depthUpload.clear()
        depthUpload.put(depth)
        depthUpload.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, depthTexture)
        // Rows of 518 bytes are not 4-byte aligned.
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexSubImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            0,
            0,
            size,
            size,
            GLES20.GL_LUMINANCE,
            GLES20.GL_UNSIGNED_BYTE,
            depthUpload,
        )
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4)
    }

    override fun flush() {
        // A seek: the next frame has nothing to do with the last one.
        if (realtime) resetRequested.set(true) else postProcessor.reset()
        super.flush()
    }

    override fun release() {
        super.release()
        try {
            synthesis.delete()
            downscale.delete()
            if (downscaleFbo != 0) GlUtil.deleteFbo(downscaleFbo)
            if (downscaleTexture != 0) GlUtil.deleteTexture(downscaleTexture)
            if (depthTexture != 0) GlUtil.deleteTexture(depthTexture)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        } finally {
            val worker = executor
            if (worker != null) {
                worker.execute {
                    estimator?.close()
                    estimator = null
                }
                worker.shutdown()
                LiveStereoStatus.update(LiveStereoStatus.Status.Idle)
            } else {
                estimator?.close()
                estimator = null
            }
        }
    }

    companion object {
        private const val TAG = "StereoConversion"
        private const val REALTIME_STEPS = 24
        private const val OFFLINE_STEPS = 48
        private const val RAMP_STEP = 1f / 24f

        private const val VERTEX_SHADER =
            """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
            }
            """

        /** Four bilinear taps per output pixel, so the 3-4x reduction does not alias. */
        private const val DOWNSCALE_SHADER =
            """
            precision highp float;
            uniform sampler2D uTexSampler;
            uniform vec2 uTapOffset;
            varying vec2 vTexSamplingCoord;
            void main() {
                vec2 uv = vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y);
                vec4 sum = texture2D(uTexSampler, uv + vec2(-uTapOffset.x, -uTapOffset.y))
                    + texture2D(uTexSampler, uv + vec2(uTapOffset.x, -uTapOffset.y))
                    + texture2D(uTexSampler, uv + vec2(-uTapOffset.x, uTapOffset.y))
                    + texture2D(uTexSampler, uv + vec2(uTapOffset.x, uTapOffset.y));
                gl_FragColor = vec4((sum * 0.25).rgb, 1.0);
            }
            """

        /**
         * Depth-image-based rendering by backward search. For an output pixel of one eye, every
         * source position within the parallax range is a candidate; a source point at depth d
         * lands at x + shift(d). Walking from the side of the nearest possible point, the first
         * crossing is the visible surface (a nearer point occludes a farther one), refined
         * linearly between the two samples that bracket it. Where nothing lands (a disocclusion)
         * the walk settles on the background next to the edge, which fills the gap with it.
         */
        private fun synthesisShader(steps: Int) =
            """
            precision highp float;
            uniform sampler2D uTexSampler;
            uniform sampler2D uDepth;
            uniform float uStrength;
            uniform float uConvergence;
            varying vec2 vTexSamplingCoord;

            float depthAt(float x, float y) {
                return texture2D(uDepth, vec2(clamp(x, 0.0, 1.0), 1.0 - y)).r;
            }

            void main() {
                vec2 uv = vTexSamplingCoord;
                float eye = uv.x < 0.5 ? -1.0 : 1.0;
                float u = uv.x < 0.5 ? uv.x * 2.0 : uv.x * 2.0 - 1.0;
                float y = uv.y;
                float halfRange = 0.5 * uStrength;
                if (halfRange <= 0.0) {
                    gl_FragColor = texture2D(uTexSampler, vec2(u, y));
                    return;
                }
                float c = uConvergence;
                float start = u + eye * halfRange * (1.0 - c);
                float end = u - eye * halfRange * c;
                float prevX = start;
                float prevG = prevX - eye * halfRange * (depthAt(prevX, y) - c) - u;
                float hit = end;
                for (int i = 1; i <= $steps; i++) {
                    float x = mix(start, end, float(i) / float($steps));
                    float g = x - eye * halfRange * (depthAt(x, y) - c) - u;
                    if (g == 0.0) {
                        hit = x;
                        break;
                    }
                    if (sign(g) != sign(prevG)) {
                        hit = mix(prevX, x, clamp(prevG / (prevG - g), 0.0, 1.0));
                        break;
                    }
                    prevX = x;
                    prevG = g;
                }
                gl_FragColor = texture2D(uTexSampler, vec2(clamp(hit, 0.0, 1.0), y));
            }
            """
    }
}
