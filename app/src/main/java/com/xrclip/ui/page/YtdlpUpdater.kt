package com.xrclip.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xrclip.download.CommandTaskManager
import com.xrclip.util.PreferenceUtil
import com.xrclip.util.PreferenceUtil.getBoolean
import com.xrclip.util.PreferenceUtil.getLong
import com.xrclip.util.PreferenceUtil.getString
import com.xrclip.util.UpdateUtil
import com.xrclip.util.YT_DLP_AUTO_UPDATE
import com.xrclip.util.YT_DLP_UPDATE_INTERVAL
import com.xrclip.util.YT_DLP_UPDATE_TIME
import com.xrclip.util.YT_DLP_VERSION
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
