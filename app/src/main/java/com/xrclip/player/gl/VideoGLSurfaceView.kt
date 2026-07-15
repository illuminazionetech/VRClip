package com.xrclip.player.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.Surface
import com.xrclip.player.ProjectionMode
import kotlin.math.abs

/**
 * [GLSurfaceView] hosting [VideoGLRenderer] for 360/180/stereo-3D playback on phone/tablet.
 * Touch-drag pans the camera; call [setProjectionMode] whenever the active video's projection
 * changes and [onSurfaceReady] fires once (on the main thread) with the [Surface] to hand to
 * ExoPlayer via `setVideoSurface`.
 */
class VideoGLSurfaceView(context: Context) : GLSurfaceView(context) {

    /** Degrees of drag per pixel; tuned so a full-width swipe is roughly a quarter turn. */
    private var dragSensitivity = 0.25f

    @Volatile private var projectionMode: ProjectionMode = ProjectionMode.FLAT
    @Volatile private var outputMode: StereoOutputMode = StereoOutputMode.SingleEye

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val renderer: VideoGLRenderer
    private val mainHandler = Handler(Looper.getMainLooper())

    var onSurfaceReady: ((Surface) -> Unit)? = null

    init {
        setEGLContextClientVersion(2)
        renderer =
            VideoGLRenderer(
                getProjectionMode = { projectionMode },
                getOutputMode = { outputMode },
                onSurfaceReady = { surface -> mainHandler.post { onSurfaceReady?.invoke(surface) } },
            )
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setProjectionMode(mode: ProjectionMode) {
        projectionMode = mode
    }

    fun setOutputMode(mode: StereoOutputMode) {
        outputMode = mode
    }

    /** Absolute camera orientation, e.g. driven by [android.hardware.SensorManager] rotation. */
    fun setCameraOrientation(yawDegrees: Float, pitchDegrees: Float) {
        renderer.yawDegrees = yawDegrees
        renderer.pitchDegrees = pitchDegrees
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!projectionMode.requiresImmersiveRendering) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                        renderer.yawDegrees -= dx * dragSensitivity
                        renderer.pitchDegrees =
                            (renderer.pitchDegrees + dy * dragSensitivity).coerceIn(-89f, 89f)
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isDragging = false
        }
        return true
    }

    override fun onDetachedFromWindow() {
        // GL resources must be freed on the GL thread; queueEvent hops there for us.
        queueEvent { renderer.release() }
        super.onDetachedFromWindow()
    }
}
