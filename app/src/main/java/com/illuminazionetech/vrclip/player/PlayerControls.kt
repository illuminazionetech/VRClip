package com.illuminazionetech.vrclip.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureInPictureAlt
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.media3.common.PlaybackException
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.player.gl.StereoOutputMode
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Formats a position as h:mm:ss or m:ss. */
internal fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000).toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

internal fun speedLabel(speed: Float): String =
    if (speed % 1f == 0f) "%.0f".format(speed)
    else if ((speed * 10) % 1f == 0f) "%.1f".format(speed) else "%.2f".format(speed)

/** Dark glass behind controls that sit on video, so they read on any picture. */
internal val PlayerGlass = Color(0xFF111318).copy(alpha = 0.74f)

private val scrimTop =
    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))
private val scrimBottom =
    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.76f)))

/** Seek bar progress at which the scrubbing haptic ticks (every 5% of the video). */
private const val SCRUB_TICKS = 20

@Composable
internal fun PlayerControls(
    state: PlayerUiState,
    visible: Boolean,
    locked: Boolean,
    aspectMode: AspectMode,
    gyroEnabled: Boolean,
    hasMotionSensor: Boolean,
    canEnterPictureInPicture: Boolean,
    preview: PreviewFrame?,
    haptics: PlayerHaptics,
    onInteraction: () -> Unit,
    onNavigateBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onScrubbing: (Boolean) -> Unit,
    onPreviewRequest: (Long?) -> Unit,
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
    val motion = MaterialTheme.motionScheme
    val showControls = visible && !locked
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = showControls,
                enter =
                    slideInVertically(motion.defaultSpatialSpec()) { -it / 2 } +
                        fadeIn(motion.defaultEffectsSpec()),
                exit =
                    slideOutVertically(motion.fastSpatialSpec()) { -it / 2 } +
                        fadeOut(motion.fastEffectsSpec()),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                TopBar(
                    state = state,
                    haptics = haptics,
                    onInteraction = onInteraction,
                    onNavigateBack = onNavigateBack,
                    onToggleLive3d = onToggleLive3d,
                    onOpenSheet = onOpenSheet,
                )
            }

            AnimatedVisibility(
                visible = showControls && state.error == null,
                enter =
                    scaleIn(motion.defaultSpatialSpec(), initialScale = 0.82f) +
                        fadeIn(motion.defaultEffectsSpec()),
                exit =
                    scaleOut(motion.fastSpatialSpec(), targetScale = 0.9f) +
                        fadeOut(motion.fastEffectsSpec()),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Transport(
                    state = state,
                    haptics = haptics,
                    onInteraction = onInteraction,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekBy = onSeekBy,
                )
            }

            AnimatedVisibility(
                visible = showControls,
                enter =
                    slideInVertically(motion.defaultSpatialSpec()) { it / 2 } +
                        fadeIn(motion.defaultEffectsSpec()),
                exit =
                    slideOutVertically(motion.fastSpatialSpec()) { it / 2 } +
                        fadeOut(motion.fastEffectsSpec()),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                BottomBar(
                    state = state,
                    aspectMode = aspectMode,
                    gyroEnabled = gyroEnabled,
                    hasMotionSensor = hasMotionSensor,
                    canEnterPictureInPicture = canEnterPictureInPicture,
                    preview = preview,
                    haptics = haptics,
                    onInteraction = onInteraction,
                    onSeekTo = onSeekTo,
                    onScrubbing = onScrubbing,
                    onPreviewRequest = onPreviewRequest,
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

            AnimatedVisibility(
                visible = visible && locked,
                enter =
                    scaleIn(motion.defaultSpatialSpec(), initialScale = 0.6f) +
                        fadeIn(motion.defaultEffectsSpec()),
                exit =
                    scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f) +
                        fadeOut(motion.fastEffectsSpec()),
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(bottom = 32.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(
                        onClick = onToggleLock,
                        shapes = IconButtonDefaults.shapes(),
                        colors =
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = PlayerGlass,
                                contentColor = Color.White,
                            ),
                        modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
                    ) {
                        Icon(
                            Icons.Rounded.LockOpen,
                            contentDescription = stringResource(R.string.player_unlock),
                            modifier = Modifier.size(IconButtonDefaults.largeIconSize),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = CircleShape, color = PlayerGlass, contentColor = Color.White) {
                        Text(
                            text = stringResource(R.string.player_locked_hint),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            if (state.isBuffering && !visible && state.error == null) {
                LoadingIndicator(
                    modifier = Modifier.align(Alignment.Center).size(72.dp),
                    color = Color.White,
                )
            }

            ResumeHint(
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = if (showControls) 76.dp else 24.dp),
                resumedFromMs = state.resumedFromMs,
                onStartOver = {
                    haptics.tap()
                    onStartOver()
                },
                onDismiss = onDismissResume,
            )

            state.error?.let { error ->
                LaunchedEffect(error) { haptics.reject() }
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
    state: PlayerUiState,
    haptics: PlayerHaptics,
    onInteraction: () -> Unit,
    onNavigateBack: () -> Unit,
    onToggleLive3d: () -> Unit,
    onOpenSheet: (PlayerSheet) -> Unit,
) {
    fun open(sheet: PlayerSheet) {
        haptics.tap()
        onInteraction()
        onOpenSheet(sheet)
    }
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .background(scrimTop)
                .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                label = stringResource(R.string.back),
                onClick = {
                    haptics.tap()
                    onNavigateBack()
                },
                glass = true,
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
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
                AnimatedVisibility(visible = subtitle != null) {
                    Text(
                        text = subtitle.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.canConvertLive || state.live3d != Live3dState.Off) {
                    Live3dToggle(
                        state = state.live3d,
                        onClick = {
                            haptics.toggle(state.live3d == Live3dState.Off)
                            onInteraction()
                            onToggleLive3d()
                        },
                    )
                }
                PlayerIconButton(
                    icon = Icons.Rounded.Vrpano,
                    label = stringResource(R.string.player_projection),
                    onClick = { open(PlayerSheet.Projection) },
                )
                if (state.audioTracks.size > 1 || state.textTracks.isNotEmpty()) {
                    PlayerIconButton(
                        icon = Icons.Rounded.ClosedCaption,
                        label = stringResource(R.string.player_tracks),
                        onClick = { open(PlayerSheet.Tracks) },
                    )
                }
                PlayerIconButton(
                    icon = Icons.Rounded.MoreVert,
                    label = stringResource(R.string.player_more),
                    onClick = { open(PlayerSheet.More) },
                )
            }
        }
    }
}

@Composable
private fun Live3dToggle(state: Live3dState, onClick: () -> Unit) {
    val checked = state == Live3dState.On || state == Live3dState.Starting
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        shapes = ToggleButtonDefaults.shapesFor(40.dp),
        colors =
            ToggleButtonDefaults.colors(
                containerColor = PlayerGlass,
                contentColor = Color.White,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
        modifier = Modifier.height(40.dp),
    ) {
        AnimatedContent(
            targetState = state == Live3dState.Starting,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith
                    (fadeOut() + scaleOut(targetScale = 0.6f))
            },
            label = "live3dIcon",
        ) { starting ->
            if (starting) {
                LoadingIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    Icons.Rounded.ViewInAr,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.player_live_3d), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Transport(
    state: PlayerUiState,
    haptics: PlayerHaptics,
    onInteraction: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeekStepButton(
            forward = false,
            onClick = {
                haptics.step()
                onInteraction()
                onSeekBy(-PlayerViewModel.SEEK_STEP_MS)
            },
        )
        PlayPauseButton(
            state = state,
            onClick = {
                haptics.tap()
                onInteraction()
                onTogglePlayPause()
            },
        )
        SeekStepButton(
            forward = true,
            onClick = {
                haptics.step()
                onInteraction()
                onSeekBy(PlayerViewModel.SEEK_STEP_MS)
            },
        )
    }
}

/** A ±10 s button whose arrow spins in the direction of the jump. */
@Composable
private fun SeekStepButton(forward: Boolean, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    FilledTonalIconButton(
        onClick = {
            onClick()
            scope.launch {
                rotation.snapTo(0f)
                rotation.animateTo(if (forward) 40f else -40f, spatial)
                rotation.animateTo(0f, spatial)
            }
        },
        shapes = IconButtonDefaults.shapes(),
        colors =
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = PlayerGlass,
                contentColor = Color.White,
            ),
        modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
    ) {
        Icon(
            if (forward) Icons.Rounded.Forward10 else Icons.Rounded.Replay10,
            contentDescription =
                stringResource(if (forward) R.string.player_forward else R.string.player_rewind),
            modifier = Modifier.size(IconButtonDefaults.mediumIconSize).rotate(rotation.value),
        )
    }
}

private enum class PlayIcon {
    PlayPause,
    Replay,
    Loading,
}

/**
 * The main button: round while paused, a rounded square while playing, with the play triangle
 * folding into the pause bars. It shows a loading indicator while buffering and replay at the end.
 */
@Composable
private fun PlayPauseButton(state: PlayerUiState, onClick: () -> Unit) {
    val playing = state.playWhenReady && !state.ended
    val corner by
        animateFloatAsState(
            targetValue = if (playing) 28f else 50f,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "playShape",
        )
    val icon =
        when {
            state.isBuffering && state.playWhenReady -> PlayIcon.Loading
            state.ended -> PlayIcon.Replay
            else -> PlayIcon.PlayPause
        }
    val label = stringResource(if (playing) R.string.player_pause else R.string.player_play)
    FilledIconButton(
        onClick = onClick,
        shape = RoundedCornerShape(percent = corner.roundToInt()),
        modifier =
            Modifier.size(
                    IconButtonDefaults.largeContainerSize(
                        IconButtonDefaults.IconButtonWidthOption.Wide
                    )
                )
                .semantics { contentDescription = label },
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith
                    (fadeOut() + scaleOut(targetScale = 0.6f))
            },
            label = "playIcon",
        ) { current ->
            when (current) {
                PlayIcon.Loading ->
                    LoadingIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                PlayIcon.Replay ->
                    Icon(Icons.Rounded.Replay, contentDescription = null, Modifier.size(40.dp))
                PlayIcon.PlayPause ->
                    PlayPauseGlyph(playing = playing, modifier = Modifier.size(40.dp))
            }
        }
    }
}

