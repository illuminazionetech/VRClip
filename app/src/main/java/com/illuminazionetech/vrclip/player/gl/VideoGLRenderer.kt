package com.illuminazionetech.vrclip.player.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import com.illuminazionetech.vrclip.player.ProjectionMode
import com.illuminazionetech.vrclip.player.StereoLayout
import com.illuminazionetech.vrclip.player.stereo.StereoShaders
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** How stereo (and spherical) content is presented on a phone or tablet screen. */
enum class StereoOutputMode {
    /** Full screen, one eye only: normal viewing. */
    SingleEye,

    /** Left and right halves of the screen, one per eye, for a Cardboard-style viewer. */
    SplitScreen,

    /** Both eyes mixed into red/cyan (Dubois), for anaglyph glasses. */
    Anaglyph,

    /**
     * One full-screen view whose viewpoint follows small tilts of the phone, so near objects move
     * against the background: depth without a viewer or glasses. Needs a depth map (live 2D to 3D).
     */
    Parallax,
}

/** Everything the renderer needs to know about what to draw; replaced as a whole. */
internal data class RenderConfig(
    val mode: ProjectionMode = ProjectionMode.FLAT,
    val output: StereoOutputMode = StereoOutputMode.SingleEye,
    /** Width / height of the decoded frame (one eye), pixel aspect ratio included. */
    val frameAspect: Float = 16f / 9f,
    /** The frame holds the picture in its top half and the picture's depth in the bottom half. */
    val depthPacked: Boolean = false,
    /** Half the parallax range as a fraction of the width, and the depth on the screen plane. */
    val halfRange: Float = 0.015f,
    val convergence: Float = 0.7f,
    /** Fill the screen, cropping the picture, instead of fitting it (flat pictures only). */
    val fill: Boolean = false,
)

/**
 * Renders decoded video frames (delivered through a [SurfaceTexture]) for stereo 3D and 360/180
 * content: a letterboxed quad per eye for flat 3D, an inward-facing sphere or half-dome for
 * spherical video, with split-screen and anaglyph output. The camera combines the device
 * orientation (from [OrientationTracker]) with touch/pinch input. Frames of live 2D to 3D carry
 * their depth map ([RenderConfig.depthPacked]); for them the views are synthesized here at the
 * screen's resolution, every time the screen is drawn, which is what lets the parallax view follow
 * the phone's tilt ([ParallaxTracker]) between video frames. Plain 2D video does not come here; it
 * plays through a SurfaceView, which is cheaper and keeps HDR.
 */
