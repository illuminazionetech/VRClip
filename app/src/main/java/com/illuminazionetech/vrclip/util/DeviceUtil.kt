package com.illuminazionetech.vrclip.util

import android.os.Build

/** True when running on a Meta Quest headset (Horizon OS), vs. a regular Android phone/tablet. */
fun isQuestDevice(): Boolean =
    Build.MANUFACTURER.contains("Oculus", ignoreCase = true) ||
        Build.MANUFACTURER.contains("Meta", ignoreCase = true) ||
        Build.MODEL.contains("Quest", ignoreCase = true)