/** Play triangle that folds into two pause bars: each bar morphs from one half of the triangle. */
@Composable
internal fun PlayPauseGlyph(playing: Boolean, modifier: Modifier = Modifier) {
    val progress by
        animateFloatAsState(
            targetValue = if (playing) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "playPauseMorph",
        )
    val color = LocalContentColor.current
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.08f, join = StrokeJoin.Round)
        fun quad(play: FloatArray, pause: FloatArray): Path {
            val p = FloatArray(8) { lerp(play[it], pause[it], progress) * s }
            return Path().apply {
                moveTo(p[0], p[1])
                lineTo(p[2], p[3])
                lineTo(p[4], p[5])
                lineTo(p[6], p[7])
                close()
            }
        }
        for ((play, pause) in GLYPH_QUADS) {
            val path = quad(play, pause)
            drawPath(path, color)
            drawPath(path, color, style = stroke)
        }
    }
}

/** Corners (top left, top right, bottom right, bottom left) as fractions of the icon size. */
private val GLYPH_QUADS =
    listOf(
        floatArrayOf(0.30f, 0.21f, 0.55f, 0.355f, 0.55f, 0.645f, 0.30f, 0.79f) to
            floatArrayOf(0.27f, 0.22f, 0.43f, 0.22f, 0.43f, 0.78f, 0.27f, 0.78f),
        floatArrayOf(0.55f, 0.355f, 0.79f, 0.5f, 0.79f, 0.5f, 0.55f, 0.645f) to
            floatArrayOf(0.57f, 0.22f, 0.73f, 0.22f, 0.73f, 0.78f, 0.57f, 0.78f),
    )

