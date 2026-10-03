package com.illuminazionetech.vrclip.player

import kotlin.math.abs

/**
 * What the decoder knows about the video track: frame size plus the stereo/spherical metadata
 * carried by the container (MP4 `st3d`/`sv3d` boxes, Spherical Video V1 XML, Matroska
 * `StereoMode`/`Projection`).
 */
data class FrameInfo(
    val width: Int,
    val height: Int,
    val pixelWidthHeightRatio: Float = 1f,
    /** Media3 `C.STEREO_MODE_*` value, or -1 when the container says nothing. */
    val stereoMode: Int = -1,
    val projectionData: ByteArray? = null,
    /** Frames per second from the container, or 0 when unknown. */
    val frameRate: Float = 0f,
) {
    val aspect: Float
        get() = if (height > 0) width * pixelWidthHeightRatio / height else 0f

    override fun equals(other: Any?): Boolean =
        other is FrameInfo &&
            width == other.width &&
            height == other.height &&
            pixelWidthHeightRatio == other.pixelWidthHeightRatio &&
            stereoMode == other.stereoMode &&
            frameRate == other.frameRate &&
            projectionData.contentEquals(other.projectionData)

    override fun hashCode(): Int =
        ((width * 31 + height) * 31 + stereoMode) * 31 + projectionData.contentHashCode()
}

/** Where an automatically detected [ProjectionMode] came from, shown next to the choice. */
enum class DetectionSource {
    Metadata,
    FileName,
    AspectRatio,
    Default,
}

data class Detection(val mode: ProjectionMode, val source: DetectionSource)

/**
 * Best-effort detection of a video's [ProjectionMode]. Container metadata wins when present; most
 * files downloaded through yt-dlp carry none, so filename conventions (the tags used by VR cameras
 * and video sites: "360", "VR180", "SBS", "over-under"...) come next, and the frame shape settles
 * what the name leaves open. The UI always offers a manual override for what this gets wrong.
 */
object ProjectionDetector {

    private val separators = Regex("[_.\\-\\[\\](){}+,]+")
    // "360"/"180" followed by a degree sign or a VR word, or preceded by "VR", is a spherical tag;
    // a bare number is only trusted when something else agrees (a stereo tag or the frame shape),
    // since titles are full of them ("Xbox 360", "180 days").
    private val strong360 = strongTag("360")
    private val strong180 = strongTag("180")
    private val bare360 = Regex("(?<![\\dx])360(?![\\dp])")
    private val bare180 = Regex("(?<![\\dx])180(?![\\dp])")
    private val notSpherical = Regex("\\bxbox ?360\\b")
    // "_360" / "-180" glued to the name is how cameras and VR sites tag exported files.
    private val attachedTag = Regex("(?:^|[_-])(360|180)(?=[_-]|$)")
    private val tag3d = Regex("\\b3d\\b|\\bstereo(?:scopic)?\\b")
    private val explicitLr = Regex("\\b(?:h|f|half ?|full ?)?sbs\\b|\\bside ?by ?side\\b|\\b3dh\\b")
    private val explicitTb =
        Regex(
            "\\b(?:hou|htb|fou|ftb)\\b|\\b(?:half|full) ?(?:ou|tb)\\b|\\bover ?under\\b|" +
                "\\btop ?(?:and ?)?bottom\\b|\\b3dv\\b"
        )
    // Short tags are too common in ordinary titles ("1 TB SSD", French "ou") to count on their own.
    private val shortLr = Regex("\\blr\\b")
    private val shortTb = Regex("\\b(?:tb|ou)\\b")

    /** Filename-only detection, used where the video has not been opened yet (e.g. lists). */
    fun detectProjection(filePath: String): ProjectionMode = detect(filePath, null).mode

    fun detect(filePath: String, frame: FrameInfo?): Detection {
        fromMetadata(frame)?.let {
            return Detection(it, DetectionSource.Metadata)
        }
        fromFileName(filePath, frame)?.let {
            return Detection(it, DetectionSource.FileName)
        }
        fromAspectRatio(frame)?.let {
            return Detection(it, DetectionSource.AspectRatio)
        }
        return Detection(ProjectionMode.FLAT, DetectionSource.Default)
    }

