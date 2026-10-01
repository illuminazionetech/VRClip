package com.illuminazionetech.vrclip.ui.page

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.util.AppUpdateManager
import com.illuminazionetech.vrclip.util.AppUpdateManager.Reason
import com.illuminazionetech.vrclip.util.AppUpdateManager.State
import com.illuminazionetech.vrclip.util.PreferenceUtil
import com.illuminazionetech.vrclip.util.toFileSizeText

/**
 * Runs the automatic update check once per launch (when enabled) and shows the update dialog
 * whenever [AppUpdateManager] has something to say, whether the check started here or from the
 * settings page.
 */
@Composable
fun AppUpdater() {
    val state by AppUpdateManager.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (PreferenceUtil.isAutoUpdateEnabled()) AppUpdateManager.check(manual = false)
    }

    when (val current = state) {
        is State.Available,
        is State.Downloading,
        is State.Installing,
        is State.SignatureChanged,
        is State.Failed -> UpdateDialog(current)
        else -> {}
    }
}

@Composable
private fun UpdateDialog(state: State) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val release =
        when (state) {
            is State.Available -> state.release
            is State.Downloading -> state.release
            is State.Installing -> state.release
            is State.SignatureChanged -> state.release
            is State.Failed -> state.release
            else -> null
        }
    val busy = state is State.Downloading || state is State.Installing
    val versionName = remember(release) { release?.let { r -> (r.tagName ?: r.name).orEmpty() } }

    AlertDialog(
        onDismissRequest = { if (!busy) AppUpdateManager.dismiss() },
        icon = {
            Icon(
                imageVector =
                    when (state) {
                        is State.SignatureChanged -> Icons.Rounded.VpnKey
                        is State.Failed -> Icons.Rounded.ErrorOutline
                        is State.Downloading,
                        is State.Installing -> Icons.Rounded.SystemUpdate
                        else -> Icons.Rounded.NewReleases
                    },
                contentDescription = null,
            )
        },
        title = {
            Text(
                when (state) {
                    is State.SignatureChanged -> stringResource(R.string.update_key_changed_title)
                    is State.Failed -> stringResource(R.string.app_update_failed)
                    else -> stringResource(R.string.update_available_title, versionName.orEmpty())
                }
            )
        },
        text = {
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "updateDialogBody",
            ) { current ->
                when (current) {
                    is State.Available ->
                        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                            current.asset.size?.let {
                                Text(
                                    text = stringResource(R.string.update_download_size, it.toFileSizeText()),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                            Text(
                                text = releaseNotes(current.release.body.orEmpty()),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    is State.Downloading ->
                        Column {
                            if (current.progress >= 0f) {
                                LinearWavyProgressIndicator(
                                    progress = { current.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text =
                                    if (current.totalBytes > 0)
                                        "${current.downloadedBytes.toFileSizeText()} / ${current.totalBytes.toFileSizeText()}"
                                    else current.downloadedBytes.toFileSizeText(),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    is State.Installing -> {
                        Column {
                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.update_installing))
                        }
                    }
                    is State.SignatureChanged -> Text(stringResource(R.string.update_key_changed_desc))
                    is State.Failed ->
                        Text(
                            stringResource(
                                when (current.reason) {
                                    Reason.Network -> R.string.update_error_network
                                    Reason.RateLimited -> R.string.update_error_rate_limited
                                    Reason.NoAssetForDevice -> R.string.update_error_no_asset
                                    Reason.Corrupted -> R.string.update_error_corrupted
                                    Reason.InstallBlocked -> R.string.update_error_blocked
                                    Reason.InstallFailed -> R.string.update_error_install
                                }
                            )
                        )
                    else -> {}
                }
            }
        },
        confirmButton = {
            when (state) {
                is State.Available ->
                    // If VRClip may not install apps yet, the system installer asks for that
                    // permission itself when the verified update is handed over.
                    Button(onClick = { AppUpdateManager.downloadAndInstall(context, state) }) {
                        Text(stringResource(R.string.update))
                    }
                is State.Downloading ->
                    TextButton(onClick = { AppUpdateManager.cancelDownload() }) {
                        Text(stringResource(R.string.cancel))
                    }
                is State.SignatureChanged,
                is State.Failed ->
                    Button(
                        onClick = {
                            AppUpdateManager.dismiss()
                            uriHandler.openUri(AppUpdateManager.RELEASES_PAGE_URL)
                        }
                    ) {
                        Text(stringResource(R.string.update_open_download_page))
                    }
                else -> {}
            }
        },
        dismissButton = {
            when (state) {
                is State.Available ->
                    TextButton(onClick = { AppUpdateManager.skip(state) }) {
                        Text(stringResource(R.string.update_skip_version))
                    }
                is State.SignatureChanged,
                is State.Failed ->
                    TextButton(onClick = { AppUpdateManager.dismiss() }) {
                        Text(stringResource(R.string.close))
                    }
                else -> {}
            }
        },
    )
}

/**
 * Minimal rendering of GitHub release notes: headings in bold, list markers as bullets, inline
 * `**bold**` honored, link-only suffixes such as "by @user in https://…" dropped.
 */
internal fun releaseNotes(markdown: String): AnnotatedString = buildAnnotatedString {
    val lines = mutableListOf<String>()
    markdown
        .lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() && !it.startsWith("**Full Changelog**") }
        .forEach { line ->
            // An indented line that does not start a new item continues the one above.
            val wrapped =
                lines.isNotEmpty() && line.first().isWhitespace() && !line.trimStart().matches(listItem)
            if (wrapped) lines[lines.lastIndex] = lines.last() + " " + line.trim() else lines += line
        }
    lines.forEachIndexed { index, raw ->
        val heading = raw.startsWith("#")
        var line = raw.trimStart('#', ' ')
        line = line.replace(Regex(""" by @[\w-]+ in https?://\S+$"""), "")
        line = line.replace(Regex("""^[*-] """), "• ").replace("`", "")
        if (heading) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(line) }
        } else {
            val parts = line.split("**")
            parts.forEachIndexed { i, part ->
                if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) }
                else append(part)
            }
        }
        if (index != lines.lastIndex) append('\n')
    }
}

private val listItem = Regex("""^[*-] .*""")
