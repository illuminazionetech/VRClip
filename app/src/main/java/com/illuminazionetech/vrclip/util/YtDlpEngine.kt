package com.illuminazionetech.vrclip.util

import com.illuminazionetech.vrclip.App
import com.illuminazionetech.vrclip.util.PreferenceUtil.getBoolean
import com.illuminazionetech.vrclip.util.PreferenceUtil.getLong
import com.illuminazionetech.vrclip.util.PreferenceUtil.getString
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tracks the lifecycle of the bundled yt-dlp engine so that downloads never race its
 * initialization or an in-flight binary update. Downloads call [awaitReady] before executing;
 * the UI observes [state] to surface what the engine is doing.
 */
object YtDlpEngine {

    sealed interface State {
        /** [YoutubeDL.init] has not completed yet. */
        data object Initializing : State

        /** A yt-dlp binary update is in progress. */
        data object Updating : State

        /** The engine is usable. [version] is the yt-dlp version currently installed. */
        data class Ready(val version: String?) : State

        /** [YoutubeDL.init] threw: the engine is unusable until the app restarts. */
        data class InitFailed(val throwable: Throwable) : State
    }

    sealed interface UpdateResult {
        data class Updated(val version: String?) : UpdateResult

        data object UpToDate : UpdateResult

        /** Skipped because auto-update is disabled or the update interval has not elapsed. */
        data object Skipped : UpdateResult

        /** Skipped because the active network is metered and metered downloads are disabled. */
        data object Postponed : UpdateResult

        data class Failed(val throwable: Throwable) : UpdateResult
    }

    private val mutableState = MutableStateFlow<State>(State.Initializing)
    val state: StateFlow<State> = mutableState.asStateFlow()

    private val updateMutex = Mutex()

    fun notifyInitialized() {
        mutableState.value = State.Ready(currentVersionOrNull())
    }

    fun notifyInitFailed(throwable: Throwable) {
        mutableState.value = State.InitFailed(throwable)
    }

    /**
     * Suspends until the engine has finished initializing and no binary update is running. The
     * engine may still be unusable afterwards if initialization failed; in that case yt-dlp
     * itself reports the error to the caller.
     */
    suspend fun awaitReady() {
        state.first { it is State.Ready || it is State.InitFailed }
    }

    /**
     * Applies the auto-update policy and runs a yt-dlp update when it is due.
     *
     * The first update after install always runs (regardless of the metered-network setting,
     * the payload is a few megabytes) because the bundled binary ages with every release and a
     * stale yt-dlp is the most common cause of extraction failures. Afterwards updates respect
     * the user's auto-update switch, the update interval, and the metered-network setting.
     */
    suspend fun runAutoUpdateIfNeeded(): UpdateResult {
        val neverUpdated = YT_DLP_VERSION.getString().isEmpty()
        if (!neverUpdated) {
            if (!YT_DLP_AUTO_UPDATE.getBoolean()) return UpdateResult.Skipped
            val lastUpdateTime = YT_DLP_UPDATE_TIME.getLong()
            if (System.currentTimeMillis() < lastUpdateTime + YT_DLP_UPDATE_INTERVAL.getLong()) {
                return UpdateResult.Skipped
            }
            if (!PreferenceUtil.isNetworkAvailableForDownload()) return UpdateResult.Postponed
        }
        return update()
    }

    /** Runs a yt-dlp binary update now, keeping [state] in sync. */
    suspend fun update(): UpdateResult =
        updateMutex.withLock {
            awaitReady()
            if (state.value is State.InitFailed) {
                return@withLock UpdateResult.Failed(
                    (state.value as State.InitFailed).throwable
                )
            }
            mutableState.value = State.Updating
            try {
                val status = UpdateUtil.updateYtDlp()
                val version = currentVersionOrNull()
                mutableState.value = State.Ready(version)
                if (status == YoutubeDL.UpdateStatus.DONE) UpdateResult.Updated(version)
                else UpdateResult.UpToDate
            } catch (throwable: Throwable) {
                // The previously installed (or bundled) binary stays in place, so the engine
                // remains usable even though the update failed.
                mutableState.value = State.Ready(currentVersionOrNull())
                UpdateResult.Failed(throwable)
            }
        }

    private fun currentVersionOrNull(): String? =
        runCatching { YoutubeDL.getInstance().version(App.context) }.getOrNull()
}
