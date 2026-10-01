package com.illuminazionetech.vrclip.player.quest

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Vrpano
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.player.Live3dState
import com.illuminazionetech.vrclip.player.PlayerViewModel
import com.illuminazionetech.vrclip.player.ProjectionMode
import com.illuminazionetech.vrclip.player.displayName
import com.illuminazionetech.vrclip.player.formatTime
import com.illuminazionetech.vrclip.player.stereo.DepthModelManager
import com.illuminazionetech.vrclip.player.stereo.DepthModelStatus
import com.illuminazionetech.vrclip.player.stereo.isBusy
import com.illuminazionetech.vrclip.util.toFileSizeText

private enum class ControlsPage {
    Main,
    Projection,
    Model,
}

/**
 * Control bar of the immersive player, drawn on a panel in the scene. Everything stays inside the
 * panel (no dialogs or popup menus, which would open outside the scene): secondary choices swap the
 * bar's content instead.
 */
@Composable
internal fun ImmersiveControls(
    viewModel: PlayerViewModel,
    passthrough: Boolean,
    onTogglePassthrough: () -> Unit,
    onRecenter: () -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf(ControlsPage.Main) }
    var passthroughOn by remember { mutableStateOf(passthrough) }

    LaunchedEffect(state.live3d) {
        if (state.live3d == Live3dState.NeedsModel) page = ControlsPage.Model
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        // A translucent color does not map to a content color by itself.
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "controlsPage",
        ) { current ->
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                when (current) {
                    ControlsPage.Main ->
                        MainPage(
                            viewModel = viewModel,
                            passthrough = passthroughOn,
                            onTogglePassthrough = {
                                passthroughOn = !passthroughOn
                                onTogglePassthrough()
                            },
                            onRecenter = onRecenter,
                            onClose = onClose,
                            onOpenProjection = { page = ControlsPage.Projection },
                        )
                    ControlsPage.Projection ->
                        ProjectionPage(viewModel = viewModel, onBack = { page = ControlsPage.Main })
                    ControlsPage.Model ->
                        ModelPage(
                            onReady = {
                                page = ControlsPage.Main
                                viewModel.dismissLive3dMessage()
                                viewModel.setLive3d(true)
                            },
                            onBack = {
                                page = ControlsPage.Main
                                viewModel.dismissLive3dMessage()
                            },
                        )
                }
            }
        }
    }
}

@Composable
private fun MainPage(
    viewModel: PlayerViewModel,
    passthrough: Boolean,
    onTogglePassthrough: () -> Unit,
    onRecenter: () -> Unit,
    onClose: () -> Unit,
    onOpenProjection: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1L)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    if (state.live3d == Live3dState.On) stringResource(R.string.player_live_3d_on)
                    else state.sourceProjection.displayName(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatTime(if (dragging) (dragValue * duration).toLong() else state.positionMs),
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            value =
                if (dragging) dragValue
                else (state.positionMs.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = {
                if (!dragging) {
                    dragging = true
                    viewModel.setScrubbing(true)
                }
                dragValue = it
                viewModel.seekTo((it * duration).toLong())
            },
            onValueChangeFinished = {
                viewModel.seekTo((dragValue * duration).toLong())
                viewModel.setScrubbing(false)
                dragging = false
            },
            enabled = state.durationMs > 0,
        )
        Text(formatTime(state.durationMs), style = MaterialTheme.typography.labelLarge)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalIconButton(onClick = { viewModel.seekBy(-PlayerViewModel.SEEK_STEP_MS) }) {
            Icon(
                Icons.Rounded.Replay10,
                contentDescription = stringResource(R.string.player_rewind),
            )
        }
        FilledIconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.size(56.dp)) {
            if (state.isBuffering && state.playWhenReady) {
                LoadingIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(
                    if (state.playWhenReady && !state.ended) Icons.Rounded.Pause
                    else Icons.Rounded.PlayArrow,
                    contentDescription =
                        stringResource(
                            if (state.playWhenReady) R.string.player_pause else R.string.player_play
                        ),
                )
            }
        }
        FilledTonalIconButton(onClick = { viewModel.seekBy(PlayerViewModel.SEEK_STEP_MS) }) {
            Icon(
                Icons.Rounded.Forward10,
                contentDescription = stringResource(R.string.player_forward),
            )
        }
        Spacer(Modifier.weight(1f))
        if (state.canConvertLive || state.live3d != Live3dState.Off) {
            val on = state.live3d == Live3dState.On || state.live3d == Live3dState.Starting
            ToggleButton(checked = on, onCheckedChange = { viewModel.setLive3d(!on) }) {
                if (state.live3d == Live3dState.Starting) LoadingIndicator(Modifier.size(20.dp))
                else
                    Icon(
                        Icons.Rounded.ViewInAr,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.player_live_3d))
            }
        }
        TextButton(onClick = onOpenProjection) {
            Icon(Icons.Rounded.Vrpano, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.player_projection))
        }
        if (!state.renderProjection.isSpherical) {
            ToggleButton(checked = passthrough, onCheckedChange = { onTogglePassthrough() }) {
                Icon(
                    if (passthrough) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.player_passthrough))
            }
        }
        IconButton(onClick = onRecenter) {
            Icon(
                Icons.Rounded.CenterFocusStrong,
                contentDescription = stringResource(R.string.player_recenter),
            )
        }
    }
    if (state.live3d == Live3dState.Failed) {
        Text(
            text = stringResource(R.string.player_live_3d_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ProjectionPage(viewModel: PlayerViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
        Text(
            stringResource(R.string.player_projection),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = state.projectionOverride == null,
            onClick = {
                viewModel.setProjectionOverride(null)
                onBack()
            },
            label = {
                Text(
                    stringResource(
                        R.string.player_projection_detected,
                        state.detection.mode.displayName(),
                    )
                )
            },
        )
        ProjectionMode.entries.forEach { mode ->
            FilterChip(
                selected = state.projectionOverride == mode,
                onClick = {
                    viewModel.setProjectionOverride(mode)
                    onBack()
                },
                label = { Text(mode.displayName()) },
            )
        }
    }
}

@Composable
private fun ModelPage(onReady: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { DepthModelManager.get(context) }
    val modelState by manager.state.collectAsStateWithLifecycle()
    LaunchedEffect(modelState) { if (modelState is DepthModelManager.State.Installed) onReady() }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
        Text(
            stringResource(R.string.stereo_model_title),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    Text(
        text =
            stringResource(
                R.string.stereo_model_desc,
                DepthModelManager.DOWNLOAD_BYTES.toFileSizeText(),
            ),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.size(8.dp))
    DepthModelStatus(state = modelState)
    if (!modelState.isBusy()) {
        Button(
            onClick = {
                manager.resetError()
                manager.start()
            },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(
                stringResource(
                    if (modelState is DepthModelManager.State.Failed) R.string.retry
                    else R.string.stereo_model_download
                )
            )
        }
    }
}