@Composable
private fun BottomBar(
    state: PlayerUiState,
    aspectMode: AspectMode,
    gyroEnabled: Boolean,
    hasMotionSensor: Boolean,
    canEnterPictureInPicture: Boolean,
    preview: PreviewFrame?,
    haptics: PlayerHaptics,
    onInteraction: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onScrubbing: (Boolean) -> Unit,
    onPreviewRequest: (Long?) -> Unit,
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
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var lastTick by remember { mutableIntStateOf(-1) }
    var showRemaining by rememberSaveable { mutableStateOf(false) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val positionFraction =
        if (dragging) dragFraction else (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val position = (positionFraction * duration).toLong()
    val tabular = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .background(scrimBottom)
                .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 32.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val width = maxWidth
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = formatTime(position), style = tabular)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text =
                                if (showRemaining) "−" + formatTime(state.durationMs - position)
                                else formatTime(state.durationMs),
                            style = tabular,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier =
                                Modifier.clip(CircleShape)
                                    .clickable {
                                        haptics.tap()
                                        showRemaining = !showRemaining
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    SeekBar(
                        position = positionFraction,
                        buffered = (state.bufferedMs.toFloat() / duration).coerceIn(0f, 1f),
                        enabled = state.durationMs > 0,
                        onValueChange = {
                            if (!dragging) {
                                dragging = true
                                lastTick = (it * SCRUB_TICKS).toInt()
                                haptics.gestureStart()
                                onScrubbing(true)
                            }
                            val tick = (it * SCRUB_TICKS).toInt()
                            if (tick != lastTick) {
                                lastTick = tick
                                haptics.tick()
                            }
                            dragFraction = it
                            onInteraction()
                            val target = (it * duration).toLong()
                            onSeekTo(target)
                            onPreviewRequest(target)
                        },
                        onValueChangeFinished = {
                            onSeekTo((dragFraction * duration).toLong())
                            onScrubbing(false)
                            onPreviewRequest(null)
                            haptics.gestureEnd()
                            dragging = false
                        },
                    )
                }
                ScrubPreview(
                    visible = dragging,
                    fraction = positionFraction,
                    trackWidth = width,
                    frame = preview,
                    timeLabel = formatTime(position),
                    showImage = state.renderProjection != ProjectionMode.FLAT || !state.isPlaying,
                )
            }

            ActionToolbar(
                state = state,
                aspectMode = aspectMode,
                gyroEnabled = gyroEnabled,
                hasMotionSensor = hasMotionSensor,
                canEnterPictureInPicture = canEnterPictureInPicture,
                haptics = haptics,
                onInteraction = onInteraction,
                onOpenSheet = onOpenSheet,
                onToggleLock = onToggleLock,
                onToggleRepeat = onToggleRepeat,
                onCycleAspect = onCycleAspect,
                onToggleGyro = onToggleGyro,
                onRecenter = onRecenter,
                onRotate = onRotate,
                onEnterPictureInPicture = onEnterPictureInPicture,
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp, bottom = 12.dp),
            )
        }
    }
}

