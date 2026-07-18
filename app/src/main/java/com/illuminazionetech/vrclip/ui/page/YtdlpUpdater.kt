package com.illuminazionetech.vrclip.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.download.CommandTaskManager
import com.illuminazionetech.vrclip.util.YtDlpEngine
import com.illuminazionetech.vrclip.util.makeToast

/**
 * Runs the yt-dlp auto-update policy once per app start. The gating logic lives in
 * [YtDlpEngine.runAutoUpdateIfNeeded]; this composable only mirrors the engine state into
 * [CommandTaskManager] (which keeps the download service alive during the update) and surfaces
 * outcomes that the user should know about.
 */
@Composable
fun YtdlpUpdater() {
    LaunchedEffect(Unit) {
        if (CommandTaskManager.downloaderState.value !is CommandTaskManager.State.Idle) {
            return@LaunchedEffect
        }

        CommandTaskManager.updateState(state = CommandTaskManager.State.Updating)
        val result = YtDlpEngine.runAutoUpdateIfNeeded()
        CommandTaskManager.updateState(state = CommandTaskManager.State.Idle)

        when (result) {
            is YtDlpEngine.UpdateResult.Postponed -> makeToast(R.string.yt_dlp_update_postponed)
            is YtDlpEngine.UpdateResult.Failed -> {
                result.throwable.printStackTrace()
                makeToast(R.string.yt_dlp_update_fail)
            }
            else -> {}
        }
    }
}