internal class VideoGLRenderer(
    private val orientation: OrientationTracker,
    private val parallax: ParallaxTracker,
    private val onSurfaceCreated: (SurfaceTexture, Surface) -> Unit,
    private val onFrameAvailable: () -> Unit,
) : GLSurfaceView.Renderer {

    @Volatile var config: RenderConfig = RenderConfig()

    /** Touch offsets in degrees, applied on top of the device orientation. */
    @Volatile var touchYaw = 0f
    @Volatile var touchPitch = 0f
    @Volatile var fieldOfView = DEFAULT_FOV

    private var program = 0
    private var oesTexture = 0
    private var surfaceTexture: SurfaceTexture? = null
    private val texMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    private var aPosition = 0
    private var aUv = 0
    private var uMvp = 0
    private var uTexMatrix = 0
    private var uEyeA = 0
    private var uEyeB = 0
    private var uAnaglyph = 0
    private var uTexture = 0

    private var synthesis = 0
    private var sPosition = 0
    private var sUv = 0
    private var sMvp = 0
    private var sTexMatrix = 0
    private var sTexture = 0
    private var sFrameTexel = 0
    private var sOffsetA = 0
    private var sOffsetB = 0
    private var sAnaglyph = 0
    private var sZoom = 0
    private var sHalfRange = 0
    private var sConvergence = 0
    private var sAspect = 0
    private var sTexel = 0
    private val parallaxOffset = FloatArray(2)

    /** Size of the frames arriving through the SurfaceTexture, set by the view. */
    @Volatile var frameSize: Pair<Int, Int> = 1920 to 2160

    private var sphere360: SphereMesh? = null
    private var sphere180: SphereMesh? = null
    private var quad: FloatBuffer? = null

    private var width = 1
    private var height = 1

    private val projection = FloatArray(16)
    private val device = FloatArray(16)
    private val yawMatrix = FloatArray(16)
    private val pitchMatrix = FloatArray(16)
    private val temp = FloatArray(16)
    private val view = FloatArray(16)
    private val mvp = FloatArray(16)

    @Volatile private var frameAvailable = false

    override fun onSurfaceCreated(gl: GL10?, eglConfig: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        program = GlUtil.linkProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aUv = GLES20.glGetAttribLocation(program, "aUv")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uEyeA = GLES20.glGetUniformLocation(program, "uEyeA")
        uEyeB = GLES20.glGetUniformLocation(program, "uEyeB")
        uAnaglyph = GLES20.glGetUniformLocation(program, "uAnaglyph")
        uTexture = GLES20.glGetUniformLocation(program, "uTexture")

        synthesis = GlUtil.linkProgram(VERTEX_SHADER, SYNTHESIS_SHADER)
        sPosition = GLES20.glGetAttribLocation(synthesis, "aPosition")
        sUv = GLES20.glGetAttribLocation(synthesis, "aUv")
        sMvp = GLES20.glGetUniformLocation(synthesis, "uMvp")
        sTexMatrix = GLES20.glGetUniformLocation(synthesis, "uTexMatrix")
        sTexture = GLES20.glGetUniformLocation(synthesis, "uTexture")
        sFrameTexel = GLES20.glGetUniformLocation(synthesis, "uFrameTexel")
        sOffsetA = GLES20.glGetUniformLocation(synthesis, "uOffsetA")
        sOffsetB = GLES20.glGetUniformLocation(synthesis, "uOffsetB")
        sAnaglyph = GLES20.glGetUniformLocation(synthesis, "uAnaglyph")
        sZoom = GLES20.glGetUniformLocation(synthesis, "uZoom")
        sHalfRange = GLES20.glGetUniformLocation(synthesis, "uHalfRange")
        sConvergence = GLES20.glGetUniformLocation(synthesis, "uConvergence")
        sAspect = GLES20.glGetUniformLocation(synthesis, "uAspect")
        sTexel = GLES20.glGetUniformLocation(synthesis, "uTexel")

        oesTexture = GlUtil.createOesTexture()
        val texture =
            SurfaceTexture(oesTexture).apply {
                setOnFrameAvailableListener {
                    frameAvailable = true
                    onFrameAvailable()
                }
            }
        surfaceTexture = texture
        quad = buildQuad()
        onSurfaceCreated(texture, Surface(texture))
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = max(width, 1)
        this.height = max(height, 1)
    }

    override fun onDrawFrame(gl: GL10?) {
        val texture = surfaceTexture ?: return
        if (frameAvailable) {
            frameAvailable = false
            texture.updateTexImage()
            texture.getTransformMatrix(texMatrix)
        }

        GLES20.glViewport(0, 0, width, height)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)

        val current = config
        if (current.depthPacked) {
            drawWithDepth(current)
            return
        }
        GLES20.glUseProgram(program)
        GLES20.glUniform1i(uTexture, 0)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)

        val layout = current.mode.stereoLayout
        val output =
            if (layout == StereoLayout.None && current.output == StereoOutputMode.Anaglyph)
                StereoOutputMode.SingleEye
            else current.output

        when (output) {
            StereoOutputMode.SplitScreen -> {
                val half = width / 2
                drawEye(current, Eye.Left, 0, half, output)
                drawEye(current, Eye.Right, half, width - half, output)
            }
            else -> drawEye(current, Eye.Left, 0, width, output)
        }
    }

    private enum class Eye {
        Left,
        Right,
    }

    /** Live 2D to 3D: every view is synthesized from the picture and its depth map. */
    private fun drawWithDepth(config: RenderConfig) {
        GLES20.glUseProgram(synthesis)
        GLES20.glUniform1i(sTexture, 0)
        GLES20.glUniformMatrix4fv(sTexMatrix, 1, false, texMatrix, 0)
        val (frameWidth, frameHeight) = frameSize
        GLES20.glUniform2f(sFrameTexel, 1f / frameWidth, 1f / frameHeight)
        GLES20.glUniform1f(sHalfRange, config.halfRange)
        GLES20.glUniform1f(sConvergence, config.convergence)
        GLES20.glUniform1f(sAspect, config.frameAspect)
        GLES20.glUniform2f(sTexel, 1f / frameWidth, 2f / frameHeight)
        GLES20.glUniform1i(sAnaglyph, 0)
        GLES20.glUniform1f(sZoom, 1f)
        GLES20.glUniform2f(sOffsetB, 0f, 0f)

        fun view(x: Int, viewportWidth: Int, offsetX: Float, offsetY: Float) {
            GLES20.glViewport(x, 0, viewportWidth, height)
            GLES20.glUniform2f(sOffsetA, offsetX, offsetY)
            drawQuad(
                config.frameAspect,
                viewportWidth.toFloat() / height,
                config.fill,
                sPosition,
                sUv,
                sMvp,
            )
        }

        when (config.output) {
            StereoOutputMode.SplitScreen -> {
                val half = width / 2
                view(0, half, -1f, 0f)
                view(half, width - half, 1f, 0f)
            }
            StereoOutputMode.Anaglyph -> {
                GLES20.glUniform1i(sAnaglyph, 1)
                GLES20.glUniform2f(sOffsetB, 1f, 0f)
                view(0, width, -1f, 0f)
            }
            StereoOutputMode.Parallax -> {
                parallax.read(parallaxOffset)
                // Zoom in just enough that the shifted picture never shows its edges.
                GLES20.glUniform1f(sZoom, 1f - 2f * config.halfRange * PARALLAX_MAX_OFFSET)
                view(0, width, parallaxOffset[0], parallaxOffset[1])
            }
            StereoOutputMode.SingleEye -> view(0, width, 0f, 0f)
        }
    }

    private fun drawEye(
        config: RenderConfig,
        eye: Eye,
        x: Int,
        viewportWidth: Int,
        output: StereoOutputMode,
    ) {
        GLES20.glViewport(x, 0, viewportWidth, height)
        val layout = config.mode.stereoLayout
        setEye(uEyeA, layout, eye)
        setEye(uEyeB, layout, Eye.Right)
        GLES20.glUniform1i(uAnaglyph, if (output == StereoOutputMode.Anaglyph) 1 else 0)

        val viewportAspect = viewportWidth.toFloat() / height
        if (config.mode.isSpherical) {
            drawSphere(config.mode, viewportAspect)
        } else {
            val eyeAspect = ProjectionMode.eyeAspectRatio(config.mode, config.frameAspect)
            drawQuad(eyeAspect, viewportAspect, fill = false, aPosition, aUv, uMvp)
        }
    }

    /** Crop of the frame that holds one eye, as (scale.xy, offset.xy) in image space. */
    private fun setEye(location: Int, layout: StereoLayout, eye: Eye) {
        val right = eye == Eye.Right
        when (layout) {
            StereoLayout.None -> GLES20.glUniform4f(location, 1f, 1f, 0f, 0f)
            StereoLayout.LeftRight ->
                GLES20.glUniform4f(location, 0.5f, 1f, if (right) 0.5f else 0f, 0f)
            // The left eye is the top half; image-space v grows upward.
            StereoLayout.TopBottom ->
                GLES20.glUniform4f(location, 1f, 0.5f, 0f, if (right) 0f else 0.5f)
        }
    }

    private fun drawSphere(mode: ProjectionMode, viewportAspect: Float) {
        val mesh =
            if (mode.is180) sphere180 ?: SphereMesh(sweepDegrees = 180f).also { sphere180 = it }
            else sphere360 ?: SphereMesh().also { sphere360 = it }

        val roll = orientation.read(device)
        Matrix.setRotateM(yawMatrix, 0, touchYaw, 0f, 1f, 0f)
        Matrix.setRotateM(pitchMatrix, 0, -touchPitch, cos(roll), sin(roll), 0f)
        Matrix.multiplyMM(temp, 0, device, 0, yawMatrix, 0)
        Matrix.multiplyMM(view, 0, pitchMatrix, 0, temp, 0)
        Matrix.perspectiveM(projection, 0, fieldOfView, viewportAspect, 0.1f, 100f)
        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)

        val stride = SphereMesh.STRIDE_FLOATS * 4
        mesh.vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(
            aPosition,
            3,
            GLES20.GL_FLOAT,
            false,
            stride,
            mesh.vertexBuffer,
        )
        GLES20.glEnableVertexAttribArray(aPosition)
        mesh.vertexBuffer.position(3)
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, stride, mesh.vertexBuffer)
        GLES20.glEnableVertexAttribArray(aUv)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            mesh.indexCount,
            GLES20.GL_UNSIGNED_SHORT,
            mesh.indexBuffer,
        )
    }

    /**
     * Draws the eye picture letterboxed inside the current viewport, or scaled to cover it with
     * [fill], using the given program's attribute and matrix locations.
     */
    private fun drawQuad(
        eyeAspect: Float,
        viewportAspect: Float,
        fill: Boolean,
        position: Int,
        uv: Int,
        matrix: Int,
    ) {
        val buffer = quad ?: return
        Matrix.setIdentityM(mvp, 0)
        if (eyeAspect > 0f) {
            val wider = eyeAspect > viewportAspect
            if (wider != fill) Matrix.scaleM(mvp, 0, 1f, viewportAspect / eyeAspect, 1f)
            else Matrix.scaleM(mvp, 0, eyeAspect / viewportAspect, 1f, 1f)
        }
        GLES20.glUniformMatrix4fv(matrix, 1, false, mvp, 0)
        buffer.position(0)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 5 * 4, buffer)
        GLES20.glEnableVertexAttribArray(position)
        buffer.position(3)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 5 * 4, buffer)
        GLES20.glEnableVertexAttribArray(uv)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** Frees GL objects; must run on the GL thread while the context is still current. */
    fun releaseGl() {
        if (program != 0) GLES20.glDeleteProgram(program)
        if (synthesis != 0) GLES20.glDeleteProgram(synthesis)
        if (oesTexture != 0) GLES20.glDeleteTextures(1, intArrayOf(oesTexture), 0)
        program = 0
        synthesis = 0
        oesTexture = 0
    }

    companion object {
        const val DEFAULT_FOV = 75f
        const val MIN_FOV = 30f
        const val MAX_FOV = 110f
        private const val PARALLAX_MAX_OFFSET = 1.25f

        private const val VERTEX_SHADER =
            """
            uniform mat4 uMvp;
            attribute vec4 aPosition;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main() {
                gl_Position = uMvp * aPosition;
                vUv = aUv;
            }
            """

        // Eye crops are applied in image space, before the SurfaceTexture transform, so they stay
        // right whatever flip or crop the decoder's buffer needs.
        private const val FRAGMENT_SHADER =
            """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vUv;
            uniform samplerExternalOES uTexture;
            uniform mat4 uTexMatrix;
            uniform vec4 uEyeA;
            uniform vec4 uEyeB;
            uniform int uAnaglyph;

            vec3 sampleEye(vec4 eye) {
                vec2 uv = vUv * eye.xy + eye.zw;
                return texture2D(uTexture, (uTexMatrix * vec4(uv, 0.0, 1.0)).xy).rgb;
            }

            void main() {
                if (uAnaglyph == 1) {
                    // Dubois least-squares red/cyan matrices (GLSL matrices are column-major).
                    mat3 leftMix = mat3(0.456, -0.040, -0.015, 0.500, -0.038, -0.021, 0.176, -0.016, -0.005);
                    mat3 rightMix = mat3(-0.043, 0.378, -0.072, -0.088, 0.734, -0.113, -0.002, -0.018, 1.226);
                    vec3 color = leftMix * sampleEye(uEyeA) + rightMix * sampleEye(uEyeB);
                    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
                } else {
                    gl_FragColor = vec4(sampleEye(uEyeA), 1.0);
                }
            }
            """

        /**
         * Views synthesized from a frame that holds the picture in its top half and its depth in
         * the bottom half (image space, v up). Samples stay half a texel away from the boundary
         * between the two so filtering never mixes them.
         */
        private val SYNTHESIS_SHADER =
            """
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            varying vec2 vUv;
            uniform samplerExternalOES uTexture;
            uniform mat4 uTexMatrix;
            uniform vec2 uFrameTexel;
            uniform vec2 uOffsetA;
            uniform vec2 uOffsetB;
            uniform int uAnaglyph;
            uniform float uZoom;

            vec3 colorAt(vec2 uv) {
                vec2 c = clamp(uv, 0.0, 1.0);
                vec2 img = vec2(c.x, max(0.5 + c.y * 0.5, 0.5 + uFrameTexel.y));
                return texture2D(uTexture, (uTexMatrix * vec4(img, 0.0, 1.0)).xy).rgb;
            }

            float depthAt(vec2 uv) {
                vec2 c = clamp(uv, 0.0, 1.0);
                vec2 img = vec2(c.x, min(c.y * 0.5, 0.5 - uFrameTexel.y));
                return texture2D(uTexture, (uTexMatrix * vec4(img, 0.0, 1.0)).xy).r;
            }
            """ +
                StereoShaders.synthesis(steps = 20, refine = 3) +
                """
            void main() {
                vec2 uv = (vUv - 0.5) * uZoom + 0.5;
                vec3 a = synthesize(uv, uOffsetA).rgb;
                if (uAnaglyph == 1) {
                    vec3 b = synthesize(uv, uOffsetB).rgb;
                    mat3 leftMix = mat3(0.456, -0.040, -0.015, 0.500, -0.038, -0.021, 0.176, -0.016, -0.005);
                    mat3 rightMix = mat3(-0.043, 0.378, -0.072, -0.088, 0.734, -0.113, -0.002, -0.018, 1.226);
                    gl_FragColor = vec4(clamp(leftMix * a + rightMix * b, 0.0, 1.0), 1.0);
                } else {
                    gl_FragColor = vec4(a, 1.0);
                }
            }
            """

        private fun buildQuad(): FloatBuffer {
            // x, y, z, u, v as a triangle strip; image-space v = 0 is the bottom of the picture.
            val data =
                floatArrayOf(
                    -1f,
                    -1f,
                    0f,
                    0f,
                    0f,
                    1f,
                    -1f,
                    0f,
                    1f,
                    0f,
                    -1f,
                    1f,
                    0f,
                    0f,
                    1f,
                    1f,
                    1f,
                    0f,
                    1f,
                    1f,
                )
            return ByteBuffer.allocateDirect(data.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(data)
                    position(0)
                }
        }
    }
}
