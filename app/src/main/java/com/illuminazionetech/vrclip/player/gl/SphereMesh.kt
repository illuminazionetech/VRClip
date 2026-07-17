package com.illuminazionetech.vrclip.player.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * A procedurally generated UV sphere (inward-facing, camera at the center) used to render
 * equirectangular 360/180 video. Vertices interleave position (xyz) and texture coordinates (uv);
 * [horizontalSweepDegrees] lets 180° content use a half-dome instead of a full sphere.
 */
internal class SphereMesh(
    latitudeSegments: Int = 48,
    longitudeSegments: Int = 48,
    radius: Float = 50f,
    horizontalSweepDegrees: Float = 360f,
) {
    val vertexBuffer: FloatBuffer
    val indexBuffer: ShortBuffer
    val indexCount: Int

    companion object {
        private const val FLOAT_SIZE_BYTES = 4
        private const val SHORT_SIZE_BYTES = 2
        const val STRIDE_FLOATS = 5 // x, y, z, u, v
    }

    init {
        val vertices = mutableListOf<Float>()
        val sweepRad = Math.toRadians(horizontalSweepDegrees.toDouble())
        val startLon = -sweepRad / 2.0

        for (lat in 0..latitudeSegments) {
            val theta = Math.PI * lat / latitudeSegments // 0 (top) .. PI (bottom)
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)

            for (lon in 0..longitudeSegments) {
                val phi = startLon + sweepRad * lon / longitudeSegments
                val sinPhi = sin(phi)
                val cosPhi = cos(phi)

                // Inward-facing sphere: camera sits at the origin looking out.
                val x = (radius * sinTheta * cosPhi).toFloat()
                val y = (radius * cosTheta).toFloat()
                val z = (radius * sinTheta * sinPhi).toFloat()

                val u = (lon.toFloat() / longitudeSegments)
                val v = (lat.toFloat() / latitudeSegments)

                vertices.addAll(listOf(x, y, z, u, v))
            }
        }

        val indices = mutableListOf<Short>()
        val vertsPerRow = longitudeSegments + 1
        for (lat in 0 until latitudeSegments) {
            for (lon in 0 until longitudeSegments) {
                val first = (lat * vertsPerRow + lon)
                val second = first + vertsPerRow

                // Wind so the visible (front) face points inward, toward the camera at origin.
                indices.add(first.toShort())
                indices.add(second.toShort())
                indices.add((first + 1).toShort())

                indices.add((first + 1).toShort())
                indices.add(second.toShort())
                indices.add((second + 1).toShort())
            }
        }

        vertexBuffer =
            ByteBuffer.allocateDirect(vertices.size * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(vertices.toFloatArray())
                    position(0)
                }

        indexBuffer =
            ByteBuffer.allocateDirect(indices.size * SHORT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .apply {
                    put(indices.toShortArray())
                    position(0)
                }

        indexCount = indices.size
    }
}
