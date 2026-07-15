package com.xrclip.download

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.xrclip.App.Companion.applicationScope
import com.xrclip.App.Companion.context
import com.xrclip.App.Companion.startService
import com.xrclip.App.Companion.stopService
import com.xrclip.R
import com.xrclip.database.objects.CommandTemplate
import com.xrclip.util.COMMAND_DIRECTORY
import com.xrclip.util.DownloadUtil
import com.xrclip.util.FileUtil
import com.xrclip.util.NotificationUtil
import com.xrclip.util.PreferenceUtil.getString
import com.xrclip.util.makeToast
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Tracks the lifecycle of custom-command (`yt-dlp` template) tasks and keeps the foreground
 * service bound while any task or download is active. This is the surviving half of the old
 * `Downloader` singleton after the dead single-download orchestration flow (superseded by
 * [DownloaderV2]) was removed.
 */
object CommandTaskManager {

    sealed class State {
        data object Idle : State()

        data object Updating : State()
    }

    data class CustomCommandTask(
        val template: CommandTemplate,
        val url: String,
        val output: String,
        val state: State,
        val currentLine: String,
    ) {
        fun toKey() = makeKey(url, template.name)

        sealed class State {
            data class Error(val errorReport: String) : State()

            object Completed : State()

            object Canceled : State()

            data class Running(val progress: Float) : State()
        }

        override fun hashCode(): Int {
            return (this.url + this.template.name + this.template.template).hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CustomCommandTask

            if (template != other.template) return false
            if (url != other.url) return false
            if (output != other.output) return false
            if (state != other.state) return false
            if (currentLine != other.currentLine) return false

            return true
        }

        fun onCopyLog(clipboardManager: ClipboardManager) {
            clipboardManager.setText(AnnotatedString(output))
        }

        fun onRestart() {
            applicationScope.launch(Dispatchers.IO) {
                DownloadUtil.executeCommandInBackground(url, template)
            }
        }

        fun onCopyError(clipboardManager: ClipboardManager) {
            clipboardManager.setText(AnnotatedString(currentLine))
            makeToast(R.string.error_copied)
        }

        fun onCancel() {
            toKey().run {
                YoutubeDL.destroyProcessById(this)
                onProcessCanceled(this)
            }
        }
    }

    private val mutableDownloaderState: MutableStateFlow<State> = MutableStateFlow(State.Idle)
    private val mutableProcessCount = MutableStateFlow(0)

    val mutableTaskList = mutableStateMapOf<String, CustomCommandTask>()

    val downloaderState = mutableDownloaderState.asStateFlow()
    val processCount = mutableProcessCount.asStateFlow()

    init {
        applicationScope.launch {
            downloaderState
                .combine(processCount) { state, cnt ->
                    if (cnt > 0) true
                    else
                        when (state) {
                            is State.Idle -> false
                            else -> true
                        }
                }
                .collect { if (it) startService() else stopService() }
        }
    }

    fun makeKey(url: String, templateName: String): String = "${templateName}_$url"

    fun onTaskStarted(template: CommandTemplate, url: String) =
        CustomCommandTask(
                template = template,
                url = url,
                output = "",
                state = CustomCommandTask.State.Running(0f),
                currentLine = "",
            )
            .run { mutableTaskList.put(this.toKey(), this) }

    fun updateTaskOutput(template: CommandTemplate, url: String, line: String, progress: Float) {
        val key = makeKey(url, template.name)
        val oldValue = mutableTaskList[key] ?: return
        val newValue =
            oldValue.run {
                copy(
                    output = output + line + "\n",
                    currentLine = line,
                    state = CustomCommandTask.State.Running(progress),
                )
            }
        mutableTaskList[key] = newValue
    }

    fun onTaskEnded(template: CommandTemplate, url: String, response: String? = null) {
        val key = makeKey(url, template.name)
        NotificationUtil.finishNotification(
            notificationId = key.toNotificationId(),
            title = key,
            text = context.getString(R.string.status_completed),
        )
        mutableTaskList.run {
            val oldValue = get(key) ?: return
            val newValue =
                oldValue.copy(state = CustomCommandTask.State.Completed).run {
                    response?.let { copy(output = response) } ?: this
                }
            this[key] = newValue
        }
        FileUtil.scanDownloadDirectoryToMediaLibrary(COMMAND_DIRECTORY.getString())
    }

    fun onProcessStarted() = mutableProcessCount.update { it + 1 }

    fun onProcessEnded() = mutableProcessCount.update { it - 1 }

    fun onProcessCanceled(taskId: String) =
        mutableTaskList.run {
            get(taskId)?.let { this.put(taskId, it.copy(state = CustomCommandTask.State.Canceled)) }
        }

    fun onTaskError(errorReport: String, template: CommandTemplate, url: String) =
        mutableTaskList.run {
            val key = makeKey(url, template.name)
            NotificationUtil.notifyError(
                title = "",
                notificationId = key.toNotificationId(),
                report = errorReport,
            )
            val oldValue = mutableTaskList[key] ?: return
            mutableTaskList[key] =
                oldValue.copy(
                    state = CustomCommandTask.State.Error(errorReport),
                    currentLine = errorReport,
                    output = oldValue.output + "\n" + errorReport,
                )
        }

    fun updateState(state: State) = mutableDownloaderState.update { state }

    fun executeCommandWithUrl(url: String) =
        applicationScope.launch(Dispatchers.IO) { DownloadUtil.executeCommandInBackground(url) }

    fun String.toNotificationId(): Int = this.hashCode()
}