    private fun fromFileName(filePath: String, frame: FrameInfo?): ProjectionMode? {
        val name =
            filePath
                .substringAfterLast('/')
                .substringBeforeLast('.')
                .lowercase()
                .replace(separators, " ")
        val raw = filePath.substringAfterLast('/').substringBeforeLast('.').lowercase()
        val attached =
            if (raw.contains("xbox")) emptySet()
            else attachedTag.findAll(raw).map { it.groupValues[1] }.toSet()
        val cleaned = name.replace(notSpherical, " ")
        val strong360 = "360" in attached || strong360.containsMatchIn(cleaned)
        val strong180 = !strong360 && ("180" in attached || strong180.containsMatchIn(cleaned))
        val maybe360 = bare360.containsMatchIn(cleaned)
        val maybe180 = bare180.containsMatchIn(cleaned)
        val has3d = tag3d.containsMatchIn(cleaned)
        val context = strong360 || strong180 || maybe360 || maybe180 || has3d
        val lr =
            explicitLr.containsMatchIn(cleaned) || (context && shortLr.containsMatchIn(cleaned))
        val tb =
            explicitTb.containsMatchIn(cleaned) || (context && shortTb.containsMatchIn(cleaned))
        val layout =
            when {
                lr && !tb -> StereoLayout.LeftRight
                tb && !lr -> StereoLayout.TopBottom
                else -> null
            }
        val confirmed = layout != null || has3d
        val has360 =
            strong360 ||
                (maybe360 && (confirmed || layoutFromShape(frame, true, strict = true) != null))
        val has180 =
            !has360 &&
                (strong180 ||
                    (maybe180 &&
                        (confirmed || layoutFromShape(frame, false, strict = true) != null)))
        return when {
            has360 -> spherical(true, layout ?: layoutFromShape(frame, true) ?: StereoLayout.None)
            has180 -> spherical(false, layout ?: layoutFromShape(frame, false) ?: StereoLayout.None)
            layout == StereoLayout.LeftRight -> ProjectionMode.SBS_3D
            layout == StereoLayout.TopBottom -> ProjectionMode.OU_3D
            else -> null
        }
    }

    /**
     * For a spherical video whose name gives no stereo layout, the frame shape does: a mono
     * equirectangular 360 frame is 2:1 and a mono 180 frame 1:1, so a 360 frame that is square (or
     * a 180 frame that is 1:2) holds two views stacked, and one twice as wide holds two views side
     * by side. Returns null when the shape fits none of these; with [strict], a shape that matches
     * the mono layout counts as a match too (used to confirm a bare "360" in a title).
     */
    private fun layoutFromShape(
        frame: FrameInfo?,
        sphere360: Boolean,
        strict: Boolean = false,
    ): StereoLayout? {
        val aspect = frame?.aspect ?: return null
        if (aspect <= 0f) return null
        val mono = if (sphere360) 2f else 1f
        fun near(target: Float, tolerance: Float) = abs(aspect - target) / target < tolerance
        return when {
            near(mono * 2f, 0.05f) -> StereoLayout.LeftRight
            near(mono / 2f, 0.05f) -> StereoLayout.TopBottom
            near(mono, if (strict) 0.02f else 0.1f) -> StereoLayout.None
            else -> null
        }
    }

    private fun spherical(is360: Boolean, layout: StereoLayout): ProjectionMode =
        when (layout) {
            StereoLayout.None -> if (is360) ProjectionMode.MONO_360 else ProjectionMode.MONO_180
            StereoLayout.LeftRight ->
                if (is360) ProjectionMode.STEREO_360_LR else ProjectionMode.STEREO_180_LR
            StereoLayout.TopBottom ->
                if (is360) ProjectionMode.STEREO_360_TB else ProjectionMode.STEREO_180_TB
        }

    /**
     * Reads the spherical projection (Google Spherical Video V2 `sv3d`/`proj` boxes, or the V1 XML)
     * and the stereo layout (`st3d` box or Matroska StereoMode, both surfaced by Media3 as
     * `Format.stereoMode`).
     */
    internal fun fromMetadata(frame: FrameInfo?): ProjectionMode? {
        frame ?: return null
        val layout =
            when (frame.stereoMode) {
                STEREO_MODE_TOP_BOTTOM -> StereoLayout.TopBottom
                STEREO_MODE_LEFT_RIGHT -> StereoLayout.LeftRight
                else -> StereoLayout.None
            }
        val sphere = frame.projectionData?.let(::sphericalExtent)
        return when {
            sphere != null -> spherical(sphere == SphericalExtent.Full, layout)
            layout == StereoLayout.LeftRight -> ProjectionMode.SBS_3D
            layout == StereoLayout.TopBottom -> ProjectionMode.OU_3D
            else -> null
        }
    }

