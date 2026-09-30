package com.illuminazionetech.vrclip.player

/** How the two views of a stereoscopic frame are packed. */
enum class StereoLayout {
    None,
    LeftRight,
    TopBottom,
}

/**
 * How a video's frame should be projected/interpreted during playback. Detected by
 * [ProjectionDetector] from container metadata, filename conventions and aspect ratio, or forced
 * by the user via a per-video override (see
 * [com.illuminazionetech.vrclip.database.objects.DownloadedVideoInfo.projectionOverride]).
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

    /** 360° or 180°: the frame is wrapped around the viewer instead of shown on a screen. */
    val isSpherical: Boolean
        get() = is360 || is180

    val stereoLayout: StereoLayout
        get() =
            when (this) {
                STEREO_360_LR,
                STEREO_180_LR,
                SBS_3D -> StereoLayout.LeftRight
                STEREO_360_TB,
                STEREO_180_TB,
                OU_3D -> StereoLayout.TopBottom
                else -> StereoLayout.None
            }

    val isStereo: Boolean
        get() = stereoLayout != StereoLayout.None

    /** True if this mode needs the custom GL/spatial renderer rather than a plain flat player. */
    val requiresImmersiveRendering: Boolean
        get() = this != FLAT

    companion object {
        fun fromStorageKey(key: String?): ProjectionMode? =
            key?.let { k -> entries.firstOrNull { it.name == k } }

        /**
         * Aspect ratio (width / height) of one eye's picture for flat content, given the decoded
         * frame size. Stereo files come in two packings: "full" (each eye keeps the source aspect,
         * so a side-by-side 16:9 video is 32:9) and "half" (each eye squeezed into half the
         * frame, so the file stays 16:9). The frame shape tells them apart, the same rule used by
         * most VR players: a side-by-side frame wider than 2.5:1, or an over-under frame taller
         * than 1.2:1, is full packing.
         */
        fun eyeAspectRatio(mode: ProjectionMode, frameAspect: Float): Float =
            when (mode.stereoLayout) {
                StereoLayout.None -> frameAspect
                StereoLayout.LeftRight ->
                    if (frameAspect >= FULL_SBS_MIN_ASPECT) frameAspect / 2f else frameAspect
                StereoLayout.TopBottom ->
                    if (frameAspect <= FULL_OU_MAX_ASPECT) frameAspect * 2f else frameAspect
            }

        const val FULL_SBS_MIN_ASPECT = 2.5f
        const val FULL_OU_MAX_ASPECT = 1.2f
    }
}
