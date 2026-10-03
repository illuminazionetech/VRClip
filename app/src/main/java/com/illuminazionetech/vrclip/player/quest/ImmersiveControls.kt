package com.illuminazionetech.vrclip.player.quest

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Vrpano
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.player.Live3dState
import com.illuminazionetech.vrclip.player.PlayPauseGlyph
import com.illuminazionetech.vrclip.player.PlayerUiState
import com.illuminazionetech.vrclip.player.PlayerViewModel
import com.illuminazionetech.vrclip.player.ProjectionMode
import com.illuminazionetech.vrclip.player.displayName
import com.illuminazionetech.vrclip.player.formatTime
import com.illuminazionetech.vrclip.player.messageRes
import com.illuminazionetech.vrclip.player.rememberPlayerHaptics
import com.illuminazionetech.vrclip.player.speedLabel
import com.illuminazionetech.vrclip.player.stereo.DepthModel
import com.illuminazionetech.vrclip.player.stereo.DepthModelManager
import com.illuminazionetech.vrclip.player.stereo.DepthModelStatus
import com.illuminazionetech.vrclip.player.stereo.isBusy
import com.illuminazionetech.vrclip.util.toFileSizeText
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class ControlsPage {
    Main,
    Projection,
    Speed,
    Tracks,
    Model,
}

private val questSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** Ticks felt while dragging the position across the whole video. */
private const val SCRUB_TICKS = 20

