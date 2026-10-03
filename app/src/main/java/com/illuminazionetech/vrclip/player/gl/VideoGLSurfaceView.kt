package com.illuminazionetech.vrclip.player.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.illuminazionetech.vrclip.player.ProjectionMode

/**
 * [GLSurfaceView] hosting [VideoGLRenderer] for 360/180, stereo 3D and live 2D to 3D playback on
 * phones and tablets. It renders only when something changed (a new video frame, device motion, a
 * gesture), follows the device orientation for spherical video when [gyroEnabled] is on and its
 * tilt for the parallax view, and hands the decoder's [Surface] out through [onSurfaceAvailable] /
 * [onSurfaceDestroyed] on the main thread. Gestures are handled by the Compose layer above, which
 * calls [pan], [zoom] and [recenter].
 */
class VideoGLSurfaceView(context: Context) : GLSurfaceView(context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val orientation = OrientationTracker(context) { requestRender() }
    private val parallax = ParallaxTracker(context) { requestRender() }
    private val renderer: VideoGLRenderer
    private var surface: Surface? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var resumed = false

    var onSurfaceAvailable: ((Surface) -> Unit)? = null
    var onSurfaceDestroyed: ((Surface) -> Unit)? = null

    val hasMotionSensor: Boolean
        get() = orientation.isAvailable

    private var config = RenderConfig()

    var gyroEnabled: Boolean = true
        set(value) {
            field = value
            updateSensor()
        }

    private var projectionMode = ProjectionMode.FLAT

    /**
     * Buffer size of the video surface. Needed while video effects draw into it (live 3D): GL
     * rendering into a SurfaceTexture uses its default buffer size, unlike the decoder.
     */
    var bufferSize: Pair<Int, Int>? = null
        set(value) {
            if (field == value) return
            field = value
            value?.let { (width, height) ->
                surfaceTexture?.setDefaultBufferSize(width, height)
                renderer.frameSize = width to height
            }
        }

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        renderer =
            VideoGLRenderer(
                orientation = orientation,
                parallax = parallax,
                onSurfaceCreated = { texture, newSurface ->
                    mainHandler.post { swapSurface(texture, newSurface) }
                },
                onFrameAvailable = { requestRender() },
            )
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    internal fun setConfig(config: RenderConfig) {
        if (renderer.config == config) return
        renderer.config = config
        this.config = config
        projectionMode = config.mode
        updateSensor()
        requestRender()
    }

    /** Drag by [dx]/[dy] pixels: the picture follows the finger. */
    fun pan(dx: Float, dy: Float) {
        val degreesPerPixel = renderer.fieldOfView / height.coerceAtLeast(1)
        renderer.touchYaw -= dx * degreesPerPixel
        renderer.touchPitch = (renderer.touchPitch + dy * degreesPerPixel).coerceIn(-85f, 85f)
        requestRender()
    }

    /** Pinch: [factor] > 1 zooms in. */
    fun zoom(factor: Float) {
        renderer.fieldOfView =
            (renderer.fieldOfView / factor).coerceIn(
                VideoGLRenderer.MIN_FOV,
                VideoGLRenderer.MAX_FOV,
            )
        requestRender()
    }

    fun recenter() {
        renderer.touchYaw = 0f
        renderer.touchPitch = 0f
        renderer.fieldOfView = VideoGLRenderer.DEFAULT_FOV
        orientation.recenter()
        requestRender()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        updateSensor()
    }

    override fun onPause() {
        resumed = false
        updateSensor()
        super.onPause()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateDisplayRotation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Rotating the device resizes the view; the sensor math depends on the display rotation.
        updateDisplayRotation()
    }

    private fun updateDisplayRotation() {
        display?.let {
            orientation.displayRotation = it.rotation
            parallax.displayRotation = it.rotation
        }
    }

    override fun onDetachedFromWindow() {
        orientation.stop()
        parallax.stop()
        queueEvent { renderer.releaseGl() }
        super.onDetachedFromWindow()
        surface?.let { onSurfaceDestroyed?.invoke(it) }
        surface?.release()
        surfaceTexture?.release()
        surface = null
        surfaceTexture = null
    }

    private fun updateSensor() {
        if (resumed && gyroEnabled && projectionMode.isSpherical) orientation.start()
        else orientation.stop()
        if (resumed && config.depthPacked && config.output == StereoOutputMode.Parallax) {
            parallax.start()
        } else {
            parallax.stop()
        }
    }

    private fun swapSurface(texture: SurfaceTexture, newSurface: Surface) {
        val old = surface
        val oldTexture = surfaceTexture
        surface = newSurface
        surfaceTexture = texture
        bufferSize?.let { (width, height) ->
            texture.setDefaultBufferSize(width, height)
            renderer.frameSize = width to height
        }
        onSurfaceAvailable?.invoke(newSurface)
        if (old != null) {
            onSurfaceDestroyed?.invoke(old)
            old.release()
        }
        oldTexture?.release()
    }
}