    private enum class SphericalExtent {
        Full,
        Half,
    }

    private fun sphericalExtent(data: ByteArray): SphericalExtent? {
        findBox(data, "equi")?.let { at ->
            // FullBox header (4 bytes) then top, bottom, left, right bounds as 0.32 fixed point.
            val base = at + 4 + 4
            if (base + 16 <= data.size) {
                val left = readUInt32(data, base + 8)
                val right = readUInt32(data, base + 12)
                val cropped = (left + right).toDouble() / 4294967296.0
                return if (cropped >= 0.4) SphericalExtent.Half else SphericalExtent.Full
            }
            return SphericalExtent.Full
        }
        // Mesh projections are what YouTube uses for VR180; render them as a half dome.
        if (findBox(data, "mshp") != null) return SphericalExtent.Half
        if (findBox(data, "cbmp") != null) return null
        val text = runCatching { String(data, Charsets.UTF_8) }.getOrNull() ?: return null
        if (!text.contains("equirectangular", ignoreCase = true)) return null
        val cropped = xmlInt(text, "CroppedAreaImageWidthPixels")
        val full = xmlInt(text, "FullPanoWidthPixels")
        return if (cropped != null && full != null && cropped * 2 <= full + 1) SphericalExtent.Half
        else SphericalExtent.Full
    }

    private fun xmlInt(text: String, tag: String): Int? =
        Regex("<(?:GSpherical:)?$tag>(\\d+)<").find(text)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Position of an ISO-BMFF box type in [data], only where it is preceded by a plausible box
     * size, so the word "equirectangular" in Spherical V1 XML is not mistaken for an equi box.
     */
    private fun findBox(data: ByteArray, fourCc: String): Int? {
        val needle = fourCc.toByteArray(Charsets.US_ASCII)
        outer@ for (i in 4..data.size - needle.size) {
            for (j in needle.indices) if (data[i + j] != needle[j]) continue@outer
            val boxSize = readUInt32(data, i - 4)
            if (boxSize in 8..(data.size - i + 4).toLong()) return i
        }
        return null
    }

    private fun readUInt32(data: ByteArray, at: Int): Long =
        ((data[at].toLong() and 0xFF) shl 24) or
            ((data[at + 1].toLong() and 0xFF) shl 16) or
            ((data[at + 2].toLong() and 0xFF) shl 8) or
            (data[at + 3].toLong() and 0xFF)

    /**
     * Last resort for files with neither metadata nor a telling name: an exactly 2:1 frame of at
     * least 1920 pixels is almost always an equirectangular 360 video (that is how YouTube serves
     * them), and a 32:9 frame of at least 3200 pixels two 16:9 views side by side (what 2D to 3D
     * conversions without the stereo box, from VRClip 1.4.0, look like). Anything else stays flat;
     * a square frame could be stacked stereo 360 but is far more often plain square video.
     */
    private fun fromAspectRatio(frame: FrameInfo?): ProjectionMode? {
        frame ?: return null
        if (frame.width < 1920 || frame.height <= 0) return null
        return when {
            abs(frame.aspect - 2f) < 0.01f -> ProjectionMode.MONO_360
            frame.width >= 3200 && abs(frame.aspect - SBS_16_9) / SBS_16_9 < 0.015f ->
                ProjectionMode.SBS_3D
            else -> null
        }
    }

    private const val SBS_16_9 = 32f / 9f

    private fun strongTag(number: String) =
        Regex(
            "(?<![\\dx])$number ?(?:°|º|deg|degrees?|gradi|vr|video)(?![a-z])|\\bvr ?$number(?![\\dp])"
        )

    // Mirrors androidx.media3.common.C.STEREO_MODE_* so this stays testable on the plain JVM.
    const val STEREO_MODE_TOP_BOTTOM = 1
    const val STEREO_MODE_LEFT_RIGHT = 2
}