/** The seek bar: the expressive slider with the buffered range drawn on its inactive track. */
@Composable
private fun SeekBar(
    position: Float,
    buffered: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors =
        SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            disabledInactiveTrackColor = Color.White.copy(alpha = 0.12f),
        )
    val bufferedColor = Color.White.copy(alpha = 0.22f)
    val density = LocalDensity.current
    val gap = with(density) { 6.dp.toPx() }
    val corner = with(density) { 2.dp.toPx() }
    Slider(
        value = position,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                colors = colors,
                enabled = enabled,
                thumbSize = DpSize(4.dp, 36.dp),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                colors = colors,
                enabled = enabled,
                drawStopIndicator = null,
                modifier =
                    Modifier.height(12.dp).drawWithContent {
                        drawContent()
                        val start = position * size.width + gap
                        val end = buffered * size.width
                        if (end > start) {
                            drawRoundRect(
                                color = bufferedColor,
                                topLeft = Offset(start, 0f),
                                size = Size(end - start, size.height),
                                cornerRadius = CornerRadius(corner),
                            )
                        }
                    },
            )
        },
    )
}

/** A still and the time under the finger, floating above the seek bar thumb. */
@Composable
private fun ScrubPreview(
    visible: Boolean,
    fraction: Float,
    trackWidth: androidx.compose.ui.unit.Dp,
    frame: PreviewFrame?,
    timeLabel: String,
    showImage: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    val cardWidth = 168.dp
    val density = LocalDensity.current
    val offsetX =
        with(density) {
            val max = (trackWidth - cardWidth).toPx().coerceAtLeast(0f)
            (fraction * trackWidth.toPx() - cardWidth.toPx() / 2).coerceIn(0f, max).roundToInt()
        }
    AnimatedVisibility(
        visible = visible,
        enter =
            scaleIn(motion.fastSpatialSpec(), initialScale = 0.8f) +
                fadeIn(motion.fastEffectsSpec()),
        exit =
            scaleOut(motion.fastSpatialSpec(), targetScale = 0.8f) +
                fadeOut(motion.fastEffectsSpec()),
        modifier = Modifier.offset { IntOffset(offsetX, with(density) { (-112).dp.roundToPx() }) },
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = PlayerGlass,
            modifier =
                Modifier.width(cardWidth)
                    .border(1.dp, Color.White.copy(alpha = 0.16f), MaterialTheme.shapes.large),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val image = frame?.image
                if (image != null && showImage) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier.fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(MaterialTheme.shapes.large),
                    )
                }
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * Secondary actions in one floating toolbar: playback options first, then what applies to this kind
 * of video (stereo output, motion and recenter for 360/180, fit for flat video), then the screen
 * controls. It scrolls if a narrow phone cannot fit them all.
 */
