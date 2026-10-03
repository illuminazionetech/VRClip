package com.illuminazionetech.vrclip.player.stereo

import com.illuminazionetech.vrclip.util.PreferenceUtil
import com.illuminazionetech.vrclip.util.PreferenceUtil.getString
import com.illuminazionetech.vrclip.util.STEREO_LIVE_MODEL
import com.illuminazionetech.vrclip.util.isQuestDevice

/**
 * Which [DepthModel] live conversion uses on this device. It starts with [DepthModel.Live] and
 * moves to [DepthModel.Full], the resolution the model was trained at, once a session shows the
 * device has the headroom for it; a session where the full model falls behind moves it back.
 * Headsets get a larger budget than phones, since nothing else on a phone screen competes with the
 * depth for attention while on a headset the 3D is the whole point.
 */
object LiveModelPolicy {

    fun choose(): DepthModel =
        DepthModel.entries.firstOrNull { it.name == STEREO_LIVE_MODEL.getString("") }
            ?: DepthModel.Live

    /** Updates the choice from the average inference time of a live session with [model]. */
    fun record(model: DepthModel, averageMillis: Float, headset: Boolean = isQuestDevice()) {
        val next =
            when (model) {
                // The full model costs about twice as much.
                DepthModel.Live ->
                    if (averageMillis <= if (headset) 34f else 24f) DepthModel.Full else null
                DepthModel.Full ->
                    if (averageMillis > if (headset) 85f else 70f) DepthModel.Live else null
            }
        if (next != null) PreferenceUtil.encodeString(STEREO_LIVE_MODEL, next.name)
    }

    fun reset() {
        PreferenceUtil.encodeString(STEREO_LIVE_MODEL, "")
    }
}
