package com.illuminazionetech.vrclip.player.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import com.illuminazionetech.vrclip.player.ProjectionMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Which half of a stereo-cropped frame to sample. No-op ([Mono]) for non-stereo content. */
internal enum class StereoEye {
    Mono,
    Left,
    Right,
}

/** How stereo content is presented on a flat (non-headset) display. */
enum class StereoOutputMode {
    /** Full-screen using a single chosen eye, normal viewing on a phone/tablet screen. */
    SingleEye,

    /** Left/right halves of the viewport rendered separately, for a Cardboard-style holder. */
    SplitScreen,
}

/**
 * Renders decoded video frames (delivered via [SurfaceTexture] from ExoPlayer) either as a flat
 * textured quad, or, for 360/180/stereo-3D [ProjectionMode]s, projected onto an inward-facing
 * [SphereMesh] (equirect) or a cropped flat quad (SBS/OU), with yaw/pitch driven by touch drag
 * and/or the device gyroscope. This is the phone/tablet rendering path; Quest uses the Meta
 * Spatial SDK scene instead (see `player.quest`).
 */
internal class VideoGLRenderer(
    private val getProjectionMode: () -> ProjectionMode,
    private val getOutputMode: () -> StereoOutputMode,
    private val onSurfaceReady: (Surface) -> Unit,
) : GLSurfaceView.Renderer {

    @Volatile var yawDegrees: Float = 0f
    @Volatile var pitchDegrees: Float = 0f

    private var program = 0
    private var oesTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private val surfaceTextureMatrix = FloatArray(16)

    private var aPositionLoc = 0
    private var aTexCoordLoc = 0
    private var uMvpMatrixLoc = 0
    private var uTexMatrixLoc = 0
    private var uUvScaleLoc = 0
    private var uUvOffsetLoc = 0
    private var uTextureLoc = 0

    private var sphereMesh: SphereMesh? = null
    private var quadVertexBuffer: FloatBuffer? = null

    private var viewportWidth = 1
    private var viewportHeight = 1

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val identityMatrix =
        FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    @Volatile private var frameAvailable = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        program = GlUtil.linkProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        uMvpMatrixLoc = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uTexMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uUvScaleLoc = GLES20.glGetUniformLocation(program, "uUvScale")
        uUvOffsetLoc = GLES20.glGetUniformLocation(program, "uUvOffset")
        uTextureLoc = GLES20.glGetUniformLocation(program, "uTexture")

        oesTextureId = GlUtil.createOesTexture()
        val texture =
            SurfaceTexture(oesTextureId).apply {
                setOnFrameAvailableListener { frameAvailable = true }
            }
        surfaceTexture = texture
        sphereMesh = SphereMesh()
        quadVertexBuffer = buildQuadBuffer()

        onSurfaceReady(Surface(texture))
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = max(width, 1)
        viewportHeight = max(height, 1)
        GLES20.glViewport(0, 0, width, height)
        val aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, FIELD_OF_VIEW_DEGREES, aspect, 0.1f, 200f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val texture = surfaceTexture ?: return
        if (frameAvailable) {
            texture.updateTexImage()
            texture.getTransformMatrix(surfaceTextureMatrix)
            frameAvailable = false
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glUniform1i(uTextureLoc, 0)
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, surfaceTextureMatrix, 0)

        val mode = getProjectionMode()
        when {
            mode.requiresImmersiveRendering && (mode.is360 || mode.is180) -> drawSphere(mode)
            mode.requiresImmersiveRendering -> drawFlatStereoQuad(mode)
            else -> drawFlatQuad(StereoEye.Mono)
        }
    }

    private fun drawSphere(mode: ProjectionMode) {
        val mesh = sphereMesh ?: return
        val pitchRad = Math.toRadians(pitchDegrees.coerceIn(-89f, 89f).toDouble())
        val yawRad = Math.toRadians(yawDegrees.toDouble())
        val cosPitch = cos(pitchRad)

        // Look direction on the unit sphere for the current yaw/pitch; camera stays at the
        // origin (center of the sphere) and only rotates, matching how a viewer's head moves.
        val lookX = (cosPitch * sin(yawRad)).toFloat()
        val lookY = sin(pitchRad).toFloat()
        val lookZ = -(cosPitch * cos(yawRad)).toFloat()

        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 0f, lookX, lookY, lookZ, 0f, 1f, 0f)

        mesh.vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(
            aPositionLoc,
            3,
            GLES20.GL_FLOAT,
            false,
            SphereMesh.STRIDE_FLOATS * 4,
            mesh.vertexBuffer,
        )
        GLES20.glEnableVertexAttribArray(aPositionLoc)

        mesh.vertexBuffer.position(3)
        GLES20.glVertexAttribPointer(
            aTexCoordLoc,
            2,
            GLES20.GL_FLOAT,
            false,
            SphereMesh.STRIDE_FLOATS * 4,
            mesh.vertexBuffer,
        )
        GLES20.glEnableVertexAttribArray(aTexCoordLoc)

        fun drawEye(eye: StereoEye, viewport: IntArray?) {
            viewport?.let { GLES20.glViewport(it[0], it[1], it[2], it[3]) }
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
            GLES20.glUniformMatrix4fv(uMvpMatrixLoc, 1, false, mvpMatrix, 0)
            applyEyeCrop(mode, eye)
            GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                mesh.indexCount,
                GLES20.GL_UNSIGNED_SHORT,
                mesh.indexBuffer,
            )
        }

        if (mode.isStereo && getOutputMode() == StereoOutputMode.SplitScreen) {
            val halfWidth = viewportWidth / 2
            drawEye(StereoEye.Left, intArrayOf(0, 0, halfWidth, viewportHeight))
            drawEye(StereoEye.Right, intArrayOf(halfWidth, 0, viewportWidth - halfWidth, viewportHeight))
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        } else {
            drawEye(if (mode.isStereo) StereoEye.Left else StereoEye.Mono, null)
        }
    }

    private fun drawFlatStereoQuad(mode: ProjectionMode) {
        if (getOutputMode() == StereoOutputMode.SplitScreen) {
            val halfWidth = viewportWidth / 2
            GLES20.glViewport(0, 0, halfWidth, viewportHeight)
            drawFlatQuad(StereoEye.Left, mode)
            GLES20.glViewport(halfWidth, 0, viewportWidth - halfWidth, viewportHeight)
            drawFlatQuad(StereoEye.Right, mode)
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        } else {
            drawFlatQuad(StereoEye.Left, mode)
        }
    }

    private fun drawFlatQuad(eye: StereoEye, mode: ProjectionMode = ProjectionMode.FLAT) {
        val quad = quadVertexBuffer ?: return
        quad.position(0)
        GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, 5 * 4, quad)
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        quad.position(3)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 5 * 4, quad)
        GLES20.glEnableVertexAttribArray(aTexCoordLoc)

        GLES20.glUniformMatrix4fv(uMvpMatrixLoc, 1, false, identityMatrix, 0)
        applyEyeCrop(mode, eye)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun applyEyeCrop(mode: ProjectionMode, eye: StereoEye) {
        val (scale, offset) = eyeCrop(mode, eye)
        GLES20.glUniform2f(uUvScaleLoc, scale[0], scale[1])
        GLES20.glUniform2f(uUvOffsetLoc, offset[0], offset[1])
    }

    private fun eyeCrop(mode: ProjectionMode, eye: StereoEye): Pair<FloatArray, FloatArray> {
        if (eye == StereoEye.Mono) return floatArrayOf(1f, 1f) to floatArrayOf(0f, 0f)
        val isTopBottom =
            mode == ProjectionMode.STEREO_360_TB ||
                mode == ProjectionMode.STEREO_180_TB ||
                mode == ProjectionMode.OU_3D
        val isLeftRight =
            mode == ProjectionMode.STEREO_360_LR ||
                mode == ProjectionMode.STEREO_180_LR ||
                mode == ProjectionMode.SBS_3D
        return when {
            isTopBottom && eye == StereoEye.Left -> floatArrayOf(1f, 0.5f) to floatArrayOf(0f, 0f)
            isTopBottom && eye == StereoEye.Right -> floatArrayOf(1f, 0.5f) to floatArrayOf(0f, 0.5f)
            isLeftRight && eye == StereoEye.Left -> floatArrayOf(0.5f, 1f) to floatArrayOf(0f, 0f)
            isLeftRight && eye == StereoEye.Right -> floatArrayOf(0.5f, 1f) to floatArrayOf(0.5f, 0f)
            else -> floatArrayOf(1f, 1f) to floatArrayOf(0f, 0f)
        }
    }

    fun release() {
        surfaceTexture?.release()
        surfaceTexture = null
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }

    companion object {
        private const val FIELD_OF_VIEW_DEGREES = 90f

        private const val VERTEX_SHADER =
            """
            uniform mat4 uMVPMatrix;
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vec4 tc = uTexMatrix * vec4(aTexCoord, 0.0, 1.0);
                vTexCoord = tc.xy;
            }
            """

        private const val FRAGMENT_SHADER =
            """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES uTexture;
            uniform vec2 uUvScale;
            uniform vec2 uUvOffset;
            void main() {
                vec2 uv = vTexCoord * uUvScale + uUvOffset;
                gl_FragColor = texture2D(uTexture, uv);
            }
            """

        private fun buildQuadBuffer(): FloatBuffer {
            // x, y, z, u, v, a full-screen triangle strip; V flipped (SurfaceTexture is
            // top-down while GL texture space is bottom-up) is handled via uTexMatrix instead.
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
