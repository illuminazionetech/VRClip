package com.illuminazionetech.vrclip.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illuminazionetech.vrclip.download.CommandTaskManager
import com.illuminazionetech.vrclip.util.PreferenceUtil
import com.illuminazionetech.vrclip.util.PreferenceUtil.getBoolean
import com.illuminazionetech.vrclip.util.PreferenceUtil.getLong
import com.illuminazionetech.vrclip.util.PreferenceUtil.getString
import com.illuminazionetech.vrclip.util.UpdateUtil
import com.illuminazionetech.vrclip.util.YT_DLP_AUTO_UPDATE
import com.illuminazionetech.vrclip.util.YT_DLP_UPDATE_INTERVAL
import com.illuminazionetech.vrclip.util.YT_DLP_UPDATE_TIME
import com.illuminazionetech.vrclip.util.YT_DLP_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun YtdlpUpdater() {

    val downloaderState by CommandTaskManager.downloaderState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (downloaderState !is CommandTaskManager.State.Idle) return@LaunchedEffect

        if (!YT_DLP_AUTO_UPDATE.getBoolean() && YT_DLP_VERSION.getString().isNotEmpty())
            return@LaunchedEffect

        if (!PreferenceUtil.isNetworkAvailableForDownload()) {
            return@LaunchedEffect
        }

        val lastUpdateTime = YT_DLP_UPDATE_TIME.getLong()
        val currentTime = System.currentTimeMillis()

        if (currentTime < lastUpdateTime + YT_DLP_UPDATE_INTERVAL.getLong()) {
            return@LaunchedEffect
        }

        runCatching {
                CommandTaskManager.updateState(state = CommandTaskManager.State.Updating)
                withContext(Dispatchers.IO) { UpdateUtil.updateYtDlp() }
            }
            .onFailure { it.printStackTrace() }
        CommandTaskManager.updateState(state = CommandTaskManager.State.Idle)
    }
}