/**
 * Control bar of the immersive player, drawn on a panel in the scene. Everything stays inside the
 * panel (no dialogs or popup menus, which would open outside the scene): secondary choices slide in
 * as pages of the bar instead.
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
    val haptics = rememberPlayerHaptics()

    LaunchedEffect(state.error) { if (state.error != null) haptics.reject() }
    LaunchedEffect(state.live3d) {
        when (state.live3d) {
            Live3dState.NeedsModel -> page = ControlsPage.Model
            Live3dState.Failed -> haptics.reject()
            else -> Unit
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
        // A translucent color does not map to a content color by itself.
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != ControlsPage.Main
                (slideInHorizontally(motion.defaultSpatialSpec()) {
                    if (forward) it / 4 else -it / 4
                } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                    (slideOutHorizontally(motion.fastSpatialSpec()) {
                        if (forward) -it / 4 else it / 4
                    } + fadeOut(motion.fastEffectsSpec()))
            },
            label = "controlsPage",
        ) { current ->
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                val back = { page = ControlsPage.Main }
                when (current) {
                    ControlsPage.Main ->
                        MainPage(
                            state = state,
                            viewModel = viewModel,
                            passthrough = passthroughOn,
                            onTogglePassthrough = {
                                passthroughOn = !passthroughOn
                                onTogglePassthrough()
                            },
                            onRecenter = onRecenter,
                            onClose = onClose,
                            onOpen = { page = it },
                        )
                    ControlsPage.Projection ->
                        ProjectionPage(state = state, viewModel = viewModel, onBack = back)
                    ControlsPage.Speed ->
                        SpeedPage(state = state, viewModel = viewModel, onBack = back)
                    ControlsPage.Tracks ->
                        TracksPage(state = state, viewModel = viewModel, onBack = back)
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
    state: PlayerUiState,
    viewModel: PlayerViewModel,
    passthrough: Boolean,
    onTogglePassthrough: () -> Unit,
    onRecenter: () -> Unit,
    onClose: () -> Unit,
    onOpen: (ControlsPage) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var lastTick by remember { mutableIntStateOf(0) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val haptics = rememberPlayerHaptics()
    val hover = LocalControllerHaptics.current

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
        IconButton(
            onClick = {
                haptics.tap()
                onClose()
            },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.hoverHaptic(hover),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        val tabular = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
        Text(
            formatTime(if (dragging) (dragValue * duration).toLong() else state.positionMs),
            style = tabular,
        )
        Slider(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp).hoverHaptic(hover),
            value =
                if (dragging) dragValue
                else (state.positionMs.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = {
                if (!dragging) {
                    dragging = true
                    lastTick = (it * SCRUB_TICKS).toInt()
                    haptics.gestureStart()
                    viewModel.setScrubbing(true)
                }
                val tick = (it * SCRUB_TICKS).toInt()
                if (tick != lastTick) {
                    lastTick = tick
                    haptics.tick()
                }
                dragValue = it
                viewModel.seekTo((it * duration).toLong())
            },
            onValueChangeFinished = {
                viewModel.seekTo((dragValue * duration).toLong())
                viewModel.setScrubbing(false)
                haptics.gestureEnd()
                dragging = false
            },
            enabled = state.durationMs > 0,
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    drawStopIndicator = null,
                    modifier = Modifier.height(12.dp),
                )
            },
        )
        Text(formatTime(state.durationMs), style = tabular)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuestSeekButton(forward = false) { viewModel.seekBy(-PlayerViewModel.SEEK_STEP_MS) }
        QuestPlayButton(
            state = state,
            onClick = {
                haptics.tap()
                viewModel.togglePlayPause()
            },
        )
        QuestSeekButton(forward = true) { viewModel.seekBy(PlayerViewModel.SEEK_STEP_MS) }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.canConvertLive || state.live3d != Live3dState.Off) {
                val on = state.live3d == Live3dState.On || state.live3d == Live3dState.Starting
                BarToggle(
                    checked = on,
                    onCheckedChange = {
                        haptics.toggle(!on)
                        viewModel.setLive3d(!on)
                    },
                ) {
                    if (state.live3d == Live3dState.Starting) {
                        LoadingIndicator(
                            Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Rounded.ViewInAr, null, Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.player_live_3d))
                }
            }
            BarButton(
                icon = Icons.Rounded.Speed,
                label = stringResource(R.string.player_speed_value, speedLabel(state.speed)),
                onClick = { onOpen(ControlsPage.Speed) },
            )
            if (state.audioTracks.size > 1 || state.textTracks.isNotEmpty()) {
                BarButton(
                    icon = Icons.Rounded.ClosedCaption,
                    label = stringResource(R.string.player_tracks),
                    onClick = { onOpen(ControlsPage.Tracks) },
                )
            }
            BarButton(
                icon = Icons.Rounded.Vrpano,
                label = stringResource(R.string.player_projection),
                onClick = { onOpen(ControlsPage.Projection) },
            )
            if (!state.renderProjection.isSpherical) {
                BarToggle(
                    checked = passthrough,
                    onCheckedChange = {
                        haptics.toggle(!passthrough)
                        onTogglePassthrough()
                    },
                ) {
                    Icon(
                        if (passthrough) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.player_passthrough))
                }
            }
            FilledTonalIconButton(
                onClick = {
                    haptics.step()
                    onRecenter()
                },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.hoverHaptic(hover),
            ) {
                Icon(
                    Icons.Rounded.CenterFocusStrong,
                    contentDescription = stringResource(R.string.player_recenter),
                )
            }
        }
    }

    AnimatedVisibility(visible = state.live3d == Live3dState.Failed) {
        Text(
            text = stringResource(R.string.player_live_3d_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelLarge,
        )
    }

    // A video that cannot be opened says why, instead of leaving an empty screen.
    AnimatedVisibility(visible = state.error != null) {
        val error = state.error ?: return@AnimatedVisibility
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(error.messageRes()),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = error.errorCodeName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BarButton(
                icon = Icons.Rounded.Replay,
                label = stringResource(R.string.retry),
                onClick = viewModel::retry,
            )
        }
    }
}

/** ±10 s with the arrow spinning in the direction of the jump. */
@Composable
private fun QuestSeekButton(forward: Boolean, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val haptics = rememberPlayerHaptics()
    FilledTonalIconButton(
        onClick = {
            haptics.step()
            onClick()
            scope.launch {
                rotation.animateTo(if (forward) 40f else -40f, spatial)
                rotation.animateTo(0f, spatial)
            }
        },
        shapes = IconButtonDefaults.shapes(),
        modifier =
            Modifier.size(IconButtonDefaults.mediumContainerSize())
                .hoverHaptic(LocalControllerHaptics.current),
    ) {
        Icon(
            if (forward) Icons.Rounded.Forward10 else Icons.Rounded.Replay10,
            contentDescription =
                stringResource(if (forward) R.string.player_forward else R.string.player_rewind),
            modifier = Modifier.rotate(rotation.value),
        )
    }
}

