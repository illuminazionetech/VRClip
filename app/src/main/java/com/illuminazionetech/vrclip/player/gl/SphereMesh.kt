package com.illuminazionetech.vrclip.player.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * A procedurally generated UV sphere seen from the inside, used to render equirectangular 360 and
 * 180 video. Vertices interleave position (xyz) and texture coordinates (uv) in image space
 * (v = 0 at the bottom of the picture). The center of the picture (u = 0.5) sits straight ahead
 * on -Z, so a viewer with an identity camera looks at the middle of the video; [sweepDegrees] of
 * 180 builds the front half-dome that 180° video covers.
 */
internal class SphereMesh(
    latitudeSegments: Int = 64,
    longitudeSegments: Int = 96,
    radius: Float = 50f,
    sweepDegrees: Float = 360f,
) {
    val vertexBuffer: FloatBuffer
    val indexBuffer: ShortBuffer
    val indexCount: Int

    init {
        val vertices = FloatArray((latitudeSegments + 1) * (longitudeSegments + 1) * STRIDE_FLOATS)
        val sweep = Math.toRadians(sweepDegrees.toDouble())
        var i = 0
        for (lat in 0..latitudeSegments) {
            val theta = Math.PI * lat / latitudeSegments // 0 at the zenith, PI at the nadir
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)
            for (lon in 0..longitudeSegments) {
                // phi = 0 is straight ahead (-Z), growing to the right (+X).
                val phi = -sweep / 2.0 + sweep * lon / longitudeSegments
                vertices[i++] = (radius * sinTheta * sin(phi)).toFloat()
                vertices[i++] = (radius * cosTheta).toFloat()
                vertices[i++] = (-radius * sinTheta * cos(phi)).toFloat()
                vertices[i++] = lon.toFloat() / longitudeSegments
                vertices[i++] = 1f - lat.toFloat() / latitudeSegments
            }
        }

        val indices = ShortArray(latitudeSegments * longitudeSegments * 6)
        val row = longitudeSegments + 1
        var j = 0
        for (lat in 0 until latitudeSegments) {
            for (lon in 0 until longitudeSegments) {
                val first = lat * row + lon
                val second = first + row
                indices[j++] = first.toShort()
                indices[j++] = second.toShort()
                indices[j++] = (first + 1).toShort()
                indices[j++] = (first + 1).toShort()
                indices[j++] = second.toShort()
                indices[j++] = (second + 1).toShort()
            }
        }

        vertexBuffer =
            ByteBuffer.allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(vertices)
                    position(0)
                }
        indexBuffer =
            ByteBuffer.allocateDirect(indices.size * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .apply {
                    put(indices)
                    position(0)
                }
        indexCount = indices.size
    }

    companion object {
        const val STRIDE_FLOATS = 5 // x, y, z, u, v
    }
}
