package com.illuminazionetech.vrclip.player.gl

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.Matrix
import android.view.Surface
import kotlin.math.atan2

/**
 * Turns the device rotation sensor into a camera view matrix for "magic window" viewing of 360 and
 * 180 video: hold the phone up and move it to look around. The math follows Media3's own spherical
 * player: the rotation vector is remapped for the current display rotation, then rotated 90° about
 * X because the sensor's world has Z pointing at the sky while the scene has Y up. The first
 * reading is used to recenter the yaw, so the middle of the video starts straight ahead whichever
 * way the viewer happens to face.
 */
internal class OrientationTracker(context: Context, private val onChange: () -> Unit) :
    SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val isAvailable: Boolean
        get() = sensor != null

    private val lock = Any()
    private val deviceMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private var roll = 0f
    private var recenterYaw: Float? = null
    private var registered = false

    @Volatile var displayRotation: Int = Surface.ROTATION_0

    private val rotation = FloatArray(16)
    private val remapped = FloatArray(16)
    private val angles = FloatArray(3)

    fun start() {
        if (registered || sensor == null) return
        registered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!registered) return
        sensorManager?.unregisterListener(this)
        registered = false
        synchronized(lock) {
            Matrix.setIdentityM(deviceMatrix, 0)
            roll = 0f
            recenterYaw = null
        }
    }

    /** Makes whatever the viewer is facing now the center of the video. */
    fun recenter() {
        synchronized(lock) { recenterYaw = null }
    }

    /** Copies the latest orientation into [outMatrix]; returns the device roll in radians. */
    fun read(outMatrix: FloatArray): Float =
        synchronized(lock) {
            System.arraycopy(deviceMatrix, 0, outMatrix, 0, 16)
            roll
        }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        remapForDisplay(rotation, displayRotation)

        SensorManager.remapCoordinateSystem(
            rotation,
            SensorManager.AXIS_X,
            SensorManager.AXIS_Z,
            remapped,
        )
        SensorManager.getOrientation(remapped, angles)
        val deviceRoll = -angles[2]

        Matrix.rotateM(rotation, 0, 90f, 1f, 0f, 0f)

        synchronized(lock) {
            // The camera looks along the negated third row of the view matrix (column-major
            // elements 2, 6 and 10); its heading is the angle of that vector around the Y axis.
            val yaw = Math.toDegrees(atan2(rotation[2].toDouble(), rotation[10].toDouble())).toFloat()
            val offset = recenterYaw ?: yaw.also { recenterYaw = it }
            Matrix.rotateM(rotation, 0, offset, 0f, 1f, 0f)
            System.arraycopy(rotation, 0, deviceMatrix, 0, 16)
            roll = deviceRoll
        }
        onChange()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun remapForDisplay(matrix: FloatArray, rotation: Int) {
        val (xAxis, yAxis) =
            when (rotation) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> return
            }
        System.arraycopy(matrix, 0, remapped, 0, 16)
        SensorManager.remapCoordinateSystem(remapped, xAxis, yAxis, matrix)
    }
}
