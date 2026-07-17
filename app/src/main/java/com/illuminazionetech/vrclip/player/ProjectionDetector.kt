package com.illuminazionetech.vrclip.player

/**
 * Best-effort detection of a video's [ProjectionMode]. Almost nothing downloaded through yt-dlp
 * carries embedded spherical-video metadata (the `spherical-video-v2` MP4 box), so this relies on
 * filename conventions (the de facto standard used by 360/VR camera vendors and video sites) with
 * aspect ratio as a secondary, confirmatory signal. When signals disagree or are absent, this
 * defaults to [ProjectionMode.FLAT] — the UI always offers a manual override for cases the
 * heuristic gets wrong.
 */
object ProjectionDetector {

    private val stereoLrTokens = listOf("_lr", "_sbs", "-lr", "-sbs", ".lr.", ".sbs.", "sidebyside")
    private val stereoTbTokens = listOf("_tb", "_ou", "-tb", "-ou", ".tb.", ".ou.", "topbottom")
    private val tag360Tokens = listOf("_360", "-360", ".360.", "360x180")
    private val tag180Tokens = listOf("_180", "-180", ".180.", "vr180", "_vr180")
    private val tag3dTokens = listOf("_3d", "-3d", ".3d.")

    fun detectProjection(filePath: String, width: Int? = null, height: Int? = null): ProjectionMode {
        val name = filePath.substringAfterLast('/').lowercase()

        val has360 = tag360Tokens.any { name.contains(it) }
        val has180 = tag180Tokens.any { name.contains(it) } && !has360
        val hasStereoLr = stereoLrTokens.any { name.contains(it) }
        val hasStereoTb = stereoTbTokens.any { name.contains(it) }
        val has3d = tag3dTokens.any { name.contains(it) }

        val fromFilename =
            when {
                has360 && hasStereoTb -> ProjectionMode.STEREO_360_TB
                has360 && hasStereoLr -> ProjectionMode.STEREO_360_LR
                has360 -> ProjectionMode.MONO_360
                has180 && hasStereoTb -> ProjectionMode.STEREO_180_TB
                has180 && hasStereoLr -> ProjectionMode.STEREO_180_LR
                has180 -> ProjectionMode.MONO_180
                has3d && hasStereoTb -> ProjectionMode.OU_3D
                has3d && hasStereoLr -> ProjectionMode.SBS_3D
                hasStereoTb -> ProjectionMode.OU_3D
                hasStereoLr -> ProjectionMode.SBS_3D
                else -> null
            }

        if (fromFilename != null) return fromFilename

        return fromAspectRatio(width, height)
    }

    /**
     * Secondary/confirmatory signal only: a 2:1 frame is very likely an equirectangular mono 360
     * video (the standard convention); a ~1:1 frame is a plausible stacked-stereo 360 or 180
     * candidate, but far too ambiguous with plain square video to assume without a filename hint,
     * so it is intentionally left as [ProjectionMode.FLAT] here and only offered via the manual
     * override menu.
     */
    private fun fromAspectRatio(width: Int?, height: Int?): ProjectionMode {
        if (width == null || height == null || width <= 0 || height <= 0) return ProjectionMode.FLAT
        val ratio = width.toDouble() / height.toDouble()
        return if (ratio in 1.9..2.15) ProjectionMode.MONO_360 else ProjectionMode.FLAT
    }
}
