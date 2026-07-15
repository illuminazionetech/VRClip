package com.xrclip.player

/**
 * How a video's frame should be projected/interpreted during playback. Detected by
 * [ProjectionDetector] from filename conventions and aspect ratio, or forced by the user via a
 * per-video override (see [com.xrclip.database.objects.DownloadedVideoInfo.projectionOverride]).
 */
enum class ProjectionMode {
    /** Standard flat video, rendered as-is. */
    FLAT,

    /** Full 360° equirectangular sphere, single (non-stereo) eye. */
    MONO_360,

    /** 360° equirectangular, stereo pair stacked top-over-bottom in a single frame. */
    STEREO_360_TB,

    /** 360° equirectangular, stereo pair side-by-side in a single frame. */
    STEREO_360_LR,

    /** 180° half-dome, single (non-stereo) eye. */
    MONO_180,

    /** 180° half-dome, stereo pair stacked top-over-bottom. */
    STEREO_180_TB,

    /** 180° half-dome, stereo pair side-by-side. */
    STEREO_180_LR,

    /** Flat stereoscopic 3D, side-by-side pair (not 360/180). */
    SBS_3D,

    /** Flat stereoscopic 3D, over-under (top-bottom) pair (not 360/180). */
    OU_3D;

    val is360: Boolean
        get() = this == MONO_360 || this == STEREO_360_TB || this == STEREO_360_LR

    val is180: Boolean
        get() = this == MONO_180 || this == STEREO_180_TB || this == STEREO_180_LR

    val isStereo: Boolean
        get() =
            this == STEREO_360_TB ||
                this == STEREO_360_LR ||
                this == STEREO_180_TB ||
                this == STEREO_180_LR ||
                this == SBS_3D ||
                this == OU_3D

    /** True if this mode needs the custom GL/spatial renderer rather than a plain flat player. */
    val requiresImmersiveRendering: Boolean
        get() = this != FLAT

    companion object {
        fun fromStorageKey(key: String?): ProjectionMode? =
            key?.let { k -> entries.firstOrNull { it.name == k } }
    }
}
