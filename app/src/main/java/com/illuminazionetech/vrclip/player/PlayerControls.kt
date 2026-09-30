package com.illuminazionetech.vrclip.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.ScreenRotationAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Vrpano
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.player.gl.StereoOutputMode
import kotlinx.coroutines.delay

/** Formats a position as h:mm:ss or m:ss. */
internal fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000).toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private val scrimTop = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent))
private val scrimBottom =
    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))

@Composable
internal fun PlayerControls(
    state: PlayerUiState,
    visible: Boolean,
    locked: Boolean,
    aspectMode: AspectMode,
    gyroEnabled: Boolean,
    hasMotionSensor: Boolean,
    canEnterPictureInPicture: Boolean,
    onInteraction: () -> Unit,
    onNavigateBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onScrubbing: (Boolean) -> Unit,
    onToggleLive3d: () -> Unit,
    onOpenSheet: (PlayerSheet) -> Unit,
    onToggleLock: () -> Unit,
    onToggleRepeat: () -> Unit,
    onCycleAspect: () -> Unit,
    onToggleGyro: () -> Unit,
    onRecenter: () -> Unit,
    onRotate: () -> Unit,
    onEnterPictureInPicture: () -> Unit,
    onStartOver: () -> Unit,
    onDismissResume: () -> Unit,
    onRetry: () -> Unit,
    onOpenExternally: () -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides Color.White) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    modifier = Modifier.align(Alignment.TopCenter),
                    state = state,
                    onInteraction = onInteraction,
                    onNavigateBack = onNavigateBack,
                    onToggleLive3d = onToggleLive3d,
                    onOpenSheet = onOpenSheet,
                )
                if (state.error == null) {
                    Transport(
                        modifier = Modifier.align(Alignment.Center),
                        state = state,
                        onInteraction = onInteraction,
                        onTogglePlayPause = onTogglePlayPause,
                        onSeekBy = onSeekBy,
                    )
                }
                BottomBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    state = state,
                    aspectMode = aspectMode,
                    gyroEnabled = gyroEnabled,
                    hasMotionSensor = hasMotionSensor,
                    canEnterPictureInPicture = canEnterPictureInPicture,
                    onInteraction = onInteraction,
                    onSeekTo = onSeekTo,
                    onScrubbing = onScrubbing,
                    onOpenSheet = onOpenSheet,
                    onToggleLock = onToggleLock,
                    onToggleRepeat = onToggleRepeat,
                    onCycleAspect = onCycleAspect,
                    onToggleGyro = onToggleGyro,
                    onRecenter = onRecenter,
                    onRotate = onRotate,
                    onEnterPictureInPicture = onEnterPictureInPicture,
                )
            }
        }

        AnimatedVisibility(
            visible = visible && locked,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 32.dp),
        ) {
            FilledTonalIconButton(
                onClick = onToggleLock,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(Icons.Rounded.LockOpen, contentDescription = stringResource(R.string.player_unlock))
            }
        }

        if (state.isBuffering && !visible && state.error == null) {
            LoadingIndicator(modifier = Modifier.align(Alignment.Center).size(64.dp))
        }

        ResumeHint(
            modifier =
                Modifier.align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 16.dp, bottom = if (visible) 132.dp else 24.dp),
            resumedFromMs = state.resumedFromMs,
            onStartOver = onStartOver,
            onDismiss = onDismissResume,
        )

        state.error?.let { error ->
            ErrorCard(
                modifier =
                    Modifier.align(Alignment.Center)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(24.dp),
                error = error,
                onRetry = onRetry,
                onOpenExternally = onOpenExternally,
            )
        }
    }
    }
}

