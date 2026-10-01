package com.illuminazionetech.vrclip.player.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import com.illuminazionetech.vrclip.player.ProjectionMode
import com.illuminazionetech.vrclip.player.StereoLayout
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
}

/** Everything the renderer needs to know about what to draw; replaced as a whole. */
internal data class RenderConfig(
    val mode: ProjectionMode = ProjectionMode.FLAT,
    val output: StereoOutputMode = StereoOutputMode.SingleEye,
    /** Width / height of the decoded frame, pixel aspect ratio included. */
    val frameAspect: Float = 16f / 9f,
)

/**
 * Renders decoded video frames (delivered through a [SurfaceTexture]) for stereo 3D and 360/180
 * content: a letterboxed quad per eye for flat 3D, an inward-facing sphere or half-dome for
 * spherical video, with split-screen and anaglyph output. The camera combines the device
 * orientation (from [OrientationTracker]) with touch/pinch input. Plain 2D video does not come
 * here; it plays through a SurfaceView, which is cheaper and keeps HDR.
 */
internal class VideoGLRenderer(
    private val orientation: OrientationTracker,
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
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glUniform1i(uTexture, 0)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)

        val current = config
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
            drawQuad(eyeAspect, viewportAspect)
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

    /** Draws the eye picture letterboxed inside the current viewport. */
    private fun drawQuad(eyeAspect: Float, viewportAspect: Float) {
        val buffer = quad ?: return
        Matrix.setIdentityM(mvp, 0)
        if (eyeAspect > 0f) {
            if (eyeAspect > viewportAspect)
                Matrix.scaleM(mvp, 0, 1f, viewportAspect / eyeAspect, 1f)
            else Matrix.scaleM(mvp, 0, eyeAspect / viewportAspect, 1f, 1f)
        }
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        buffer.position(0)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 5 * 4, buffer)
        GLES20.glEnableVertexAttribArray(aPosition)
        buffer.position(3)
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 5 * 4, buffer)
        GLES20.glEnableVertexAttribArray(aUv)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** Frees GL objects; must run on the GL thread while the context is still current. */
    fun releaseGl() {
        if (program != 0) GLES20.glDeleteProgram(program)
        if (oesTexture != 0) GLES20.glDeleteTextures(1, intArrayOf(oesTexture), 0)
        program = 0
        oesTexture = 0
    }

    companion object {
        const val DEFAULT_FOV = 75f
        const val MIN_FOV = 30f
        const val MAX_FOV = 110f

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