@Composable
private fun ActionToolbar(
    state: PlayerUiState,
    aspectMode: AspectMode,
    gyroEnabled: Boolean,
    hasMotionSensor: Boolean,
    canEnterPictureInPicture: Boolean,
    haptics: PlayerHaptics,
    onInteraction: () -> Unit,
    onOpenSheet: (PlayerSheet) -> Unit,
    onToggleLock: () -> Unit,
    onToggleRepeat: () -> Unit,
    onCycleAspect: () -> Unit,
    onToggleGyro: () -> Unit,
    onRecenter: () -> Unit,
    onRotate: () -> Unit,
    onEnterPictureInPicture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun act(block: () -> Unit) {
        haptics.tap()
        onInteraction()
        block()
    }
    val projection = state.renderProjection
    HorizontalFloatingToolbar(
        expanded = true,
        colors =
            FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = PlayerGlass,
                toolbarContentColor = Color.White,
            ),
        contentPadding = PaddingValues(horizontal = 8.dp),
        expandedShadowElevation = 0.dp,
        modifier = modifier.widthIn(max = 720.dp).horizontalScroll(rememberScrollState()),
    ) {
        TextButton(
            onClick = { act { onOpenSheet(PlayerSheet.Speed) } },
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.player_speed_value, speedLabel(state.speed)),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            )
        }
        PlayerIconToggle(
            checked = state.repeat,
            icon = if (state.repeat) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            label = stringResource(R.string.player_repeat),
            onCheckedChange = {
                haptics.toggle(it)
                onInteraction()
                onToggleRepeat()
            },
        )
        if (projection.isStereo || projection.isSpherical || state.depthPacked) {
            PlayerIconButton(
                icon = Icons.Rounded.ViewInAr,
                label = stringResource(R.string.player_stereo_output),
                onClick = { act { onOpenSheet(PlayerSheet.StereoOutput) } },
            )
        }
        if (projection.isSpherical) {
            if (hasMotionSensor) {
                PlayerIconToggle(
                    checked = gyroEnabled,
                    icon = Icons.Rounded.ScreenRotationAlt,
                    label = stringResource(R.string.player_motion),
                    onCheckedChange = {
                        haptics.toggle(it)
                        onInteraction()
                        onToggleGyro()
                    },
                )
            }
            PlayerIconButton(
                icon = Icons.Rounded.CenterFocusStrong,
                label = stringResource(R.string.player_recenter),
                onClick = { act(onRecenter) },
            )
        } else if (!projection.requiresImmersiveRendering) {
            PlayerIconButton(
                icon = Icons.Rounded.AspectRatio,
                label =
                    stringResource(
                        when (aspectMode) {
                            AspectMode.Fit -> R.string.player_aspect_fit
                            AspectMode.Fill -> R.string.player_aspect_fill
                            AspectMode.Stretch -> R.string.player_aspect_stretch
                        }
                    ),
                onClick = { act(onCycleAspect) },
            )
        }
        VerticalDivider(
            modifier = Modifier.height(24.dp).padding(horizontal = 4.dp),
            color = Color.White.copy(alpha = 0.24f),
        )
        PlayerIconButton(
            icon = Icons.Rounded.Lock,
            label = stringResource(R.string.player_lock),
            onClick = {
                haptics.toggle(true)
                onToggleLock()
            },
        )
        PlayerIconButton(
            icon = Icons.Rounded.ScreenRotation,
            label = stringResource(R.string.player_rotate),
            onClick = { act(onRotate) },
        )
        if (canEnterPictureInPicture) {
            PlayerIconButton(
                icon = Icons.Rounded.PictureInPictureAlt,
                label = stringResource(R.string.player_pip),
                onClick = {
                    haptics.tap()
                    onEnterPictureInPicture()
                },
            )
        }
    }
}