@Composable
private fun TopBar(
    modifier: Modifier,
    state: PlayerUiState,
    onInteraction: () -> Unit,
    onNavigateBack: () -> Unit,
    onToggleLive3d: () -> Unit,
    onOpenSheet: (PlayerSheet) -> Unit,
) {
    Box(
        modifier =
            modifier.fillMaxWidth().background(scrimTop).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                label = stringResource(R.string.back),
                onClick = onNavigateBack,
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = 1, initialDelayMillis = 2_000),
                )
                val subtitle =
                    when (state.live3d) {
                        Live3dState.On,
                        Live3dState.Starting -> stringResource(R.string.player_live_3d_on)
                        else ->
                            state.sourceProjection
                                .takeIf { it != ProjectionMode.FLAT }
                                ?.displayName()
                    }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (state.canConvertLive || state.live3d != Live3dState.Off) {
                Live3dToggle(
                    state = state.live3d,
                    onClick = {
                        onInteraction()
                        onToggleLive3d()
                    },
                )
            }
            ControlButton(
                icon = Icons.Rounded.Vrpano,
                label = stringResource(R.string.player_projection),
                onClick = {
                    onInteraction()
                    onOpenSheet(PlayerSheet.Projection)
                },
            )
            if (state.audioTracks.size > 1 || state.textTracks.isNotEmpty()) {
                ControlButton(
                    icon = Icons.Rounded.ClosedCaption,
                    label = stringResource(R.string.player_tracks),
                    onClick = {
                        onInteraction()
                        onOpenSheet(PlayerSheet.Tracks)
                    },
                )
            }
            ControlButton(
                icon = Icons.Rounded.MoreVert,
                label = stringResource(R.string.player_more),
                onClick = {
                    onInteraction()
                    onOpenSheet(PlayerSheet.More)
                },
            )
        }
    }
}

@Composable
private fun Live3dToggle(state: Live3dState, onClick: () -> Unit) {
    val checked = state == Live3dState.On || state == Live3dState.Starting
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        AnimatedContent(targetState = state == Live3dState.Starting, label = "live3dIcon") { starting ->
            if (starting) {
                LoadingIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Rounded.ViewInAr, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.player_live_3d))
    }
}

@Composable
private fun Transport(
    modifier: Modifier,
    state: PlayerUiState,
    onInteraction: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = {
                onInteraction()
                onSeekBy(-PlayerViewModel.SEEK_STEP_MS)
            },
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Rounded.Replay10, contentDescription = stringResource(R.string.player_rewind))
        }

        // The button morphs from a circle (paused) to a squircle (playing).
        val corner by animateIntAsState(if (state.isPlaying) 30 else 50, label = "playShape")
        FilledIconButton(
            onClick = {
                onInteraction()
                onTogglePlayPause()
            },
            modifier = Modifier.size(84.dp),
            shape = RoundedCornerShape(percent = corner),
        ) {
            AnimatedContent(
                targetState =
                    when {
                        state.isBuffering && state.playWhenReady -> PlayIcon.Loading
                        state.ended -> PlayIcon.Replay
                        state.playWhenReady -> PlayIcon.Pause
                        else -> PlayIcon.Play
                    },
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith
                        (fadeOut() + scaleOut(targetScale = 0.6f))
                },
                label = "playIcon",
            ) { icon ->
                when (icon) {
                    PlayIcon.Loading ->
                        LoadingIndicator(
                            modifier = Modifier.size(44.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    PlayIcon.Replay ->
                        Icon(Icons.Rounded.Replay, stringResource(R.string.player_play), Modifier.size(40.dp))
                    PlayIcon.Pause ->
                        Icon(Icons.Rounded.Pause, stringResource(R.string.player_pause), Modifier.size(40.dp))
                    PlayIcon.Play ->
                        Icon(Icons.Rounded.PlayArrow, stringResource(R.string.player_play), Modifier.size(40.dp))
                }
            }
        }

        FilledTonalIconButton(
            onClick = {
                onInteraction()
                onSeekBy(PlayerViewModel.SEEK_STEP_MS)
            },
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Rounded.Forward10, contentDescription = stringResource(R.string.player_forward))
        }
    }
}

private enum class PlayIcon {
    Play,
    Pause,
    Replay,
    Loading,
}

