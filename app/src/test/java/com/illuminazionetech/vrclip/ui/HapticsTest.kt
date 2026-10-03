package com.illuminazionetech.vrclip.ui

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.illuminazionetech.vrclip.ui.common.Haptic
import com.illuminazionetech.vrclip.ui.common.toHaptic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HapticsTest {

    @Test
    fun composeTypesKeepTheirMeaning() {
        assertEquals(Haptic.Tick, HapticFeedbackType.SegmentFrequentTick.toHaptic())
        assertEquals(Haptic.Step, HapticFeedbackType.SegmentTick.toHaptic())
        assertEquals(Haptic.ToggleOn, HapticFeedbackType.ToggleOn.toHaptic())
        assertEquals(Haptic.ToggleOff, HapticFeedbackType.ToggleOff.toHaptic())
        assertEquals(Haptic.GestureStart, HapticFeedbackType.GestureThresholdActivate.toHaptic())
        assertEquals(Haptic.GestureEnd, HapticFeedbackType.GestureEnd.toHaptic())
        assertEquals(Haptic.LongPress, HapticFeedbackType.LongPress.toHaptic())
        assertEquals(Haptic.Confirm, HapticFeedbackType.Confirm.toHaptic())
        assertEquals(Haptic.Reject, HapticFeedbackType.Reject.toHaptic())
    }

    @Test
    fun keysAreTapsAndTextHandlesStayWithTheSystem() {
        assertEquals(Haptic.Tap, HapticFeedbackType.VirtualKey.toHaptic())
        assertEquals(Haptic.Tap, HapticFeedbackType.KeyboardTap.toHaptic())
        assertEquals(Haptic.Tap, HapticFeedbackType.ContextClick.toHaptic())
        assertNull(HapticFeedbackType.TextHandleMove.toHaptic())
    }
}
