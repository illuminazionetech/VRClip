package com.illuminazionetech.vrclip.player.gl

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.abs
import kotlin.math.exp

/**
 * Turns small tilts of the phone into a viewpoint offset for motion parallax: tilt the right edge
 * away and the picture is seen from a little to the left, so near objects slide against the
 * background as through a window. The gyroscope's rotation is integrated and slowly let go, so the
 * view settles back to the center while the phone is held still and holding it at an angle never
 * leaves the scene skewed.
 */
internal class ParallaxTracker(context: Context, private val onChange: () -> Unit) :
    SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    val isAvailable: Boolean
        get() = sensor != null

    @Volatile var displayRotation: Int = Surface.ROTATION_0

    private val lock = Any()
    private var tiltX = 0f
    private var tiltY = 0f
    private var offsetX = 0f
    private var offsetY = 0f
    private var lastTimestamp = 0L
    private var registered = false

    fun start() {
        if (registered || sensor == null) return
        registered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!registered) return
        sensorManager?.unregisterListener(this)
        registered = false
        synchronized(lock) {
            tiltX = 0f
            tiltY = 0f
            offsetX = 0f
            offsetY = 0f
            lastTimestamp = 0L
        }
    }

    /**
     * Writes the viewpoint offset into [out] (x to the right, y up), in units of the stereo half
     * range: 1 is as far as one eye of the side-by-side views.
     */
    fun read(out: FloatArray) {
        synchronized(lock) {
            out[0] = offsetX
            out[1] = offsetY
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Angular speed around the screen's axes, from the device's natural orientation.
        val wx = event.values[0]
        val wy = event.values[1]
        val (screenX, screenY) =
            when (displayRotation) {
                Surface.ROTATION_90 -> wy to -wx
                Surface.ROTATION_180 -> -wx to -wy
                Surface.ROTATION_270 -> -wy to wx
                else -> wx to wy
            }
        val changed =
            synchronized(lock) {
                if (lastTimestamp != 0L) {
                    val dt = ((event.timestamp - lastTimestamp) / 1e9f).coerceIn(0f, 0.1f)
                    val keep = exp(-dt / SETTLE_SECONDS)
                    tiltX = ((tiltX + screenX * dt) * keep).coerceIn(-LIMIT, LIMIT)
                    tiltY = ((tiltY + screenY * dt) * keep).coerceIn(-LIMIT, LIMIT)
                }
                lastTimestamp = event.timestamp
                val x = deadZone((-tiltY / FULL_TILT).coerceIn(-MAX_OFFSET, MAX_OFFSET))
                val y = deadZone((tiltX / FULL_TILT).coerceIn(-MAX_OFFSET, MAX_OFFSET))
                // Sensor noise while the phone rests should not redraw the screen 50 times a
                // second.
                val moved = abs(x - offsetX) > REDRAW_STEP || abs(y - offsetY) > REDRAW_STEP
                if (moved || (x == 0f && y == 0f && (offsetX != 0f || offsetY != 0f))) {
                    offsetX = x
                    offsetY = y
                    true
                } else false
            }
        if (changed) onChange()
    }

    /** Below a small offset the view snaps to the center, where it is drawn without synthesis. */
    private fun deadZone(value: Float) = if (abs(value) < DEAD_ZONE) 0f else value

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        /** Tilt in radians that moves the viewpoint by a full eye: about 12 degrees. */
        const val FULL_TILT = 0.21f
        const val LIMIT = FULL_TILT * 1.5f
        const val MAX_OFFSET = 1.25f
        const val SETTLE_SECONDS = 1.4f
        const val DEAD_ZONE = 0.03f
        const val REDRAW_STEP = 0.004f
    }
}