@Composable
private fun BottomBar(
    modifier: Modifier,
    state: PlayerUiState,
    aspectMode: AspectMode,
    gyroEnabled: Boolean,
    hasMotionSensor: Boolean,
    canEnterPictureInPicture: Boolean,
    onInteraction: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onScrubbing: (Boolean) -> Unit,
    onOpenSheet: (PlayerSheet) -> Unit,
    onToggleLock: () -> Unit,
    onToggleRepeat: () -> Unit,
    onCycleAspect: () -> Unit,
    onToggleGyro: () -> Unit,
    onRecenter: () -> Unit,
    onRotate: () -> Unit,
    onEnterPictureInPicture: () -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val position = if (dragging) (dragPosition * duration).toLong() else state.positionMs
    val tabular = "tnum"

    Box(
        modifier =
            modifier.fillMaxWidth().background(scrimBottom).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 24.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTime(position),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = tabular),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatTime(state.durationMs),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = tabular),
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
            Slider(
                value = if (dragging) dragPosition else (state.positionMs.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = {
                    if (!dragging) {
                        dragging = true
                        onScrubbing(true)
                    }
                    dragPosition = it
                    onInteraction()
                    onSeekTo((it * duration).toLong())
                },
                onValueChangeFinished = {
                    onSeekTo((dragPosition * duration).toLong())
                    onScrubbing(false)
                    dragging = false
                },
                enabled = state.durationMs > 0,
                colors =
                    SliderDefaults.colors(
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                    ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        onInteraction()
                        onOpenSheet(PlayerSheet.Speed)
                    }
                ) {
                    Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.player_speed_value, speedLabel(state.speed)))
                }
                IconToggleButton(
                    checked = state.repeat,
                    onCheckedChange = {
                        onInteraction()
                        onToggleRepeat()
                    },
                ) {
                    Icon(
                        if (state.repeat) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = stringResource(R.string.player_repeat),
                    )
                }
                val projection = state.renderProjection
                if (projection.isStereo || projection.isSpherical) {
                    ControlButton(
                        icon = Icons.Rounded.ViewInAr,
                        label = stringResource(R.string.player_stereo_output),
                        onClick = {
                            onInteraction()
                            onOpenSheet(PlayerSheet.StereoOutput)
                        },
                    )
                }
                if (projection.isSpherical) {
                    if (hasMotionSensor) {
                        IconToggleButton(
                            checked = gyroEnabled,
                            onCheckedChange = {
                                onInteraction()
                                onToggleGyro()
                            },
                        ) {
                            Icon(
                                Icons.Rounded.ScreenRotationAlt,
                                contentDescription = stringResource(R.string.player_motion),
                            )
                        }
                    }
                    ControlButton(
                        icon = Icons.Rounded.CenterFocusStrong,
                        label = stringResource(R.string.player_recenter),
                        onClick = {
                            onInteraction()
                            onRecenter()
                        },
                    )
                } else if (!projection.requiresImmersiveRendering) {
                    ControlButton(
                        icon = Icons.Rounded.AspectRatio,
                        label =
                            stringResource(
                                when (aspectMode) {
                                    AspectMode.Fit -> R.string.player_aspect_fit
                                    AspectMode.Fill -> R.string.player_aspect_fill
                                    AspectMode.Stretch -> R.string.player_aspect_stretch
                                }
                            ),
                        onClick = {
                            onInteraction()
                            onCycleAspect()
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                ControlButton(
                    icon = Icons.Rounded.Lock,
                    label = stringResource(R.string.player_lock),
                    onClick = onToggleLock,
                )
                ControlButton(
                    icon = Icons.Rounded.ScreenRotation,
                    label = stringResource(R.string.player_rotate),
                    onClick = {
                        onInteraction()
                        onRotate()
                    },
                )
                if (canEnterPictureInPicture) {
                    ControlButton(
                        icon = Icons.Rounded.PictureInPictureAlt,
                        label = stringResource(R.string.player_pip),
                        onClick = onEnterPictureInPicture,
                    )
                }
            }
        }
    }
}

