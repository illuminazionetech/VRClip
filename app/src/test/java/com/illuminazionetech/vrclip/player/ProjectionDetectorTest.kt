package com.illuminazionetech.vrclip.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectionDetectorTest {

    private fun detect(name: String, frame: FrameInfo? = null) =
        ProjectionDetector.detect("/storage/emulated/0/Download/VRClip/$name", frame).mode

    @Test
    fun sphericalTagsWithDegreeSignOrVrWord() {
        assertEquals(ProjectionMode.MONO_360, detect("Alps flight 360°.mp4"))
        assertEquals(ProjectionMode.MONO_360, detect("Roller coaster 360 VR [abc].mp4"))
        assertEquals(ProjectionMode.MONO_360, detect("walk_vr360.webm"))
        assertEquals(ProjectionMode.STEREO_180_LR, detect("concert_VR180_SBS.mp4"))
        assertEquals(ProjectionMode.STEREO_360_TB, detect("dive 360 3D TB.mp4"))
        assertEquals(ProjectionMode.MONO_360, detect("maldive_360.mp4"))
        assertEquals(ProjectionMode.STEREO_180_LR, detect("concerto_vr180_sbs.mp4"))
        assertEquals(ProjectionMode.MONO_180, detect("walk-180.mp4"))
    }

    @Test
    fun bareNumbersNeedConfirmation() {
        assertEquals(ProjectionMode.FLAT, detect("Xbox 360 gameplay.mp4", FrameInfo(1920, 1080)))
        assertEquals(ProjectionMode.FLAT, detect("Xbox_360_gameplay.mp4"))
        assertEquals(ProjectionMode.FLAT, detect("Top 180 songs.mp4"))
        assertEquals(ProjectionMode.FLAT, detect("Mountains 360.mp4"))
        // The frame shape confirms a bare "360".
        assertEquals(ProjectionMode.MONO_360, detect("Mountains 360.mp4", FrameInfo(5760, 2880)))
    }

    @Test
    fun resolutionsAreNotSphericalTags() {
        assertEquals(ProjectionMode.FLAT, detect("clip 640x360.mp4"))
        assertEquals(ProjectionMode.FLAT, detect("clip 360p.mp4"))
        assertEquals(ProjectionMode.FLAT, detect("clip 1080p.mp4"))
    }

    @Test
    fun stereoLayoutsForFlat3d() {
        assertEquals(ProjectionMode.SBS_3D, detect("Avatar 3D SBS.mkv"))
        assertEquals(ProjectionMode.SBS_3D, detect("trailer_hsbs.mp4"))
        assertEquals(ProjectionMode.OU_3D, detect("trailer HOU.mp4"))
        assertEquals(ProjectionMode.OU_3D, detect("film over-under.mp4"))
        assertEquals(ProjectionMode.SBS_3D, detect("demo 3D LR.mp4"))
    }

    @Test
    fun commonWordsAreNotStereoTags() {
        assertEquals(ProjectionMode.FLAT, detect("1 TB SSD review.mp4"))
        assertEquals(ProjectionMode.FLAT, detect("Ici ou là.mp4"))
        assertEquals(ProjectionMode.FLAT, detect("Guitar tab lesson.mp4"))
        assertEquals(ProjectionMode.FLAT, detect("3D printing basics.mp4"))
    }

    @Test
    fun layoutComesFromFrameShapeForSphericalTags() {
        assertEquals(ProjectionMode.STEREO_360_TB, detect("dive 360°.mp4", FrameInfo(3840, 3840)))
        assertEquals(ProjectionMode.STEREO_360_LR, detect("dive 360°.mp4", FrameInfo(7680, 1920)))
        assertEquals(ProjectionMode.STEREO_180_LR, detect("VR180 hike.mp4", FrameInfo(5760, 2880)))
        assertEquals(ProjectionMode.MONO_180, detect("VR180 hike.mp4", FrameInfo(2880, 2880)))
    }

    @Test
    fun equirectangularAspectRatioAlone() {
        assertEquals(ProjectionMode.MONO_360, detect("video [xyz].mp4", FrameInfo(3840, 1920)))
        assertEquals(ProjectionMode.FLAT, detect("video [xyz].mp4", FrameInfo(1280, 640)))
        assertEquals(ProjectionMode.FLAT, detect("video [xyz].mp4", FrameInfo(1920, 1080)))
    }

    @Test
    fun sideBySideConversionsByShape() {
        assertEquals(ProjectionMode.SBS_3D, detect("trailer.mp4", FrameInfo(3840, 1080)))
        assertEquals(ProjectionMode.SBS_3D, detect("trailer.mp4", FrameInfo(4096, 1152)))
        // Too small to be a conversion of a real video, or not exactly 32:9.
        assertEquals(ProjectionMode.FLAT, detect("trailer.mp4", FrameInfo(2560, 720)))
        assertEquals(ProjectionMode.FLAT, detect("trailer.mp4", FrameInfo(3840, 1600)))
    }

    @Test
    fun containerMetadataWins() {
        val topBottom =
            FrameInfo(1920, 2160, stereoMode = ProjectionDetector.STEREO_MODE_TOP_BOTTOM)
        assertEquals(ProjectionMode.OU_3D, detect("plain.mp4", topBottom))

        val halfDome =
            FrameInfo(
                4096,
                2048,
                stereoMode = ProjectionDetector.STEREO_MODE_LEFT_RIGHT,
                projectionData = equiBox(left = 0x40000000, right = 0x40000000),
            )
        assertEquals(ProjectionMode.STEREO_180_LR, detect("anything 360.mp4", halfDome))

        val full = FrameInfo(3840, 1920, projectionData = equiBox(left = 0, right = 0))
        assertEquals(ProjectionMode.MONO_360, detect("plain.mp4", full))

        val v1 =
            FrameInfo(
                3840,
                1920,
                projectionData =
                    "<GSpherical:ProjectionType>equirectangular</GSpherical:ProjectionType>"
                        .toByteArray(),
            )
        assertEquals(ProjectionMode.MONO_360, detect("plain.mp4", v1))
    }

    @Test
    fun eyeAspectFollowsPacking() {
        // Full side-by-side 3840x1080: each eye is 16:9.
        assertEquals(
            16f / 9f,
            ProjectionMode.eyeAspectRatio(ProjectionMode.SBS_3D, 3840f / 1080f),
            0.01f,
        )
        // Half side-by-side 1920x1080: each eye is squeezed, shown at 16:9.
        assertEquals(
            16f / 9f,
            ProjectionMode.eyeAspectRatio(ProjectionMode.SBS_3D, 1920f / 1080f),
            0.01f,
        )
        // Full over-under 1920x2160.
        assertEquals(
            16f / 9f,
            ProjectionMode.eyeAspectRatio(ProjectionMode.OU_3D, 1920f / 2160f),
            0.01f,
        )
        assertEquals(1.5f, ProjectionMode.eyeAspectRatio(ProjectionMode.FLAT, 1.5f), 0.001f)
    }

    /** A minimal sv3d/proj/equi payload with the given left/right bounds. */
    private fun equiBox(left: Int, right: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun int(v: Int) =
            out.write(
                byteArrayOf(
                    (v ushr 24).toByte(),
                    (v ushr 16).toByte(),
                    (v ushr 8).toByte(),
                    v.toByte(),
                )
            )
        int(28)
        out.write("equi".toByteArray())
        int(0) // version and flags
        int(0) // top
        int(0) // bottom
        int(left)
        int(right)
        return out.toByteArray()
    }
}
