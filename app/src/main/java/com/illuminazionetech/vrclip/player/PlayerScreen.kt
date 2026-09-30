@file:OptIn(UnstableApi::class)

package com.illuminazionetech.vrclip.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.illuminazionetech.vrclip.player.gl.RenderConfig
import com.illuminazionetech.vrclip.player.gl.StereoOutputMode
import com.illuminazionetech.vrclip.player.gl.VideoGLSurfaceView
import com.illuminazionetech.vrclip.util.FileUtil
import com.illuminazionetech.vrclip.util.PLAYER_GYRO
import com.illuminazionetech.vrclip.util.PreferenceUtil
import com.illuminazionetech.vrclip.util.PreferenceUtil.getBoolean
import kotlin.math.abs
import kotlinx.coroutines.delay

/** How flat video fills the screen. */
enum class AspectMode(val resizeMode: Int) {
    Fit(AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Fill(AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    Stretch(AspectRatioFrameLayout.RESIZE_MODE_FILL),
}

/** Transient feedback shown in the middle or on the sides of the video. */
internal sealed interface GestureFeedback {
    data class Seek(val forward: Boolean, val seconds: Int) : GestureFeedback

    data class Scrub(val targetMs: Long, val deltaMs: Long) : GestureFeedback

    data class Brightness(val level: Float) : GestureFeedback

    data class Volume(val level: Float) : GestureFeedback

    data object FastForward : GestureFeedback
}

/**
 * The phone/tablet player: the video (a SurfaceView for plain video, [VideoGLSurfaceView] for 3D
 * and 360/180), a gesture layer (tap to show the controls, double tap to seek, long press for 2x,
 * horizontal swipe to scrub, vertical swipes for brightness and volume, drag and pinch to look
 * around and zoom in immersive video) and the controls on top.
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    isInPictureInPicture: Boolean,
    canEnterPictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    onNavigateBack: () -> Unit,
    onRequestOrientation: (Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<GestureFeedback?>(null) }
    var feedbackKey by remember { mutableIntStateOf(0) }
    var aspectMode by remember { mutableStateOf(AspectMode.Fit) }
    var gyroEnabled by remember { mutableStateOf(PLAYER_GYRO.getBoolean(true)) }
    var glView by remember { mutableStateOf<VideoGLSurfaceView?>(null) }
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    var orientationLocked by remember { mutableStateOf(false) }

    val immersive = state.renderProjection.requiresImmersiveRendering
    val spherical = state.renderProjection.isSpherical

    fun showFeedback(value: GestureFeedback) {
        feedback = value
        feedbackKey++
    }

    fun poke() {
        interaction++
    }

    // Keep the screen awake while a video plays.
    DisposableEffect(state.isPlaying) {
        view.keepScreenOn = state.isPlaying
        onDispose { view.keepScreenOn = false }
    }

    // Controls fade away after a few seconds of playback without interaction.
    LaunchedEffect(controlsVisible, state.isPlaying, interaction, sheet, locked) {
        if (controlsVisible && state.isPlaying && sheet == null) {
            delay(if (locked) 1_500 else 3_500)
            controlsVisible = false
        }
    }
    LaunchedEffect(feedbackKey) {
        if (feedback != null && feedback !is GestureFeedback.FastForward) {
            delay(700)
            feedback = null
        }
    }

    // On a phone, turn to match the video the first time its shape is known.
    val isPhone = configuration.smallestScreenWidthDp < 600
    LaunchedEffect(state.frame?.aspect, spherical, state.stereoOutput) {
        if (orientationLocked) return@LaunchedEffect
        val aspect = state.frame?.aspect ?: return@LaunchedEffect
        onRequestOrientation(
            when {
                state.stereoOutput == StereoOutputMode.SplitScreen -> PlayerActivity.ORIENTATION_LANDSCAPE
                !isPhone || spherical -> PlayerActivity.ORIENTATION_UNSPECIFIED
                aspect > 1.05f -> PlayerActivity.ORIENTATION_LANDSCAPE
                aspect < 0.95f -> PlayerActivity.ORIENTATION_PORTRAIT
                else -> PlayerActivity.ORIENTATION_UNSPECIFIED
            }
        )
    }

    BackHandler(enabled = sheet != null) { sheet = null }

    val brightness = remember { BrightnessController(context) }
    val volume = remember { VolumeController(context) }
    DisposableEffect(Unit) { onDispose { brightness.restore() } }

    // Controls sit on video, not on a surface: give them light content explicitly.
    CompositionLocalProvider(LocalContentColor provides Color.White) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VideoLayer(
            viewModel = viewModel,
            state = state,
            aspectMode = aspectMode,
            gyroEnabled = gyroEnabled,
            onGlView = { glView = it },
        )

        if (!isInPictureInPicture) {
            GestureLayer(
                locked = locked,
                immersive = immersive,
                onTap = {
                    controlsVisible = !controlsVisible
                    poke()
                },
                onDoubleTap = { forward ->
                    if (locked) return@GestureLayer
                    viewModel.seekBy(if (forward) PlayerViewModel.SEEK_STEP_MS else -PlayerViewModel.SEEK_STEP_MS)
                    val previous = feedback as? GestureFeedback.Seek
                    val seconds =
                        if (previous != null && previous.forward == forward) previous.seconds + 10 else 10
                    showFeedback(GestureFeedback.Seek(forward, seconds))
                },
                onLongPress = { pressed ->
                    if (pressed && state.isPlaying) {
                        viewModel.setTemporarySpeed(2f)
                        feedback = GestureFeedback.FastForward
                    } else if (!pressed && feedback == GestureFeedback.FastForward) {
                        viewModel.setTemporarySpeed(null)
                        feedback = null
                    }
                },
                onPan = { delta -> glView?.pan(delta.x, delta.y) },
                onZoom = { factor ->
                    if (immersive) glView?.zoom(factor)
                    else if (factor > 1.08f) aspectMode = AspectMode.Fill
                    else if (factor < 0.92f) aspectMode = AspectMode.Fit
                },
                onVerticalDrag = { left, fraction ->
                    if (left) showFeedback(GestureFeedback.Brightness(brightness.adjust(context, fraction)))
                    else showFeedback(GestureFeedback.Volume(volume.adjust(fraction)))
                },
                onHorizontalDrag = { fraction ->
                    val duration = state.durationMs
                    if (duration > 0) {
                        val range = duration.coerceAtMost(SCRUB_RANGE_MS)
                        val current = feedback as? GestureFeedback.Scrub
                        val delta = (current?.deltaMs ?: 0L) + (fraction * range).toLong()
                        val target = (state.positionMs + delta).coerceIn(0L, duration)
                        showFeedback(GestureFeedback.Scrub(target, target - state.positionMs))
                    }
                },
                onDragEnd = {
                    (feedback as? GestureFeedback.Scrub)?.let { viewModel.seekTo(it.targetMs) }
                },
            )

            PlayerFeedback(feedback = feedback, durationMs = state.durationMs)

            PlayerControls(
                state = state,
                visible = controlsVisible,
                locked = locked,
                aspectMode = aspectMode,
                gyroEnabled = gyroEnabled,
                hasMotionSensor = glView?.hasMotionSensor == true,
                canEnterPictureInPicture =
                    canEnterPictureInPicture && !immersive && state.error == null,
                onInteraction = ::poke,
                onNavigateBack = onNavigateBack,
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeekBy = viewModel::seekBy,
                onSeekTo = viewModel::seekTo,
                onScrubbing = viewModel::setScrubbing,
                onToggleLive3d = { viewModel.setLive3d(state.live3d == Live3dState.Off) },
                onOpenSheet = { sheet = it },
                onToggleLock = {
                    locked = !locked
                    controlsVisible = true
                    poke()
                },
                onToggleRepeat = viewModel::toggleRepeat,
                onCycleAspect = {
                    aspectMode = AspectMode.entries[(aspectMode.ordinal + 1) % AspectMode.entries.size]
                },
                onToggleGyro = {
                    gyroEnabled = !gyroEnabled
                    PreferenceUtil.updateValue(PLAYER_GYRO, gyroEnabled)
                },
                onRecenter = { glView?.recenter() },
                onRotate = {
                    orientationLocked = true
                    onRequestOrientation(
                        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                            PlayerActivity.ORIENTATION_PORTRAIT
                        else PlayerActivity.ORIENTATION_LANDSCAPE
                    )
                },
                onEnterPictureInPicture = {
                    controlsVisible = false
                    onEnterPictureInPicture()
                },
                onStartOver = viewModel::startOver,
                onDismissResume = viewModel::consumeResumeHint,
                onRetry = viewModel::retry,
                onOpenExternally = {
                    state.videoPath?.let { path -> FileUtil.openFile(path) {} }
                },
            )

            PlayerSheets(
                sheet = sheet,
                state = state,
                viewModel = viewModel,
                onDismiss = { sheet = null },
                onShare = {
                    state.videoPath?.let { path ->
                        FileUtil.createIntentForSharingFile(path)?.let {
                            context.startActivity(Intent.createChooser(it, null))
                        }
                    }
                },
                onOpenExternally = { state.videoPath?.let { path -> FileUtil.openFile(path) {} } },
                onOpenSheet = { sheet = it },
                onDeleted = onNavigateBack,
            )

            Live3dMessages(state = state, viewModel = viewModel)
        }
    }
    }
}

private const val SCRUB_RANGE_MS = 120_000L

@Composable
private fun VideoLayer(
    viewModel: PlayerViewModel,
    state: PlayerUiState,
    aspectMode: AspectMode,
    gyroEnabled: Boolean,
    onGlView: (VideoGLSurfaceView?) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    if (state.renderProjection.requiresImmersiveRendering) {
        var view by remember { mutableStateOf<VideoGLSurfaceView?>(null) }
        DisposableEffect(lifecycleOwner, view) {
            val target = view ?: return@DisposableEffect onDispose {}
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> target.onResume()
                    Lifecycle.Event.ON_PAUSE -> target.onPause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                target.onResume()
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                target.onPause()
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                VideoGLSurfaceView(context).apply {
                    onSurfaceAvailable = viewModel::attachSurface
                    onSurfaceDestroyed = viewModel::detachSurface
                    view = this
                    onGlView(this)
                }
            },
            update = { glView ->
                glView.setConfig(
                    RenderConfig(
                        mode = state.renderProjection,
                        output = state.stereoOutput,
                        frameAspect = state.renderFrameAspect,
                    )
                )
                glView.gyroEnabled = gyroEnabled
                glView.bufferSize = state.outputBufferSize
            },
            onRelease = { onGlView(null) },
        )
        if (state.stereoOutput != StereoOutputMode.SplitScreen) {
            SubtitleOverlay(player = viewModel.player)
        }
    } else {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                    player = viewModel.player
                }
            },
            update = { playerView ->
                playerView.resizeMode = aspectMode.resizeMode
                if (playerView.player !== viewModel.player) playerView.player = viewModel.player
            },
            onRelease = { it.player = null },
        )
    }
}

/** Subtitles drawn over the immersive view, which has no subtitle rendering of its own. */
@Composable
private fun SubtitleOverlay(player: Player) {
    var subtitleView by remember { mutableStateOf<SubtitleView?>(null) }
    DisposableEffect(player, subtitleView) {
        val target = subtitleView ?: return@DisposableEffect onDispose {}
        val listener =
            object : Player.Listener {
                override fun onCues(cueGroup: CueGroup) {
                    target.setCues(cueGroup.cues)
                }
            }
        player.addListener(listener)
        target.setCues(player.currentCues.cues)
        onDispose { player.removeListener(listener) }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            SubtitleView(context).apply {
                setUserDefaultStyle()
                setUserDefaultTextSize()
                subtitleView = this
            }
        },
    )
}

@Composable
private fun GestureLayer(
    locked: Boolean,
    immersive: Boolean,
    onTap: () -> Unit,
    onDoubleTap: (forward: Boolean) -> Unit,
    onLongPress: (pressed: Boolean) -> Unit,
    onPan: (Offset) -> Unit,
    onZoom: (Float) -> Unit,
    onVerticalDrag: (left: Boolean, fraction: Float) -> Unit,
    onHorizontalDrag: (fraction: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val tap by rememberUpdatedState(onTap)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val longPress by rememberUpdatedState(onLongPress)
    val pan by rememberUpdatedState(onPan)
    val zoom by rememberUpdatedState(onZoom)
    val vertical by rememberUpdatedState(onVerticalDrag)
    val horizontal by rememberUpdatedState(onHorizontalDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)

    Box(
        modifier =
            Modifier.fillMaxSize()
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = { tap() },
                        onDoubleTap =
                            if (locked) null
                            else { offset -> doubleTap(offset.x > size.width / 2f) },
                        onLongPress = if (locked) null else { _ -> longPress(true) },
                        onPress = {
                            tryAwaitRelease()
                            longPress(false)
                        },
                    )
                }
                .pointerInput(locked, immersive) {
                    if (locked) return@pointerInput
                    detectDragAndPinch(
                        touchSlop = touchSlop,
                        immersive = immersive,
                        onPan = { pan(it) },
                        onZoom = { zoom(it) },
                        onVertical = { left, fraction -> vertical(left, fraction) },
                        onHorizontal = { horizontal(it) },
                        onEnd = { dragEnd() },
                    )
                }
    )
}

