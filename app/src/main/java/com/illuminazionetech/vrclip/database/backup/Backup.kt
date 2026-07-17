package com.illuminazionetech.vrclip.database.backup

import com.illuminazionetech.vrclip.database.objects.CommandTemplate
import com.illuminazionetech.vrclip.database.objects.DownloadedVideoInfo
import com.illuminazionetech.vrclip.database.objects.OptionShortcut
import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val templates: List<CommandTemplate>? = null,
    val shortcuts: List<OptionShortcut>? = null,
    val downloadHistory: List<DownloadedVideoInfo>? = null,
)
