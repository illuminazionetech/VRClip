@file:OptIn(UnstableApi::class)

package com.illuminazionetech.vrclip.player.stereo

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLES30
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.google.ai.edge.litert.Accelerator
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the two synthesized views share the output frame of a permanent conversion. */
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

        data class Running(val accelerator: Accelerator, val model: DepthModel) : Status

        data object Failed : Status
    }

    private val mutableState = MutableStateFlow<Status>(Status.Idle)
    val state: StateFlow<Status> = mutableState.asStateFlow()

    internal fun update(status: Status) {
        mutableState.value = status
    }
}

/**
 * Media3 effect that turns a flat video into 3D. Each frame is reduced to the depth model's input
 * on the GPU, depth is estimated with [DepthEstimator] and steadied by [DepthRefiner], then brought
 * back to the frame's resolution along the picture's own edges ([StereoShaders.UPSAMPLE]).
 *
 * - [Output.Conversion]: every frame gets its own depth map from the full model, and both views are
 *   drawn side by side for the permanent 3D file.
 * - [Output.LiveSideBySide] (Meta Quest): the views are drawn side by side for the stereo layer.
 * - [Output.LiveColorAndDepth] (phones and tablets): the frame and its depth map are passed on, one
 *   above the other, and the screen's renderer draws whatever views its output mode needs.
 *
 * Live, depth is estimated on its own thread from frames read back without stalling the GPU, and
 * the newest map serves the frames in between, so playback never waits; after each inference the
 * GPU gets a pause proportional to its length, so the video keeps the time it needs.
 */
class StereoConversionEffect(
    private val model: DepthModelManager,
    private val settings: StereoSettings,
    private val output: Output,
    private val packing: StereoPacking = StereoPacking.Full,
    /** Exact output size, when the caller already fitted it to the encoder. */
    private val targetSize: Pair<Int, Int>? = null,
) : GlEffect {

    enum class Output {
        Conversion,
        LiveSideBySide,
        LiveColorAndDepth;

        val isLive: Boolean
            get() = this != Conversion
    }

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        val depthModel = if (output.isLive) LiveModelPolicy.choose() else DepthModel.Full
        return StereoShaderProgram(
            context = context.applicationContext,
            useHdr = useHdr,
            manager = model,
            depthModel = depthModel,
            settings = settings,
            output = output,
            packing = packing,
            targetSize = targetSize,
        )
    }

    companion object {
        /** Live side by side for headsets: about the width of a Quest 3 display per eye. */
        const val HEADSET_MAX_EYE_WIDTH = 2048

        /** Live picture plus depth for phones and tablets. */
        const val SCREEN_MAX_EYE_WIDTH = 1920
        private const val MAX_TEXTURE_SIDE = 4096

        /** Output frame size for an input of [width] x [height]; both values are even. */
        fun outputSize(
            width: Int,
            height: Int,
            output: Output,
            packing: StereoPacking = StereoPacking.Full,
        ): Pair<Int, Int> =
            when (output) {
                Output.Conversion ->
                    if (packing == StereoPacking.Full) even(width * 2) to even(height)
                    else even(width) to even(height)
                Output.LiveSideBySide -> {
                    val scale = min(1f, HEADSET_MAX_EYE_WIDTH.toFloat() / width)
                    even((width * scale).roundToInt() * 2) to even((height * scale).roundToInt())
                }
                Output.LiveColorAndDepth -> {
                    val scale =
                        minOf(
                            1f,
                            SCREEN_MAX_EYE_WIDTH.toFloat() / width,
                            MAX_TEXTURE_SIDE / (2f * height),
                        )
                    even((width * scale).roundToInt()) to even((height * scale).roundToInt()) * 2
                }
            }

        private fun even(value: Int) = max(2, value - value % 2)
    }
}