/** Icon button for controls on video, with a tooltip that also names it for accessibility. */
@Composable
internal fun PlayerIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glass: Boolean = false,
) {
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = if (glass) PlayerGlass else Color.Transparent,
                    contentColor = Color.White,
                ),
            modifier = modifier,
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}

/** On/off control on video: a filled container shows it is on, the shape squares when pressed. */
@Composable
private fun PlayerIconToggle(
    checked: Boolean,
    icon: ImageVector,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            shapes = IconButtonDefaults.toggleableShapes(),
            colors =
                IconButtonDefaults.iconToggleButtonColors(
                    contentColor = Color.White,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
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
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = resumedFromMs != null,
        enter =
            fadeIn(motion.defaultEffectsSpec()) +
                slideInVertically(motion.defaultSpatialSpec()) { -it },
        exit =
            fadeOut(motion.fastEffectsSpec()) +
                slideOutVertically(motion.fastSpatialSpec()) { -it },
        modifier = modifier,
    ) {
        Surface(shape = CircleShape, color = PlayerGlass, contentColor = Color.White) {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.player_resumed, formatTime(resumedFromMs ?: 0L)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onStartOver,
                    shapes = ButtonDefaults.shapes(),
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                ) {
                    Text(stringResource(R.string.player_start_over))
                }
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
        contentColor = MaterialTheme.colorScheme.onSurface,
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
            Spacer(Modifier.height(4.dp))
            Text(
                text = error.errorCodeName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onOpenExternally, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.player_open_externally))
                }
                Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

