package com.illuminazionetech.vrclip.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerUiStateTest {

    private val flat = PlayerUiState(frame = FrameInfo(1920, 1080))

    @Test
    fun liveOnTheHeadsetIsSideBySideStereoFromTheStart() {
        for (live in listOf(Live3dState.Starting, Live3dState.On)) {
            val state = flat.copy(liveOnScreen = false, live3d = live)
            assertEquals(ProjectionMode.SBS_3D, state.renderProjection)
            // The stereo layer shows one 16:9 view per eye.
            assertEquals(
                16f / 9f,
                ProjectionMode.eyeAspectRatio(state.renderProjection, state.renderFrameAspect),
                0.01f,
            )
            // Never the phone format, with the depth map under the picture.
            assertFalse(state.depthPacked)
        }
    }

    @Test
    fun liveOnAScreenCarriesTheDepthMap() {
        val state = flat.copy(liveOnScreen = true, live3d = Live3dState.On)
        assertTrue(state.depthPacked)
        assertTrue(state.usesGlRenderer)
        assertEquals(ProjectionMode.FLAT, state.renderProjection)
    }

    @Test
    fun withoutLiveConversionTheFilePlaysAsItIs() {
        val headset = flat.copy(liveOnScreen = false)
        assertEquals(ProjectionMode.FLAT, headset.renderProjection)
        assertFalse(headset.depthPacked)
        val failed = flat.copy(liveOnScreen = false, live3d = Live3dState.Failed)
        assertEquals(ProjectionMode.FLAT, failed.renderProjection)
    }
}