private class StereoShaderProgram(
    private val context: Context,
    useHdr: Boolean,
    private val manager: DepthModelManager,
    private val depthModel: DepthModel,
    private val settings: StereoSettings,
    private val output: StereoConversionEffect.Output,
    private val packing: StereoPacking,
    private val targetSize: Pair<Int, Int>?,
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val live = output.isLive
    private val size = depthModel.inputSize

    private val downscale = program(StereoShaders.DOWNSCALE)
    private val upsample = program(StereoShaders.UPSAMPLE)
    private val compose =
        program(
            when (output) {
                StereoConversionEffect.Output.LiveColorAndDepth -> StereoShaders.COLOR_AND_DEPTH
                StereoConversionEffect.Output.LiveSideBySide ->
                    StereoShaders.sideBySide(LIVE_STEPS, LIVE_REFINE)
                StereoConversionEffect.Output.Conversion ->
                    StereoShaders.sideBySide(
                        CONVERSION_STEPS,
                        CONVERSION_REFINE,
                        texelsPerStep = 1f,
                    )
            }
        )

    private var guideTexture = 0
    private var guideFbo = 0
    private var lowDepthTexture = 0
    private var depthTexture = 0
    private var depthFbo = 0
    private var depthWidth = 0
    private var depthHeight = 0
    private var eyeAspect = 16f / 9f

    private val reader = FrameReader(size)
    private val depthUpload = ByteBuffer.allocateDirect(size * size).order(ByteOrder.nativeOrder())
    private val refiner =
        DepthRefiner(
            size,
            stillBlend = if (live) 0.3f else 0.4f,
            rangeBlend = if (live) 0.15f else 0.1f,
        )

    // Conversion: everything happens on the GL thread.
    private var estimator: DepthEstimator? = null

    // Live: inference on its own thread, results handed over through [pendingDepth].
    private val executor: ExecutorService? =
        if (live)
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "VRClip-depth").apply {
                    isDaemon = true
                    priority = Thread.NORM_PRIORITY - 1
                }
            }
        else null
    private val busy = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val resetRequested = AtomicBoolean(false)
    private val pendingDepth = AtomicReference<ByteArray?>(null)

    /** Bumped by every seek: depth of a frame from before it is dropped instead of shown. */
    private val generation = AtomicLong(0L)
    private var readGeneration = 0L
    private val nextInferenceAt = AtomicLong(0L)
    private val inferenceCount = AtomicLong(0L)
    private val inferenceMillisTotal = AtomicLong(0L)
    private var hasDepth = false
    private var ramp = 0f

    private val savedFramebuffer = IntArray(1)
    private val savedViewport = IntArray(4)

    init {
        if (live) LiveStereoStatus.update(LiveStereoStatus.Status.Loading)
    }

    private fun program(fragment: String) =
        GlProgram(StereoShaders.VERTEX, fragment).apply {
            setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
            )
        }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        val (width, height) =
            targetSize
                ?: StereoConversionEffect.outputSize(inputWidth, inputHeight, output, packing)
        val (eyeWidth, eyeHeight) =
            when (output) {
                StereoConversionEffect.Output.LiveColorAndDepth -> width to height / 2
                else -> width / 2 to height
            }
        eyeAspect = inputWidth.toFloat() / inputHeight
        try {
            if (guideTexture == 0) {
                guideTexture = GlUtil.createTexture(size, size, false)
                guideFbo = GlUtil.createFboForTexture(guideTexture)
                lowDepthTexture = createLowDepthTexture()
            }
            // The depth map is drawn at the resolution of one eye, at most the source's.
            val dw = min(eyeWidth, inputWidth).coerceAtLeast(2)
            val dh = min(eyeHeight, inputHeight).coerceAtLeast(2)
            if (dw != depthWidth || dh != depthHeight) {
                if (depthFbo != 0) GlUtil.deleteFbo(depthFbo)
                if (depthTexture != 0) GlUtil.deleteTexture(depthTexture)
                depthTexture = GlUtil.createTexture(dw, dh, false)
                depthFbo = GlUtil.createFboForTexture(depthTexture)
                depthWidth = dw
                depthHeight = dh
            }
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
        return Size(width, height)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, savedFramebuffer, 0)
            GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, savedViewport, 0)

            // 1. The frame at the model's size: input for the model and color guide below.
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, guideFbo)
            GLES20.glViewport(0, 0, size, size)
            downscale.use()
            downscale.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            downscale.setFloatsUniform("uStep", floatArrayOf(1f / (3 * size), 1f / (3 * size)))
            downscale.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            if (live) updateDepthLive() else updateDepthNow()

            // 2. Depth at the eye's resolution, along the edges of this very frame.
            if (live && hasDepth && ramp < 1f) ramp = (ramp + RAMP_STEP).coerceAtMost(1f)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, depthFbo)
            GLES20.glViewport(0, 0, depthWidth, depthHeight)
            upsample.use()
            upsample.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            upsample.setSamplerTexIdUniform("uGuide", guideTexture, 1)
            upsample.setSamplerTexIdUniform("uLowDepth", lowDepthTexture, 2)
            upsample.setFloatsUniform("uLowSize", floatArrayOf(size.toFloat(), size.toFloat()))
            upsample.setFloatUniform("uRamp", if (!hasDepth) 0f else if (live) ramp else 1f)
            upsample.setFloatUniform("uConvergence", settings.convergence)
            upsample.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            // 3. The output frame.
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, savedFramebuffer[0])
            GLES20.glViewport(
                savedViewport[0],
                savedViewport[1],
                savedViewport[2],
                savedViewport[3],
            )
            compose.use()
            compose.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            compose.setSamplerTexIdUniform("uDepth", depthTexture, 1)
            if (output != StereoConversionEffect.Output.LiveColorAndDepth) {
                compose.setFloatUniform("uHalfRange", settings.strength / 2f)
                compose.setFloatUniform("uConvergence", settings.convergence)
                compose.setFloatUniform("uAspect", eyeAspect)
                compose.setFloatsUniform(
                    "uTexel",
                    floatArrayOf(1f / depthWidth, 1f / depthHeight),
                )
            }
            compose.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    /** Conversion: this frame's own depth, before it is drawn. */
    private fun updateDepthNow() {
        val rgba = reader.readNow()
        val model =
            estimator
                ?: runCatching {
                    DepthEstimator.create(context, depthModel, manager.fileOf(depthModel))
                }
                    .getOrElse { throw VideoFrameProcessingException(it) }
                    .also { estimator = it }
        val depth = ByteArray(size * size)
        refiner.process(model.estimate(rgba, bottomUp = true), depth)
        uploadDepth(depth)
        hasDepth = true
    }

    /** Live: starts a read of this frame when the model is free, hands finished depth over. */
    private fun updateDepthLive() {
        val worker = executor ?: return
        if (
            !failed.get() &&
                !busy.get() &&
                !reader.pending &&
                SystemClock.elapsedRealtime() >= nextInferenceAt.get()
        ) {
            readGeneration = generation.get()
            reader.start()
        }
        reader.poll()?.let { rgba ->
            val frameGeneration = readGeneration
            if (frameGeneration != generation.get()) return@let
            busy.set(true)
            worker.execute { infer(rgba, frameGeneration) }
        }
        pendingDepth.getAndSet(null)?.let {
            uploadDepth(it)
            hasDepth = true
        }
    }

    private fun infer(rgba: ByteArray, frameGeneration: Long) {
        try {
            val model =
                estimator
                    ?: DepthEstimator.create(context, depthModel, manager.fileOf(depthModel)).also {
                        estimator = it
                        // Turned off while the model was loading: the player already shows Idle.
                        if (!released.get()) {
                            LiveStereoStatus.update(
                                LiveStereoStatus.Status.Running(it.accelerator, depthModel)
                            )
                        }
                    }
            if (resetRequested.getAndSet(false)) refiner.reset()
            val start = SystemClock.elapsedRealtime()
            val raw = model.estimate(rgba, bottomUp = true)
            val took = SystemClock.elapsedRealtime() - start
            if (frameGeneration == generation.get()) {
                val depth = ByteArray(size * size)
                refiner.process(raw, depth)
                pendingDepth.set(depth)
            }
            inferenceCount.incrementAndGet()
            inferenceMillisTotal.addAndGet(took)
            nextInferenceAt.set(SystemClock.elapsedRealtime() + (took * GPU_REST).toLong())
        } catch (t: Throwable) {
            Log.e(TAG, "Live depth estimation failed", t)
            failed.set(true)
            if (!released.get()) LiveStereoStatus.update(LiveStereoStatus.Status.Failed)
        } finally {
            busy.set(false)
        }
    }

    private fun createLowDepthTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        depthUpload.clear()
        repeat(size * size) { depthUpload.put(0) }
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

    /** Rows arrive top first, so the map is upside down in GL terms; the shader flips it. */
    private fun uploadDepth(depth: ByteArray) {
        depthUpload.clear()
        depthUpload.put(depth)
        depthUpload.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lowDepthTexture)
        // Rows of 364 or 518 bytes are not 4-byte aligned.
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
        // A seek: the next frame has nothing to do with the last one, so the depth fades in again.
        if (live) {
            generation.incrementAndGet()
            resetRequested.set(true)
            pendingDepth.set(null)
            hasDepth = false
            ramp = 0f
        } else {
            refiner.reset()
        }
        super.flush()
    }

    override fun release() {
        released.set(true)
        super.release()
        try {
            reader.release()
            downscale.delete()
            upsample.delete()
            compose.delete()
            if (guideFbo != 0) GlUtil.deleteFbo(guideFbo)
            if (guideTexture != 0) GlUtil.deleteTexture(guideTexture)
            if (lowDepthTexture != 0) GlUtil.deleteTexture(lowDepthTexture)
            if (depthFbo != 0) GlUtil.deleteFbo(depthFbo)
            if (depthTexture != 0) GlUtil.deleteTexture(depthTexture)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        } finally {
            val worker = executor
            if (worker != null) {
                worker.execute {
                    estimator?.close()
                    estimator = null
                    val count = inferenceCount.get()
                    if (count >= POLICY_MIN_INFERENCES) {
                        LiveModelPolicy.record(
                            depthModel,
                            inferenceMillisTotal.get().toFloat() / count,
                        )
                    }
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
        private const val LIVE_STEPS = 20
        private const val LIVE_REFINE = 3
        private const val CONVERSION_STEPS = 48
        private const val CONVERSION_REFINE = 5
        private const val RAMP_STEP = 1f / 24f

        /** After an inference of t ms the GPU is left to the video for GPU_REST * t ms. */
        private const val GPU_REST = 0.5f
        private const val POLICY_MIN_INFERENCES = 30L
    }
}

/**
 * Reads the bound framebuffer (the frame at the model's size) back to memory. With OpenGL ES 3 the
 * read goes into a pixel buffer and is collected a frame or two later, once a fence says the GPU is
 * done, so the video pipeline never waits for it; on ES 2 it reads synchronously.
 */
private class FrameReader(private val size: Int) {
    private val byteCount = size * size * 4
    private val direct = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder())
    private var asyncSupported: Boolean? = null
    private var pbo = 0
    private var fence = 0L

    var pending = false
        private set

    /** Synchronous read of the bound framebuffer. */
    fun readNow(): ByteArray {
        direct.clear()
        GLES20.glReadPixels(0, 0, size, size, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, direct)
        return ByteArray(byteCount).also {
            direct.position(0)
            direct.get(it)
        }
    }

    /** Starts reading the bound framebuffer; [poll] returns the pixels when they are ready. */
    fun start() {
        if (!isAsyncSupported()) {
            syncResult = readNow()
            pending = true
            return
        }
        if (pbo == 0) {
            val ids = IntArray(1)
            GLES30.glGenBuffers(1, ids, 0)
            pbo = ids[0]
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pbo)
            GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, byteCount, null, GLES30.GL_STREAM_READ)
        } else {
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pbo)
        }
        GLES30.glReadPixels(0, 0, size, size, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, 0)
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)
        fence = GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
        GLES20.glFlush()
        pending = true
    }

    private var syncResult: ByteArray? = null

    fun poll(): ByteArray? {
        if (!pending) return null
        syncResult?.let {
            syncResult = null
            pending = false
            return it
        }
        val status = GLES30.glClientWaitSync(fence, 0, 0)
        if (status != GLES30.GL_ALREADY_SIGNALED && status != GLES30.GL_CONDITION_SATISFIED) {
            return null
        }
        GLES30.glDeleteSync(fence)
        fence = 0L
        pending = false
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pbo)
        val mapped =
            GLES30.glMapBufferRange(
                GLES30.GL_PIXEL_PACK_BUFFER,
                0,
                byteCount,
                GLES30.GL_MAP_READ_BIT,
            ) as? ByteBuffer
        val bytes = mapped?.let { buffer ->
            ByteArray(byteCount).also {
                buffer.order(ByteOrder.nativeOrder()).position(0)
                buffer.get(it)
            }
        }
        GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER)
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)
        if (bytes == null) asyncSupported = false
        return bytes
    }

    private fun isAsyncSupported(): Boolean =
        asyncSupported
            ?: (GLES20.glGetString(GLES20.GL_VERSION)?.startsWith("OpenGL ES 3") == true).also {
                asyncSupported = it
            }

    fun release() {
        if (fence != 0L) GLES30.glDeleteSync(fence)
        if (pbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(pbo), 0)
        fence = 0L
        pbo = 0
        pending = false
    }
}
