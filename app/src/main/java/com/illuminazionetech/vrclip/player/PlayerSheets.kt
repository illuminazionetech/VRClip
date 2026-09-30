package com.illuminazionetech.vrclip.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.player.gl.StereoOutputMode
import com.illuminazionetech.vrclip.player.stereo.ConvertTo3dDialog
import com.illuminazionetech.vrclip.player.stereo.DepthModelDialog
import com.illuminazionetech.vrclip.ui.component.DrawerSheetSubtitle
import com.illuminazionetech.vrclip.ui.component.VRClipModalBottomSheet
import com.illuminazionetech.vrclip.util.DatabaseUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PlayerSheet {
    Projection,
    Tracks,
    Speed,
    StereoOutput,
    More,
    Delete,
    Convert,
}

private val speedPresets = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)

@Composable
internal fun PlayerSheets(
    sheet: PlayerSheet?,
    state: PlayerUiState,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onOpenExternally: () -> Unit,
    onOpenSheet: (PlayerSheet) -> Unit,
    onDeleted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    when (sheet) {
        PlayerSheet.Projection ->
            PlayerBottomSheet(onDismiss) {
                DrawerSheetSubtitle(text = stringResource(R.string.player_projection))
                Column(Modifier.fillMaxWidth()) {
                    val detected = state.detection.mode.displayName()
                    ChoiceRow(
                        label = stringResource(R.string.player_projection_detected, detected),
                        supporting = detectionSourceLabel(state.detection.source),
                        selected = state.projectionOverride == null,
                        onClick = {
                            viewModel.setProjectionOverride(null)
                            onDismiss()
                        },
                    )
                    ProjectionMode.entries.forEach { mode ->
                        ChoiceRow(
                            label = mode.displayName(),
                            selected = state.projectionOverride == mode,
                            onClick = {
                                viewModel.setProjectionOverride(mode)
                                onDismiss()
                            },
                        )
                    }
                }
            }

        PlayerSheet.Tracks ->
            PlayerBottomSheet(onDismiss) {
                Column(Modifier.fillMaxWidth()) {
                    if (state.audioTracks.size > 1) {
                        DrawerSheetSubtitle(text = stringResource(R.string.player_audio))
                        state.audioTracks.forEach { option ->
                            ChoiceRow(
                                label = option.label,
                                selected = option.selected,
                                onClick = { viewModel.selectTrack(option) },
                            )
                        }
                    }
                    DrawerSheetSubtitle(text = stringResource(R.string.player_subtitles))
                    ChoiceRow(
                        label = stringResource(R.string.player_subtitles_off),
                        selected = state.subtitlesOff,
                        onClick = viewModel::disableSubtitles,
                    )
                    state.textTracks.forEach { option ->
                        ChoiceRow(
                            label = option.label,
                            selected = option.selected,
                            onClick = { viewModel.selectTrack(option) },
                        )
                    }
                }
            }

        PlayerSheet.Speed ->
            PlayerBottomSheet(onDismiss) {
                DrawerSheetSubtitle(text = stringResource(R.string.player_speed))
                var value by remember { mutableFloatStateOf(state.speed) }
                Text(
                    text = stringResource(R.string.player_speed_value, speedLabel(value)),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Slider(
                    value = value,
                    onValueChange = { value = (Math.round(it * 20) / 20f) },
                    onValueChangeFinished = { viewModel.setSpeed(value) },
                    valueRange = 0.25f..3f,
                )
                SpeedPresets(
                    current = value,
                    onSelect = {
                        value = it
                        viewModel.setSpeed(it)
                    },
                )
            }

        PlayerSheet.StereoOutput ->
            PlayerBottomSheet(onDismiss) {
                DrawerSheetSubtitle(text = stringResource(R.string.player_stereo_output))
                Column(Modifier.fillMaxWidth()) {
                    val options =
                        if (state.renderProjection.isStereo) StereoOutputMode.entries
                        else listOf(StereoOutputMode.SingleEye, StereoOutputMode.SplitScreen)
                    options.forEach { mode ->
                        ChoiceRow(
                            label = mode.label(),
                            selected = state.stereoOutput == mode,
                            onClick = {
                                viewModel.setStereoOutput(mode)
                                onDismiss()
                            },
                        )
                    }
                }
            }

        PlayerSheet.More ->
            PlayerBottomSheet(onDismiss) {
                Column(Modifier.fillMaxWidth()) {
                    val canConvert =
                        state.libraryId != null &&
                            state.sourceProjection == ProjectionMode.FLAT &&
                            state.videoPath?.startsWith("content://") == false
                    if (canConvert) {
                        ActionRow(Icons.Rounded.ViewInAr, stringResource(R.string.convert_to_3d)) {
                            viewModel.pause()
                            onOpenSheet(PlayerSheet.Convert)
                        }
                    }
                    if (state.libraryId != null) {
                        ActionRow(Icons.Rounded.Share, stringResource(R.string.share)) {
                            onDismiss()
                            onShare()
                        }
                        ActionRow(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            stringResource(R.string.player_open_externally),
                        ) {
                            onDismiss()
                            viewModel.pause()
                            onOpenExternally()
                        }
                        ActionRow(Icons.Rounded.Delete, stringResource(R.string.delete)) {
                            onOpenSheet(PlayerSheet.Delete)
                        }
                    }
                }
            }

        PlayerSheet.Delete ->
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                title = { Text(stringResource(R.string.player_delete_title)) },
                text = { Text(stringResource(R.string.player_delete_desc)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val id = state.libraryId ?: return@TextButton
                            viewModel.pause()
                            scope.launch {
                                runCatching {
                                    DatabaseUtil.deleteInfoList(
                                        listOf(DatabaseUtil.getInfoById(id)),
                                        deleteFile = true,
                                    )
                                }
                                onDismiss()
                                onDeleted()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                },
            )

        PlayerSheet.Convert -> {
            val id = state.libraryId
            if (id == null) onDismiss()
            else ConvertTo3dDialog(videoId = id, onDismiss = onDismiss)
        }

        null -> Unit
    }
}

@Composable
private fun PlayerBottomSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    VRClipModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            content()
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    supporting: String? = null,
) {
    ListItem(
        modifier =
            Modifier.fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(label)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth().selectable(selected = false, onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedPresets(current: Float, onSelect: (Float) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        speedPresets.forEach { speed ->
            FilterChip(
                selected = current == speed,
                onClick = { onSelect(speed) },
                label = { Text(stringResource(R.string.player_speed_value, speedLabel(speed))) },
            )
        }
    }
}

@Composable
private fun detectionSourceLabel(source: DetectionSource): String =
    stringResource(
        when (source) {
            DetectionSource.Metadata -> R.string.player_detected_metadata
            DetectionSource.FileName -> R.string.player_detected_filename
            DetectionSource.AspectRatio -> R.string.player_detected_aspect
            DetectionSource.Default -> R.string.player_detected_default
        }
    )

/** Model download prompt and the "live 3D unavailable" notice. */
@Composable
internal fun Live3dMessages(state: PlayerUiState, viewModel: PlayerViewModel) {
    if (state.live3d == Live3dState.NeedsModel) {
        DepthModelDialog(
            onDismiss = viewModel::dismissLive3dMessage,
            onReady = {
                viewModel.dismissLive3dMessage()
                viewModel.setLive3d(true)
            },
        )
    }
    var showFailure by remember { mutableStateOf(false) }
    LaunchedEffect(state.live3d) {
        if (state.live3d == Live3dState.Failed) {
            showFailure = true
            delay(4_000)
            showFailure = false
            viewModel.dismissLive3dMessage()
        }
    }
    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        AnimatedVisibility(
            visible = showFailure,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp, start = 24.dp, end = 24.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.ViewInAr, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.player_live_3d_failed),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}
