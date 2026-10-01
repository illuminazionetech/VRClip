package com.illuminazionetech.vrclip.player.stereo

import com.illuminazionetech.vrclip.util.PreferenceUtil
import com.illuminazionetech.vrclip.util.PreferenceUtil.getFloat
import com.illuminazionetech.vrclip.util.STEREO_POP_OUT
import com.illuminazionetech.vrclip.util.STEREO_STRENGTH

/**
 * How strong the synthesized 3D is. [strength] is the total parallax range as a fraction of the
 * picture width (the distance between the nearest and the farthest point, left eye to right eye);
 * [popOut] is the share of that range placed in front of the screen, the rest recedes behind it.
 * The defaults keep most of the scene behind the screen, which is comfortable for long viewing and
 * avoids objects being cut by the frame edges.
 */
data class StereoSettings(
    val strength: Float = DEFAULT_STRENGTH,
    val popOut: Float = DEFAULT_POP_OUT,
) {

    /** Depth (0 far, 1 near) that lands exactly on the screen plane. */
    val convergence: Float
        get() = 1f - popOut

    fun save() {
        PreferenceUtil.encodeFloat(STEREO_STRENGTH, strength)
        PreferenceUtil.encodeFloat(STEREO_POP_OUT, popOut)
    }

    companion object {
        const val DEFAULT_STRENGTH = 0.03f
        const val DEFAULT_POP_OUT = 0.4f
        val STRENGTH_RANGE = 0.01f..0.05f
        val POP_OUT_RANGE = 0f..0.6f

        fun load() =
            StereoSettings(
                strength = STEREO_STRENGTH.getFloat(DEFAULT_STRENGTH).coerceIn(STRENGTH_RANGE),
                popOut = STEREO_POP_OUT.getFloat(DEFAULT_POP_OUT).coerceIn(POP_OUT_RANGE),
            )
    }
}
