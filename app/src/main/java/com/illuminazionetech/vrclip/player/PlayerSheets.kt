package com.illuminazionetech.vrclip.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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
private const val SPEED_MIN = 0.25f
private const val SPEED_MAX = 3f
private const val SPEED_STEP = 0.05f

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
    val haptics = rememberPlayerHaptics()
    when (sheet) {
        PlayerSheet.Projection ->
            PlayerBottomSheet(onDismiss) {
                DrawerSheetSubtitle(text = stringResource(R.string.player_projection))
                val detected = state.detection.mode.displayName()
                val modes = ProjectionMode.entries
                SegmentedGroup {
                    ChoiceRow(
                        index = 0,
                        count = modes.size + 1,
                        label = stringResource(R.string.player_projection_detected, detected),
                        supporting = detectionSourceLabel(state.detection.source),
                        selected = state.projectionOverride == null,
                        onClick = {
                            haptics.tap()
                            viewModel.setProjectionOverride(null)
                            onDismiss()
                        },
                    )
                    modes.forEachIndexed { index, mode ->
                        ChoiceRow(
                            index = index + 1,
                            count = modes.size + 1,
                            label = mode.displayName(),
                            selected = state.projectionOverride == mode,
                            onClick = {
                                haptics.tap()
                                viewModel.setProjectionOverride(mode)
                                onDismiss()
                            },
                        )
                    }
                }
            }

        PlayerSheet.Tracks ->
            PlayerBottomSheet(onDismiss) {
                if (state.audioTracks.size > 1) {
                    DrawerSheetSubtitle(text = stringResource(R.string.player_audio))
                    SegmentedGroup {
                        state.audioTracks.forEachIndexed { index, option ->
                            ChoiceRow(
                                index = index,
                                count = state.audioTracks.size,
                                label = option.label,
                                selected = option.selected,
                                onClick = {
                                    haptics.tap()
                                    viewModel.selectTrack(option)
                                },
                            )
                        }
                    }
                }
                DrawerSheetSubtitle(text = stringResource(R.string.player_subtitles))
                val count = state.textTracks.size + 1
                SegmentedGroup {
                    ChoiceRow(
                        index = 0,
                        count = count,
                        label = stringResource(R.string.player_subtitles_off),
                        selected = state.subtitlesOff,
                        onClick = {
                            haptics.tap()
                            viewModel.disableSubtitles()
                        },
                    )
                    state.textTracks.forEachIndexed { index, option ->
                        ChoiceRow(
                            index = index + 1,
                            count = count,
                            label = option.label,
                            selected = option.selected,
                            onClick = {
                                haptics.tap()
                                viewModel.selectTrack(option)
                            },
                        )
                    }
                }
            }

        PlayerSheet.Speed ->
            PlayerBottomSheet(onDismiss) {
                DrawerSheetSubtitle(text = stringResource(R.string.player_speed))
                SpeedPicker(
                    initial = state.speed,
                    haptics = haptics,
                    onPreview = { viewModel.setSpeed(it) },
                )
            }

        PlayerSheet.StereoOutput ->
            PlayerBottomSheet(onDismiss) {
                DrawerSheetSubtitle(text = stringResource(R.string.player_stereo_output))
                val options =
                    if (state.renderProjection.isStereo) StereoOutputMode.entries
                    else listOf(StereoOutputMode.SingleEye, StereoOutputMode.SplitScreen)
                SegmentedGroup {
                    options.forEachIndexed { index, mode ->
                        ChoiceRow(
                            index = index,
                            count = options.size,
                            label = mode.label(),
                            selected = state.stereoOutput == mode,
                            onClick = {
                                haptics.tap()
                                viewModel.setStereoOutput(mode)
                                onDismiss()
                            },
                        )
                    }
                }
            }

        PlayerSheet.More ->
            PlayerBottomSheet(onDismiss) {
                val canConvert =
                    state.libraryId != null &&
                        state.sourceProjection == ProjectionMode.FLAT &&
                        state.videoPath?.startsWith("content://") == false
                val actions = buildList {
                    if (canConvert) {
                        add(
                            Action(Icons.Rounded.ViewInAr, R.string.convert_to_3d) {
                                viewModel.pause()
                                onOpenSheet(PlayerSheet.Convert)
                            }
                        )
                    }
                    if (state.libraryId != null) {
                        add(
                            Action(Icons.Rounded.Share, R.string.share) {
                                onDismiss()
                                onShare()
                            }
                        )
                        add(
                            Action(
                                Icons.AutoMirrored.Rounded.OpenInNew,
                                R.string.player_open_externally,
                            ) {
                                onDismiss()
                                viewModel.pause()
                                onOpenExternally()
                            }
                        )
                        add(
                            Action(Icons.Rounded.Delete, R.string.delete, destructive = true) {
                                onOpenSheet(PlayerSheet.Delete)
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SegmentedGroup {
                    actions.forEachIndexed { index, action ->
                        ActionRow(index = index, count = actions.size, action = action) {
                            haptics.tap()
                            action.onClick()
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
                    Button(
                        onClick = {
                            val id = state.libraryId ?: return@Button
                            haptics.confirm()
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
                        },
                        shapes = ButtonDefaults.shapes(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )

        PlayerSheet.Convert -> {
            val id = state.libraryId
            if (id == null) onDismiss() else ConvertTo3dDialog(videoId = id, onDismiss = onDismiss)
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
        Column(
            modifier =
                Modifier.heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp)
        ) {
            content()
        }
    }
}

/**
 * Items of a segmented list: separate rounded tiles with a small gap, the group rounded at its
 * ends.
 */
@Composable
private fun SegmentedGroup(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) { content() }
}

@Composable
private fun ChoiceRow(
    index: Int,
    count: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    supporting: String? = null,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + scaleIn(initialScale = 0.4f),
                exit = fadeOut() + scaleOut(targetScale = 0.4f),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}

private class Action(
    val icon: ImageVector,
    val label: Int,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun ActionRow(index: Int, count: Int, action: Action, onClick: () -> Unit) {
    val tint =
        if (action.destructive) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
        leadingContent = { Icon(action.icon, contentDescription = null, tint = tint) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(action.label),
            color =
                if (action.destructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Speed as a large value with step buttons, a slider for fine control and the common presets.
 * Changes apply at once, so the effect can be heard while choosing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedPicker(initial: Float, haptics: PlayerHaptics, onPreview: (Float) -> Unit) {
    var value by remember { mutableFloatStateOf(initial) }
    fun set(next: Float, fromSlider: Boolean = false) {
        val rounded = (Math.round(next / SPEED_STEP) * SPEED_STEP).coerceIn(SPEED_MIN, SPEED_MAX)
        if (rounded == value) return
        // A tick at every quarter step while sliding, a firmer one for buttons and presets.
        if (fromSlider) {
            if ((rounded * 4).toInt() != (value * 4).toInt()) haptics.tick()
        } else {
            haptics.step()
        }
        value = rounded
        onPreview(rounded)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FilledTonalIconButton(
            onClick = { set(value - SPEED_STEP) },
            enabled = value > SPEED_MIN,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
        ) {
            Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.player_slower))
        }
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                    (slideOutVertically { if (up) -it else it } + fadeOut())
            },
            label = "speedValue",
        ) {
            Text(
                text = stringResource(R.string.player_speed_value, speedLabel(it)),
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
            )
        }
        FilledTonalIconButton(
            onClick = { set(value + SPEED_STEP) },
            enabled = value < SPEED_MAX,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.player_faster))
        }
    }
    Slider(
        value = value,
        onValueChange = { set(it, fromSlider = true) },
        valueRange = SPEED_MIN..SPEED_MAX,
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        speedPresets.forEach { speed ->
            ToggleButton(
                checked = value == speed,
                onCheckedChange = { set(speed) },
                shapes = ToggleButtonDefaults.shapesFor(40.dp),
                modifier = Modifier.height(40.dp),
            ) {
                Text(
                    stringResource(R.string.player_speed_value, speedLabel(speed)),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                )
            }
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
    val haptics = rememberPlayerHaptics()
    var showFailure by remember { mutableStateOf(false) }
    LaunchedEffect(state.live3d) {
        if (state.live3d == Live3dState.Failed) {
            haptics.reject()
            showFailure = true
            delay(4_000)
            showFailure = false
            viewModel.dismissLive3dMessage()
        }
    }
    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        AnimatedVisibility(
            visible = showFailure,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .padding(top = 72.dp, start = 24.dp, end = 24.dp),
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