/** Round while paused, a rounded square while playing, with the folding play/pause glyph. */
@Composable
private fun QuestPlayButton(state: PlayerUiState, onClick: () -> Unit) {
    val playing = state.playWhenReady && !state.ended
    val corner by
        animateFloatAsState(
            targetValue = if (playing) 28f else 50f,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "questPlayShape",
        )
    val label = stringResource(if (playing) R.string.player_pause else R.string.player_play)
    FilledIconButton(
        onClick = onClick,
        shape = RoundedCornerShape(percent = corner.roundToInt()),
        modifier =
            Modifier.size(DpSize(80.dp, 64.dp))
                .hoverHaptic(LocalControllerHaptics.current)
                .semantics { contentDescription = label },
    ) {
        AnimatedContent(
            targetState =
                when {
                    state.isBuffering && state.playWhenReady -> 0
                    state.ended -> 1
                    else -> 2
                },
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith
                    (fadeOut() + scaleOut(targetScale = 0.6f))
            },
            label = "questPlayIcon",
        ) {
            when (it) {
                0 ->
                    LoadingIndicator(
                        Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                1 -> Icon(Icons.Rounded.Replay, contentDescription = null, Modifier.size(32.dp))
                else -> PlayPauseGlyph(playing = playing, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun BarToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = ToggleButtonDefaults.shapesFor(48.dp),
        colors =
            ToggleButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.height(48.dp).hoverHaptic(LocalControllerHaptics.current),
        content = content,
    )
}

@Composable
private fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val haptics = rememberPlayerHaptics()
    Button(
        onClick = {
            haptics.tap()
            onClick()
        },
        shapes = ButtonDefaults.shapes(),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.height(48.dp).hoverHaptic(LocalControllerHaptics.current),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    val haptics = rememberPlayerHaptics()
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = {
                haptics.tap()
                onBack()
            },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.hoverHaptic(LocalControllerHaptics.current),
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

/** A scrolling row of toggle buttons, one per choice, the current one filled. */
@Composable
private fun <T> ChoiceRow(
    choices: List<T>,
    selected: (T) -> Boolean,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val haptics = rememberPlayerHaptics()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { choice ->
            val isSelected = selected(choice)
            BarToggle(
                checked = isSelected,
                onCheckedChange = {
                    if (!isSelected) haptics.step()
                    onSelect(choice)
                },
            ) {
                Text(label(choice), maxLines = 1)
            }
        }
    }
}

@Composable
private fun ProjectionPage(state: PlayerUiState, viewModel: PlayerViewModel, onBack: () -> Unit) {
    PageHeader(stringResource(R.string.player_projection), onBack)
    val automatic: ProjectionMode? = null
    ChoiceRow(
        choices = listOf(automatic) + ProjectionMode.entries,
        selected = { it == state.projectionOverride },
        label = {
            if (it == null)
                stringResource(
                    R.string.player_projection_detected,
                    state.detection.mode.displayName(),
                )
            else it.displayName()
        },
        onSelect = {
            viewModel.setProjectionOverride(it)
            onBack()
        },
    )
}

@Composable
private fun SpeedPage(state: PlayerUiState, viewModel: PlayerViewModel, onBack: () -> Unit) {
    PageHeader(stringResource(R.string.player_speed), onBack)
    ChoiceRow(
        choices = (questSpeeds + state.speed).distinct().sorted(),
        selected = { it == state.speed },
        label = { stringResource(R.string.player_speed_value, speedLabel(it)) },
        onSelect = { viewModel.setSpeed(it) },
    )
}

@Composable
private fun TracksPage(state: PlayerUiState, viewModel: PlayerViewModel, onBack: () -> Unit) {
    PageHeader(stringResource(R.string.player_tracks), onBack)
    if (state.audioTracks.size > 1) {
        ChoiceRow(
            choices = state.audioTracks,
            selected = { it.selected },
            label = { it.label },
            onSelect = viewModel::selectTrack,
        )
    }
    val off = stringResource(R.string.player_subtitles_off)
    ChoiceRow(
        choices = listOf(null) + state.textTracks,
        selected = { if (it == null) state.subtitlesOff else it.selected },
        label = { it?.label ?: off },
        onSelect = { if (it == null) viewModel.disableSubtitles() else viewModel.selectTrack(it) },
    )
}

@Composable
private fun ModelPage(onReady: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { DepthModelManager.get(context) }
    val modelState by manager.state.collectAsStateWithLifecycle()
    val haptics = rememberPlayerHaptics()
    val hover = LocalControllerHaptics.current
    LaunchedEffect(modelState) { if (modelState is DepthModelManager.State.Installed) onReady() }

    PageHeader(stringResource(R.string.stereo_model_title), onBack)
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            text =
                stringResource(
                    R.string.stereo_model_desc,
                    DepthModel.totalBytes.toFileSizeText(),
                ),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DepthModelStatus(state = modelState)
            if (modelState.isBusy()) {
                Button(
                    onClick = {
                        haptics.tap()
                        manager.cancel()
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.hoverHaptic(hover),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            } else {
                Button(
                    onClick = {
                        haptics.tap()
                        manager.resetError()
                        manager.start()
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.hoverHaptic(hover),
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
    }
}
