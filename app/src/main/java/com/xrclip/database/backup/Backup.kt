package com.xrclip.database.backup

import com.xrclip.database.objects.CommandTemplate
import com.xrclip.database.objects.DownloadedVideoInfo
import com.xrclip.database.objects.OptionShortcut
import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val templates: List<CommandTemplate>? = null,
    val shortcuts: List<OptionShortcut>? = null,
    val downloadHistory: List<DownloadedVideoInfo>? = null,
)