/** Double-tap seek ripples, scrub preview, brightness/volume level, the 2x hint. */
@Composable
internal fun PlayerFeedback(feedback: GestureFeedback?, durationMs: Long, preview: PreviewFrame?) {
    val motion = MaterialTheme.motionScheme
    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val seek = feedback as? GestureFeedback.Seek
        AnimatedVisibility(
            visible = seek != null && !seek.forward,
            enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), 0.7f),
            exit = fadeOut(motion.defaultEffectsSpec()),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 40.dp),
        ) {
            SeekBubble(seconds = seek?.seconds ?: 10, forward = false)
        }
        AnimatedVisibility(
            visible = seek != null && seek.forward,
            enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), 0.7f),
            exit = fadeOut(motion.defaultEffectsSpec()),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 40.dp),
        ) {
            SeekBubble(seconds = seek?.seconds ?: 10, forward = true)
        }

        val pill: (@Composable () -> Unit)? =
            when (feedback) {
                is GestureFeedback.Scrub -> {
                    {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val image = preview?.image
                            if (image != null) {
                                Image(
                                    bitmap = image,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier.width(200.dp)
                                            .aspectRatio(16f / 9f)
                                            .clip(MaterialTheme.shapes.large),
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            val sign = if (feedback.deltaMs >= 0) "+" else "−"
                            Text(
                                text =
                                    "$sign${formatTime(kotlin.math.abs(feedback.deltaMs))}  ·  " +
                                        "${formatTime(feedback.targetMs)} / ${formatTime(durationMs)}",
                                style =
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontFeatureSettings = "tnum"
                                    ),
                            )
                        }
                    }
                }
                is GestureFeedback.Brightness -> {
                    {
                        LevelPill(
                            icon =
                                when {
                                    feedback.level < 0.34f -> Icons.Rounded.BrightnessLow
                                    feedback.level < 0.67f -> Icons.Rounded.BrightnessMedium
                                    else -> Icons.Rounded.BrightnessHigh
                                },
                            label = stringResource(R.string.player_brightness),
                            level = feedback.level,
                        )
                    }
                }
                is GestureFeedback.Volume -> {
                    {
                        LevelPill(
                            icon =
                                when {
                                    feedback.level <= 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                                    feedback.level < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                                    else -> Icons.AutoMirrored.Rounded.VolumeUp
                                },
                            label = stringResource(R.string.player_volume),
                            level = feedback.level,
                        )
                    }
                }
                GestureFeedback.FastForward -> {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("2×", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            Chevrons(forward = true)
                        }
                    }
                }
                else -> null
            }
        AnimatedVisibility(
            visible = pill != null,
            enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), 0.9f),
            exit = fadeOut(motion.defaultEffectsSpec()),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = PlayerGlass,
                contentColor = Color.White,
            ) {
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    pill?.invoke()
                }
            }
        }
    }
}

@Composable
private fun SeekBubble(seconds: Int, forward: Boolean) {
    Surface(
        shape = CircleShape,
        color = PlayerGlass,
        contentColor = Color.White,
        modifier = Modifier.size(112.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Chevrons(forward = forward)
            Spacer(Modifier.height(4.dp))
            Text(
                text =
                    stringResource(
                        R.string.player_seek_amount,
                        if (forward) "+$seconds" else "−$seconds",
                    ),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            )
        }
    }
}

/** Three chevrons lighting up one after the other in the direction of travel. */
@Composable
private fun Chevrons(forward: Boolean) {
    val transition = rememberInfiniteTransition(label = "chevrons")
    val phase by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Restart),
            label = "chevronPhase",
        )
    Row {
        repeat(3) { index ->
            val order = if (forward) index else 2 - index
            val distance = ((phase - order + 3f) % 3f)
            val alpha = 1f - (distance / 3f) * 0.75f
            Icon(
                if (forward) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft,
                contentDescription = null,
                tint = Color.White.copy(alpha = alpha),
                modifier = Modifier.size(22.dp).padding(horizontal = 0.dp),
            )
        }
    }
}

@Composable
private fun LevelPill(icon: ImageVector, label: String, level: Float) {
    val animated by
        animateFloatAsState(
            targetValue = level,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "level",
        )
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(targetState = icon, label = "levelIcon") {
            Icon(it, contentDescription = label, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.width(160.dp).height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.22f),
            drawStopIndicator = {},
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${(level * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.width(44.dp),
        )
    }
}

/** Label for each stereo output choice, used by the sheet. */
@Composable
internal fun StereoOutputMode.label(): String =
    stringResource(
        when (this) {
            StereoOutputMode.SingleEye -> R.string.player_stereo_output_single
            StereoOutputMode.SplitScreen -> R.string.player_stereo_output_split
            StereoOutputMode.Anaglyph -> R.string.player_stereo_output_anaglyph
            StereoOutputMode.Parallax -> R.string.player_stereo_output_parallax
        }
    )