private enum class DragAxis {
    Horizontal,
    Vertical,
    Free,
    Pinch,
}

/**
 * One finger: in immersive video it looks around; in plain video the first direction decides
 * between scrubbing (horizontal) and brightness/volume (vertical, by screen half). Two fingers
 * pinch to zoom. Movements are consumed only after the touch slop, so taps still work.
 */
private suspend fun PointerInputScope.detectDragAndPinch(
    touchSlop: Float,
    immersive: Boolean,
    onPan: (Offset) -> Unit,
    onZoom: (Float) -> Unit,
    onVertical: (left: Boolean, fraction: Float) -> Unit,
    onHorizontal: (fraction: Float) -> Unit,
    onEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startsLeft = down.position.x < size.width / 2f
        var axis: DragAxis? = null
        var travelled = Offset.Zero
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) {
                axis = DragAxis.Pinch
                val factor = event.calculateZoom()
                if (factor != 1f) onZoom(factor)
                event.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
                continue
            }
            if (axis == DragAxis.Pinch) continue
            val delta = event.calculatePan()
            if (axis == null) {
                travelled += delta
                if (travelled.getDistance() > touchSlop) {
                    axis =
                        when {
                            immersive -> DragAxis.Free
                            abs(travelled.x) > abs(travelled.y) -> DragAxis.Horizontal
                            else -> DragAxis.Vertical
                        }
                }
            }
            when (axis) {
                DragAxis.Free -> onPan(delta)
                DragAxis.Horizontal -> onHorizontal(delta.x / size.width)
                DragAxis.Vertical -> onVertical(startsLeft, -delta.y / size.height)
                else -> Unit
            }
            if (axis != null) {
                event.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (axis != null) onEnd()
    }
}

/** Screen brightness for this window only; given back to the system when the player closes. */
private class BrightnessController(context: Context) {
    private val activity = context as? Activity
    private var level: Float = -1f

    fun adjust(context: Context, fraction: Float): Float {
        val window = activity?.window ?: return 0f
        if (level < 0f) {
            val current = window.attributes.screenBrightness
            level =
                if (current >= 0f) current
                else
                    runCatching {
                            Settings.System.getInt(
                                context.contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS,
                            ) / 255f
                        }
                        .getOrDefault(0.5f)
        }
        level = (level + fraction * 1.2f).coerceIn(0.01f, 1f)
        window.attributes = window.attributes.apply { screenBrightness = level }
        return level
    }

    fun restore() {
        val window = activity?.window ?: return
        window.attributes =
            window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
    }
}

private class VolumeController(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val max = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1
    private var level = -1f

    fun adjust(fraction: Float): Float {
        val manager = audio ?: return 0f
        if (level < 0f) level = manager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
        level = (level + fraction * 1.2f).coerceIn(0f, 1f)
        runCatching {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, (level * max + 0.5f).toInt(), 0)
        }
        return level
    }
}
