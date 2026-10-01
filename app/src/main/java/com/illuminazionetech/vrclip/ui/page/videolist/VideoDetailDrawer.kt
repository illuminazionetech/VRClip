package com.illuminazionetech.vrclip.ui.page.videolist

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.database.objects.DownloadedVideoInfo
import com.illuminazionetech.vrclip.player.ProjectionDetector
import com.illuminazionetech.vrclip.player.ProjectionMenu
import com.illuminazionetech.vrclip.player.ProjectionMode
import com.illuminazionetech.vrclip.player.displayName
import com.illuminazionetech.vrclip.player.stereo.StereoConversionWorker
import com.illuminazionetech.vrclip.ui.common.HapticFeedback.slightHapticFeedback
import com.illuminazionetech.vrclip.ui.common.rememberTextClipboard
import com.illuminazionetech.vrclip.ui.component.MediaImage
import com.illuminazionetech.vrclip.ui.component.VRClipModalBottomSheet
import com.illuminazionetech.vrclip.util.AUDIO_REGEX
import com.illuminazionetech.vrclip.util.DatabaseUtil
import com.illuminazionetech.vrclip.util.FileUtil
import com.illuminazionetech.vrclip.util.makeToast
import kotlinx.coroutines.launch

/**
 * Details and actions for one library entry. [onConvertTo3D] is shown only for flat video files
 * that exist on disk, and only when a converter is available (the caller passes null otherwise).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailDrawer(
    sheetState: SheetState,
    info: DownloadedVideoInfo,
    isFileAvailable: Boolean = true,
    onPlay: () -> Unit = {},
    onConvertTo3D: (() -> Unit)? = null,
    onDismissRequest: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val view = LocalView.current
    val context = LocalContext.current
    val clipboard = rememberTextClipboard()
    val scope = rememberCoroutineScope()
    var showProjectionMenu by remember { mutableStateOf(false) }

    val isAudio = remember(info) { info.videoPath.contains(Regex(AUDIO_REGEX)) }
    val projection =
        remember(info) {
            ProjectionMode.fromStorageKey(info.projectionOverride)
                ?: ProjectionDetector.detectProjection(info.videoPath)
        }
    val shareTitle = stringResource(id = R.string.share)
    val conversion by
        remember(info.id) { StereoConversionWorker.status(context, info.id) }
            .collectAsStateWithLifecycle(initialValue = StereoConversionWorker.Status.None)
    val converting = conversion is StereoConversionWorker.Status.Running

    VRClipModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismissRequest,
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MediaImage(imageModel = info.thumbnailUrl, isAudio = isAudio)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                SelectionContainer {
                    Text(
                        text = info.videoTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (info.videoAuthor != "playlist" && info.videoAuthor != "null") {
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = info.videoAuthor,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = {
                    clipboard.setText(info.videoUrl)
                    context.makeToast(R.string.link_copied)
                },
                label = { Text(stringResource(R.string.copy_link)) },
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = {
                    onDismissRequest()
                    uriHandler.openUri(info.videoUrl)
                },
                label = { Text(stringResource(R.string.open_url)) },
                leadingIcon = { Icon(Icons.Rounded.Link, null, Modifier.size(18.dp)) },
            )
        }

        if (isFileAvailable && !isAudio) {
            Text(
                modifier = Modifier.padding(top = 16.dp),
                text = stringResource(R.string.player_projection) + ": " + projection.displayName(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ConversionStatus(
            status = conversion,
            onCancel = { StereoConversionWorker.cancel(context, info.id) },
        )

        Spacer(Modifier.height(20.dp))

        if (isFileAvailable) {
            Button(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                onClick = {
                    view.slightHapticFeedback()
                    onDismissRequest()
                    if (isAudio) {
                        FileUtil.openFile(path = info.videoPath) {
                            makeToast(R.string.file_unavailable)
                        }
                    } else {
                        onPlay()
                    }
                },
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.play), style = MaterialTheme.typography.titleSmall)
            }
        } else {
            Button(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                onClick = {
                    view.slightHapticFeedback()
                    context.startActivity(
                        Intent().apply {
                            action = Intent.ACTION_SEND
                            setPackage(context.packageName)
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, info.videoUrl)
                        }
                    )
                },
            ) {
                Icon(Icons.Rounded.FileDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.redownload))
            }
        }

        Spacer(Modifier.height(12.dp))

        DetailActions {
            if (isFileAvailable && !isAudio) {
                DetailAction(Icons.Rounded.Public, stringResource(R.string.player_projection)) {
                    showProjectionMenu = true
                }
                if (
                    onConvertTo3D != null &&
                        projection == ProjectionMode.FLAT &&
                        !converting &&
                        !info.videoPath.startsWith("content://")
                ) {
                    DetailAction(Icons.Rounded.ViewInAr, stringResource(R.string.convert_to_3d)) {
                        onDismissRequest()
                        onConvertTo3D()
                    }
                }
                DetailAction(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    stringResource(R.string.player_open_externally),
                ) {
                    FileUtil.openFile(path = info.videoPath) {
                        makeToast(R.string.file_unavailable)
                    }
                }
            }
            if (isFileAvailable) {
                DetailAction(Icons.Rounded.Share, shareTitle) {
                    FileUtil.createIntentForSharingFile(info.videoPath)?.runCatching {
                        context.startActivity(Intent.createChooser(this, shareTitle))
                    }
                }
            }
            DetailAction(
                Icons.Rounded.Delete,
                stringResource(R.string.remove),
                destructive = true,
            ) {
                view.slightHapticFeedback()
                onDismissRequest()
                onDelete()
            }
        }
    }

    if (showProjectionMenu) {
        ProjectionMenu(
            current = ProjectionMode.fromStorageKey(info.projectionOverride),
            onDismiss = { showProjectionMenu = false },
            onSelect = { mode ->
                showProjectionMenu = false
                scope.launch { DatabaseUtil.updateProjectionOverride(info.videoPath, mode?.name) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailActions(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun DetailAction(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    if (destructive) {
        OutlinedButton(onClick = onClick) {
            Icon(icon, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    } else {
        FilledTonalButton(onClick = onClick) {
            Icon(icon, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}

/** Progress (with a stop button) or the outcome of a 2D to 3D conversion of this video. */
@Composable
private fun ConversionStatus(status: StereoConversionWorker.Status, onCancel: () -> Unit) {
    AnimatedVisibility(
        visible =
            status is StereoConversionWorker.Status.Running ||
                status is StereoConversionWorker.Status.Failed
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            shape = MaterialTheme.shapes.large,
            color =
                if (status is StereoConversionWorker.Status.Failed)
                    MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                when (status) {
                    is StereoConversionWorker.Status.Running -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text =
                                    if (status.progress >= 0f)
                                        stringResource(
                                            R.string.stereo_status_running,
                                            (status.progress * 100).toInt(),
                                        )
                                    else stringResource(R.string.stereo_status_queued),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onCancel) {
                                Text(stringResource(R.string.stereo_convert_cancel))
                            }
                        }
                        if (status.progress >= 0f) {
                            LinearWavyProgressIndicator(
                                progress = { status.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is StereoConversionWorker.Status.Failed ->
                        Text(
                            text =
                                stringResource(
                                    when (status.reason) {
                                        StereoConversionWorker.Failure.Missing ->
                                            R.string.stereo_error_missing
                                        StereoConversionWorker.Failure.NotWritable ->
                                            R.string.stereo_error_not_writable
                                        StereoConversionWorker.Failure.NoSpace ->
                                            R.string.stereo_error_space
                                        StereoConversionWorker.Failure.Model ->
                                            R.string.stereo_model_failed
                                        StereoConversionWorker.Failure.Encoder ->
                                            R.string.stereo_error_encoder
                                        StereoConversionWorker.Failure.Failed ->
                                            R.string.stereo_error_failed
                                    }
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    else -> Unit
                }
            }
        }
    }
}