internal fun speedLabel(speed: Float): String =
    if (speed % 1f == 0f) "%.0f".format(speed)
    else if ((speed * 10) % 1f == 0f) "%.1f".format(speed)
    else "%.2f".format(speed)

/** Icon button with a tooltip carrying its label (also its accessibility description). */
@Composable
internal fun ControlButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, colors = IconButtonDefaults.iconButtonColors()) {
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
private fun ResumeHint(
    modifier: Modifier,
    resumedFromMs: Long?,
    onStartOver: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(resumedFromMs) {
        if (resumedFromMs != null) {
            delay(7_000)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = resumedFromMs != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.player_resumed, formatTime(resumedFromMs ?: 0L)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onStartOver) { Text(stringResource(R.string.player_start_over)) }
            }
        }
    }
}

@Composable
private fun ErrorCard(
    modifier: Modifier,
    error: PlaybackException,
    onRetry: () -> Unit,
    onOpenExternally: () -> Unit,
) {
    Surface(
        modifier = modifier.widthIn(max = 420.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text =
                    stringResource(
                        when (error.errorCode) {
                            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
                            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ->
                                R.string.player_error_not_found
                            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                                R.string.player_error_unsupported
                            else -> R.string.player_error_generic
                        }
                    ),
                style = MaterialTheme.typography.titleMedium,
            )
            error.errorCodeName.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onOpenExternally) {
                    Text(stringResource(R.string.player_open_externally))
                }
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

/** Double-tap seek ripples, scrub preview, brightness/volume level, the 2x hint. */
@Composable
internal fun PlayerFeedback(feedback: GestureFeedback?, durationMs: Long) {
    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val seek = feedback as? GestureFeedback.Seek
        AnimatedVisibility(
            visible = seek != null && !seek.forward,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp),
        ) {
            SeekBubble(icon = Icons.Rounded.FastRewind, seconds = seek?.seconds ?: 10, forward = false)
        }
        AnimatedVisibility(
            visible = seek != null && seek.forward,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp),
        ) {
            SeekBubble(icon = Icons.Rounded.FastForward, seconds = seek?.seconds ?: 10, forward = true)
        }

        val pill: (@Composable () -> Unit)? =
            when (feedback) {
                is GestureFeedback.Scrub -> {
                    {
                        val sign = if (feedback.deltaMs >= 0) "+" else "−"
                        Text(
                            text =
                                "$sign${formatTime(kotlin.math.abs(feedback.deltaMs))}  ·  " +
                                    "${formatTime(feedback.targetMs)} / ${formatTime(durationMs)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                is GestureFeedback.Brightness -> {
                    { LevelPill(Icons.Rounded.BrightnessMedium, stringResource(R.string.player_brightness), feedback.level) }
                }
                is GestureFeedback.Volume -> {
                    { LevelPill(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.player_volume), feedback.level) }
                }
                GestureFeedback.FastForward -> {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("2×", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Rounded.FastForward, contentDescription = null)
                        }
                    }
                }
                else -> null
            }
        AnimatedVisibility(
            visible = pill != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            ) {
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) { pill?.invoke() }
            }
        }
    }
}

@Composable
private fun SeekBubble(icon: ImageVector, seconds: Int, forward: Boolean) {
    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.5f), modifier = Modifier.size(104.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            Text(
                text = stringResource(R.string.player_seek_amount, if (forward) "+$seconds" else "−$seconds"),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun LevelPill(icon: ImageVector, label: String, level: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        LinearProgressIndicator(progress = { level }, modifier = Modifier.width(140.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${(level * 100).toInt()}%",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(40.dp),
        )
    }
}

/** Shown in the top bar for stereo output choices; kept here so the sheet can reuse it. */
@Composable
internal fun StereoOutputMode.label(): String =
    stringResource(
        when (this) {
            StereoOutputMode.SingleEye -> R.string.player_stereo_output_single
            StereoOutputMode.SplitScreen -> R.string.player_stereo_output_split
            StereoOutputMode.Anaglyph -> R.string.player_stereo_output_anaglyph
        }
    )
