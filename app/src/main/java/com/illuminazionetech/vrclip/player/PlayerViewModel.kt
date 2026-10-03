@file:OptIn(UnstableApi::class)

package com.illuminazionetech.vrclip.player

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.Surface
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.session.MediaSession
import com.illuminazionetech.vrclip.App
import com.illuminazionetech.vrclip.database.objects.DownloadedVideoInfo
import com.illuminazionetech.vrclip.player.gl.StereoOutputMode
import com.illuminazionetech.vrclip.player.stereo.DepthModelManager
import com.illuminazionetech.vrclip.player.stereo.LiveStereoStatus
import com.illuminazionetech.vrclip.player.stereo.StereoConversionEffect
import com.illuminazionetech.vrclip.player.stereo.StereoSettings
import com.illuminazionetech.vrclip.util.DatabaseUtil
import com.illuminazionetech.vrclip.util.PLAYER_CARDBOARD_DEFAULT
import com.illuminazionetech.vrclip.util.PLAYER_SPEED
import com.illuminazionetech.vrclip.util.PreferenceUtil
import com.illuminazionetech.vrclip.util.PreferenceUtil.getBoolean
import com.illuminazionetech.vrclip.util.PreferenceUtil.getFloat
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the player was asked to open. */
sealed interface PlayerSource {
    /** A video from the VRClip library. */
    data class Library(val id: Int) : PlayerSource

    /** A file handed over by another app ("Open with"). */
    data class External(val uri: Uri, val title: String?) : PlayerSource
}

/** A still from the video, shown above the seek bar while scrubbing. */
class PreviewFrame(val positionMs: Long, val image: ImageBitmap)

/** What a scrub preview needs, read from the player on its own thread. */
private class PreviewRequest(
    val uri: Uri,
    val positionMs: Long,
    val aspect: Float,
    val projection: ProjectionMode,
)

data class TrackOption(
    val group: Tracks.Group,
    val index: Int,
    val label: String,
    val selected: Boolean,
)

enum class Live3dState {
    Off,
    /** The depth model is not on the device yet. */
    NeedsModel,
    Starting,
    On,
    Failed,
}

data class PlayerUiState(
    val title: String = "",
    val videoPath: String? = null,
    /** File name used for projection detection (the display name for shared files). */
    val detectionName: String = "",
    val libraryId: Int? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = true,
    val isBuffering: Boolean = true,
    val ended: Boolean = false,
    val positionMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val repeat: Boolean = false,
    val frame: FrameInfo? = null,
    val detection: Detection = Detection(ProjectionMode.FLAT, DetectionSource.Default),
    val projectionOverride: ProjectionMode? = null,
    val stereoOutput: StereoOutputMode = StereoOutputMode.SingleEye,
    val live3d: Live3dState = Live3dState.Off,
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
    val subtitlesOff: Boolean = true,
    /** Set once when playback resumed from a saved position, for the "start over" hint. */
    val resumedFromMs: Long? = null,
    val error: PlaybackException? = null,
    /** Size the immersive view's surface buffer must have while video effects draw into it. */
    val outputBufferSize: Pair<Int, Int>? = null,
    /**
     * Live 2D to 3D draws on a flat screen (phone, tablet, a panel on Quest): the effect hands over
     * the picture with its depth and the screen's renderer draws the views. False for the immersive
     * Quest player, which shows both views of a side-by-side frame on its stereo layer.
     */
    val liveOnScreen: Boolean = true,
) {
    private val liveActive: Boolean
        get() = live3d == Live3dState.On

    /**
     * Live conversion on a screen: each frame comes with its depth map below it. Already true while
     * the effect starts, so the renderer is in place before its first frame arrives.
     */
    val depthPacked: Boolean
        get() = liveOnScreen && (live3d == Live3dState.On || live3d == Live3dState.Starting)

    /** Stereo, spherical or depth-packed frames need the GL renderer instead of a plain view. */
    val usesGlRenderer: Boolean
        get() = renderProjection.requiresImmersiveRendering || depthPacked

    /** The projection of the file itself, before any live 2D to 3D conversion. */
    val sourceProjection: ProjectionMode
        get() = projectionOverride ?: detection.mode

    /**
     * What the renderer gets: for the immersive player live conversion turns a flat picture into a
     * full side-by-side one; on a screen the picture stays flat and carries its depth.
     */
    val renderProjection: ProjectionMode
        get() = if (liveActive && !liveOnScreen) ProjectionMode.SBS_3D else sourceProjection

    val renderFrameAspect: Float
        get() {
            val aspect = frame?.aspect?.takeIf { it > 0f } ?: (16f / 9f)
            return if (liveActive && !liveOnScreen) aspect * 2f else aspect
        }

    /** Live conversion only makes sense for flat, non-stereo video. */
    val canConvertLive: Boolean
        get() = sourceProjection == ProjectionMode.FLAT && frame != null
}

/**
 * Owns the [ExoPlayer] for the phone/tablet player: loading, resume position, speed, tracks
 * (including subtitle files yt-dlp saved next to the video), projection detection from the decoded
 * format, live 2D to 3D conversion, and a [MediaSession] so headset buttons, Bluetooth controls and
 * the system media controls work.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    val player: ExoPlayer =
        ExoPlayer.Builder(application)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus= */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build()

    private val session: MediaSession =
        // Session IDs must be unique per process, and two players can briefly coexist (the phone
        // player handing over to the immersive one, or an old instance not yet cleared).
        MediaSession.Builder(application, player)
            .setId("vrclip-player-${sessionCounter.incrementAndGet()}")
            .build()

    private val mutableState = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = mutableState.asStateFlow()

    private var source: PlayerSource? = null
    private var effectsPipelineCreated = false
    private var surface: Surface? = null
    private var info: DownloadedVideoInfo? = null
    private var tickerJob: Job? = null
    private var lastSavedPosition = -1L

    private val previewRequests = MutableStateFlow<PreviewRequest?>(null)
    private val mutablePreview = MutableStateFlow<PreviewFrame?>(null)
    val preview: StateFlow<PreviewFrame?> = mutablePreview.asStateFlow()

    /** Guards the retriever, which is used on a background thread and released on the main one. */
    private val previewLock = Any()
    private var previewRetriever: MediaMetadataRetriever? = null
    private var previewUri: Uri? = null
    private var previewClosed = false

    private val listener =
        object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                syncFromPlayer()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                updateFrame()
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateFrame()
                updateTracks(tracks)
            }

            override fun onPlayerError(error: PlaybackException) {
                if (
                    mutableState.value.live3d == Live3dState.On ||
                        mutableState.value.live3d == Live3dState.Starting
                ) {
                    // The effects pipeline is the most likely culprit; fall back to plain playback.
                    mutableState.update { it.copy(live3d = Live3dState.Failed) }
                    restartWithEffects(enabled = false)
                } else {
                    mutableState.update { it.copy(error = error) }
                }
            }

            override fun onRenderedFirstFrame() {
                if (mutableState.value.live3d == Live3dState.Starting) {
                    mutableState.update { it.copy(live3d = Live3dState.On) }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startTicker() else savePosition()
            }
        }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Only the latest request matters while the finger moves; each one waits for the
            // previous decode, since a retriever is not thread safe.
            previewRequests.collectLatest { request ->
                if (request == null) {
                    mutablePreview.value = null
                    return@collectLatest
                }
                delay(PREVIEW_DEBOUNCE_MS)
                val frame = runCatching { decodePreview(request) }.getOrNull()
                if (frame != null) mutablePreview.value = frame
            }
        }
        viewModelScope.launch {
            LiveStereoStatus.state.collect { status ->
                val live = mutableState.value.live3d
                if (
                    status is LiveStereoStatus.Status.Failed &&
                        (live == Live3dState.On || live == Live3dState.Starting)
                ) {
                    mutableState.update { it.copy(live3d = Live3dState.Failed) }
                    restartWithEffects(enabled = false)
                    applyDefaultOutput()
                }
            }
        }
        player.addListener(listener)
        val speed = PLAYER_SPEED.getFloat(1f).coerceIn(0.25f, 3f)
        player.playbackParameters = PlaybackParameters(speed)
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setPreferredAudioLanguage(Locale.getDefault().language)
                .build()
    }

    fun load(newSource: PlayerSource) {
        if (newSource == source) return
        savePosition()
        source = newSource
        viewModelScope.launch {
            when (newSource) {
                is PlayerSource.Library -> {
                    val loaded =
                        runCatching { DatabaseUtil.getInfoById(newSource.id) }.getOrNull()
                            ?: run {
                                mutableState.update {
                                    it.copy(
                                        error =
                                            PlaybackException(
                                                "missing",
                                                null,
                                                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                                            )
                                    )
                                }
                                return@launch
                            }
                    info = loaded
                    open(
                        uri = pathToUri(loaded.videoPath),
                        path = loaded.videoPath,
                        title = loaded.videoTitle,
                        artist = loaded.videoAuthor,
                        override = ProjectionMode.fromStorageKey(loaded.projectionOverride),
                        resumeMs = loaded.playbackPositionMs,
                        knownDurationMs = loaded.playbackDurationMs,
                    )
                }
                is PlayerSource.External -> {
                    info = null
                    open(
                        uri = newSource.uri,
                        path = newSource.uri.toString(),
                        detectionName = newSource.title ?: newSource.uri.lastPathSegment.orEmpty(),
                        title = newSource.title ?: newSource.uri.lastPathSegment.orEmpty(),
                        artist = null,
                        override = null,
                        resumeMs = 0L,
                        knownDurationMs = 0L,
                    )
                }
            }
        }
    }

    private suspend fun open(
        uri: Uri,
        path: String,
        detectionName: String = path,
        title: String,
        artist: String?,
        override: ProjectionMode?,
        resumeMs: Long,
        knownDurationMs: Long,
    ) {
        val subtitles = withContext(Dispatchers.IO) { findSubtitleFiles(path) }
        val item =
            MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build())
                .setSubtitleConfigurations(subtitles)
                .build()
        // Resume unless the video was (nearly) finished or barely started.
        val resume = resumeMs.takeIf {
            it >= MIN_RESUME_MS && (knownDurationMs <= 0 || it < knownDurationMs - END_THRESHOLD_MS)
        }
        mutableState.value =
            PlayerUiState(
                title = title,
                videoPath = path,
                detectionName = detectionName,
                libraryId = info?.id,
                speed = player.playbackParameters.speed,
                repeat = player.repeatMode == Player.REPEAT_MODE_ONE,
                detection = ProjectionDetector.detect(detectionName, null),
                projectionOverride = override,
                resumedFromMs = resume,
            )
        applyDefaultOutput()
        // The effects pipeline stays once created; make sure a previous video's effect is gone.
        if (effectsPipelineCreated) player.setVideoEffects(emptyList())
        player.setMediaItem(item, resume ?: 0L)
        player.prepare()
        player.playWhenReady = true
    }

    // region Transport

    fun togglePlayPause() {
        val current = player
        if (current.playbackState == Player.STATE_ENDED) {
            current.seekTo(0)
            current.play()
        } else if (current.isPlaying || current.playWhenReady) current.pause() else current.play()
    }

    fun pause() = player.pause()

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceIn(0L, player.duration.coerceAtLeast(0L)))
        syncFromPlayer()
    }

    fun seekBy(deltaMs: Long) = seekTo(player.currentPosition + deltaMs)

    fun setScrubbing(scrubbing: Boolean) {
        player.isScrubbingModeEnabled = scrubbing
    }

    fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
        PreferenceUtil.encodeFloat(PLAYER_SPEED, speed)
        syncFromPlayer()
    }

    /** Temporary speed while a long press lasts; does not touch the saved preference. */
    fun setTemporarySpeed(speed: Float?) {
        player.playbackParameters =
            PlaybackParameters(speed ?: PLAYER_SPEED.getFloat(1f).coerceIn(0.25f, 3f))
        syncFromPlayer()
    }

    fun toggleRepeat() {
        player.repeatMode =
            if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF
            else Player.REPEAT_MODE_ONE
        syncFromPlayer()
    }

    fun startOver() {
        mutableState.update { it.copy(resumedFromMs = null) }
        seekTo(0)
    }

    fun consumeResumeHint() = mutableState.update { it.copy(resumedFromMs = null) }

    fun retry() {
        mutableState.update { it.copy(error = null) }
        player.prepare()
        player.play()
    }

    // endregion

    // region Video output

    fun attachSurface(newSurface: Surface) {
        surface = newSurface
        player.setVideoSurface(newSurface)
        signalOutputResolution()
    }

    fun detachSurface(oldSurface: Surface) {
        if (surface == oldSurface) surface = null
        player.clearVideoSurface(oldSurface)
    }

    /**
     * With video effects, frames are drawn into the output surface by GL instead of the decoder, so
     * the player has to be told how big that surface is when it is a plain [Surface] (the immersive
     * view's SurfaceTexture). The view sizes its buffer from [PlayerUiState.outputBufferSize].
     */
    private fun signalOutputResolution() {
        val size = mutableState.value.outputBufferSize ?: return
        for (index in 0 until player.rendererCount) {
            if (player.getRendererType(index) != C.TRACK_TYPE_VIDEO) continue
            player
                .createMessage(player.getRenderer(index))
                .setType(Renderer.MSG_SET_VIDEO_OUTPUT_RESOLUTION)
                .setPayload(Size(size.first, size.second))
                .send()
        }
    }

    fun setProjectionOverride(mode: ProjectionMode?) {
        val path = mutableState.value.videoPath
        mutableState.update { it.copy(projectionOverride = mode) }
        if (
            mode != null &&
                mode != ProjectionMode.FLAT &&
                mutableState.value.live3d != Live3dState.Off
        ) {
            setLive3d(false)
        }
        applyDefaultOutput()
        if (info == null || path == null) return
        info = info?.copy(projectionOverride = mode?.name)
        viewModelScope.launch { DatabaseUtil.updateProjectionOverride(path, mode?.name) }
    }

    fun setStereoOutput(output: StereoOutputMode) {
        mutableState.update { it.copy(stereoOutput = output) }
    }

    /**
     * Where live 2D to 3D goes: the immersive Quest player calls this with true before loading, so
     * the effect draws side-by-side views for its stereo layer instead of a picture with depth.
     */
    fun setLiveTarget(headset: Boolean) {
        mutableState.update { it.copy(liveOnScreen = !headset) }
    }

    private fun applyDefaultOutput() {
        val current = mutableState.value
        val live = current.live3d == Live3dState.On || current.live3d == Live3dState.Starting
        val output =
            when {
                // Without a viewer or glasses, motion parallax is the 3D a flat screen can show.
                live && current.liveOnScreen ->
                    if (PLAYER_CARDBOARD_DEFAULT.getBoolean()) StereoOutputMode.SplitScreen
                    else StereoOutputMode.Parallax
                !current.renderProjection.isStereo && !current.renderProjection.isSpherical ->
                    StereoOutputMode.SingleEye
                PLAYER_CARDBOARD_DEFAULT.getBoolean() -> StereoOutputMode.SplitScreen
                else -> StereoOutputMode.SingleEye
            }
        mutableState.update { it.copy(stereoOutput = output) }
    }

    /**
     * Turns live 2D to 3D conversion on or off. Video effects can only be swapped while the player
     * is idle, so this re-prepares at the current position; the pause is a fraction of a second.
     */
    fun setLive3d(enabled: Boolean) {
        val current = mutableState.value
        if (enabled) {
            if (!current.canConvertLive) return
            if (!DepthModelManager.get(getApplication()).isReady()) {
                mutableState.update { it.copy(live3d = Live3dState.NeedsModel) }
                return
            }
            mutableState.update { it.copy(live3d = Live3dState.Starting) }
        } else {
            mutableState.update { it.copy(live3d = Live3dState.Off) }
        }
        applyDefaultOutput()
        restartWithEffects(enabled)
    }

    fun dismissLive3dMessage() {
        mutableState.update {
            if (it.live3d == Live3dState.NeedsModel || it.live3d == Live3dState.Failed)
                it.copy(live3d = Live3dState.Off)
            else it
        }
    }

    private fun restartWithEffects(enabled: Boolean) {
        val position = player.currentPosition
        val playWhenReady = player.playWhenReady
        val source = mutableState.value.frame
        val output =
            if (mutableState.value.liveOnScreen) StereoConversionEffect.Output.LiveColorAndDepth
            else StereoConversionEffect.Output.LiveSideBySide
        mutableState.update {
            it.copy(
                outputBufferSize =
                    if (enabled && source != null)
                        StereoConversionEffect.outputSize(source.width, source.height, output)
                    else null
            )
        }
        player.stop()
        effectsPipelineCreated = true
        player.setVideoEffects(
            if (enabled)
                listOf(
                    StereoConversionEffect(
                        model = DepthModelManager.get(getApplication()),
                        settings = StereoSettings.load(),
                        output = output,
                    )
                )
            else emptyList()
        )
        signalOutputResolution()
        player.seekTo(position)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    // endregion

    // region Tracks

    fun selectTrack(option: TrackOption) {
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(option.group.type, false)
                .setOverrideForType(
                    TrackSelectionOverride(option.group.mediaTrackGroup, option.index)
                )
                .build()
    }

    fun disableSubtitles() {
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
    }

    private fun updateTracks(tracks: Tracks) {
        fun options(type: Int): List<TrackOption> =
            tracks.groups
                .filter { it.type == type }
                .flatMap { group ->
                    (0 until group.length)
                        .filter { group.isTrackSupported(it) }
                        .map { index ->
                            TrackOption(
                                group = group,
                                index = index,
                                label = describe(group.getTrackFormat(index), type),
                                selected = group.isTrackSelected(index),
                            )
                        }
                }
        val text = options(C.TRACK_TYPE_TEXT)
        mutableState.update {
            it.copy(
                audioTracks = options(C.TRACK_TYPE_AUDIO),
                textTracks = text,
                subtitlesOff = text.none { option -> option.selected },
            )
        }
    }

    private fun describe(format: Format, type: Int): String {
        val parts = mutableListOf<String>()
        val language =
            format.language
                ?.takeUnless { it == C.LANGUAGE_UNDETERMINED }
                ?.let { Locale.forLanguageTag(it).getDisplayName(Locale.getDefault()) }
                ?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        format.label?.takeIf { it.isNotBlank() }?.let(parts::add)
        if (language != null && parts.none { it.equals(language, ignoreCase = true) }) {
            parts.add(language)
        }
        if (type == C.TRACK_TYPE_AUDIO) {
            when (format.channelCount) {
                1 -> parts.add("Mono")
                2 -> parts.add("Stereo")
                6 -> parts.add("5.1")
                8 -> parts.add("7.1")
            }
        }
        if (parts.isEmpty()) parts.add(format.id ?: "#${format.hashCode() and 0xFFFF}")
        return parts.joinToString(" · ")
    }

    // endregion

    private fun updateFrame() {
        val format = player.videoFormat
        val size = player.videoSize
        // With live conversion the reported video size is the converted (double width) frame, so
        // the source size comes from the decoder's input format, turned upright.
        val live =
            mutableState.value.live3d.let { it == Live3dState.On || it == Live3dState.Starting }
        val rotated =
            format != null && (format.rotationDegrees == 90 || format.rotationDegrees == 270)
        val width: Int
        val height: Int
        if (live && format != null && format.width > 0) {
            width = if (rotated) format.height else format.width
            height = if (rotated) format.width else format.height
        } else {
            width = size.width.takeIf { it > 0 } ?: format?.width ?: 0
            height = size.height.takeIf { it > 0 } ?: format?.height ?: 0
        }
        if (width <= 0 || height <= 0) return
        val frame =
            FrameInfo(
                width = width,
                height = height,
                pixelWidthHeightRatio =
                    size.pixelWidthHeightRatio.takeIf { it > 0f }
                        ?: format?.pixelWidthHeightRatio?.takeIf { it > 0f }
                        ?: 1f,
                stereoMode = format?.stereoMode ?: Format.NO_VALUE,
                projectionData = format?.projectionData,
                frameRate = format?.frameRate?.takeIf { it > 0f } ?: 0f,
            )
        val current = mutableState.value
        if (current.frame == frame) return
        val path = current.detectionName
        val wasImmersive = current.usesGlRenderer
        mutableState.update {
            it.copy(frame = frame, detection = ProjectionDetector.detect(path, frame))
        }
        if (wasImmersive != mutableState.value.usesGlRenderer) {
            applyDefaultOutput()
        }
    }

    private fun syncFromPlayer() {
        val p = player
        mutableState.update {
            it.copy(
                isPlaying = p.isPlaying,
                playWhenReady = p.playWhenReady,
                isBuffering = p.playbackState == Player.STATE_BUFFERING,
                ended = p.playbackState == Player.STATE_ENDED,
                positionMs = p.currentPosition.coerceAtLeast(0L),
                bufferedMs = p.bufferedPosition.coerceAtLeast(0L),
                durationMs = p.duration.takeIf { d -> d != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L,
                speed = p.playbackParameters.speed,
                repeat = p.repeatMode == Player.REPEAT_MODE_ONE,
            )
        }
        if (p.playbackState == Player.STATE_ENDED) savePosition()
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            var sinceSave = 0L
            while (isActive && player.isPlaying) {
                syncFromPlayer()
                delay(TICK_MS)
                sinceSave += TICK_MS
                if (sinceSave >= SAVE_INTERVAL_MS) {
                    sinceSave = 0L
                    savePosition()
                }
            }
            syncFromPlayer()
        }
    }

    // region Scrub preview

    /**
     * Asks for a still at [positionMs] for the scrub preview; null hides it. Called on the main
     * thread, where the player may be read; the frame is decoded in the background.
     */
    fun requestPreview(positionMs: Long?) {
        val uri = player.currentMediaItem?.localConfiguration?.uri
        previewRequests.value =
            if (positionMs == null || uri == null) null
            else {
                val current = mutableState.value
                PreviewRequest(
                    uri = uri,
                    positionMs = positionMs,
                    aspect = current.frame?.aspect?.takeIf { it > 0f } ?: (16f / 9f),
                    projection = current.sourceProjection,
                )
            }
    }

    private fun decodePreview(request: PreviewRequest): PreviewFrame? =
        synchronized(previewLock) {
            if (previewClosed) return null
            val retriever =
                previewRetriever?.takeIf { previewUri == request.uri }
                    ?: MediaMetadataRetriever().also {
                        previewRetriever?.release()
                        previewRetriever = null
                        it.setDataSource(getApplication(), request.uri)
                        previewRetriever = it
                        previewUri = request.uri
                    }
            val width = PREVIEW_DECODE_WIDTH
            val height = (width / request.aspect).toInt().coerceAtLeast(1)
            val bitmap =
                retriever.getScaledFrameAtTime(
                    request.positionMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    width,
                    height,
                ) ?: return null
            PreviewFrame(
                request.positionMs,
                cropToView(bitmap, request.projection).asImageBitmap(),
            )
        }

    /** The part of a frame a viewer would look at: one eye, and the front of a 360/180 video. */
    private fun cropToView(bitmap: Bitmap, mode: ProjectionMode): Bitmap {
        var left = 0f
        var top = 0f
        var right = 1f
        var bottom = 1f
        when (mode.stereoLayout) {
            StereoLayout.LeftRight -> right = 0.5f
            StereoLayout.TopBottom -> bottom = 0.5f
            StereoLayout.None -> Unit
        }
        if (mode.isSpherical) {
            // The middle of an equirectangular frame is straight ahead; keep roughly the
            // field of view of a phone held in landscape.
            val w = right - left
            val h = bottom - top
            val keepW = if (mode.is360) 0.3f else 0.55f
            left += w * (1 - keepW) / 2
            right -= w * (1 - keepW) / 2
            top += h * 0.2f
            bottom -= h * 0.2f
        }
        if (left == 0f && top == 0f && right == 1f && bottom == 1f) return bitmap
        val x = (left * bitmap.width).toInt()
        val y = (top * bitmap.height).toInt()
        val w = ((right - left) * bitmap.width).toInt().coerceAtLeast(1)
        val h = ((bottom - top) * bitmap.height).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(bitmap, x, y, w, h)
    }

    // endregion

    /** Stores where playback is, or clears it once the video has been watched to the end. */
    fun savePosition() {
        val id = info?.id ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        val position = player.currentPosition
        val finished =
            player.playbackState == Player.STATE_ENDED || position >= duration - END_THRESHOLD_MS
        val toSave = if (finished) 0L else position
        if (toSave == lastSavedPosition) return
        lastSavedPosition = toSave
        // The application scope outlives this view model, so the last save survives onCleared.
        App.applicationScope.launch {
            runCatching { DatabaseUtil.updatePlaybackPosition(id, toSave, duration) }
        }
    }

    override fun onCleared() {
        savePosition()
        synchronized(previewLock) {
            previewClosed = true
            previewRetriever?.release()
            previewRetriever = null
        }
        player.removeListener(listener)
        session.release()
        player.release()
    }

    companion object {
        private val sessionCounter = java.util.concurrent.atomic.AtomicInteger()
        const val SEEK_STEP_MS = 10_000L
        private const val PREVIEW_DEBOUNCE_MS = 40L
        private const val PREVIEW_DECODE_WIDTH = 384
        private const val TICK_MS = 250L
        private const val SAVE_INTERVAL_MS = 5_000L
        private const val MIN_RESUME_MS = 5_000L
        private const val END_THRESHOLD_MS = 5_000L

        private val subtitleMimeTypes =
            mapOf(
                "vtt" to MimeTypes.TEXT_VTT,
                "srt" to MimeTypes.APPLICATION_SUBRIP,
                "ass" to MimeTypes.TEXT_SSA,
                "ssa" to MimeTypes.TEXT_SSA,
                "ttml" to MimeTypes.APPLICATION_TTML,
            )

        private fun pathToUri(path: String): Uri =
            if (path.startsWith("content://") || path.startsWith("file://")) path.toUri()
            else Uri.fromFile(File(path))

        /**
         * yt-dlp writes subtitles next to the video as `<name>.<language>.<ext>` when they are not
         * embedded; offer them as extra text tracks.
         */
        internal fun findSubtitleFiles(videoPath: String): List<MediaItem.SubtitleConfiguration> {
            if (videoPath.startsWith("content://")) return emptyList()
            val video = File(videoPath.removePrefix("file://"))
            val directory = video.parentFile ?: return emptyList()
            val base = video.nameWithoutExtension
            val files = runCatching { directory.listFiles() }.getOrNull() ?: return emptyList()
            return files
                .filter { file ->
                    file.isFile &&
                        file.name.startsWith("$base.") &&
                        file.extension.lowercase() in subtitleMimeTypes
                }
                .sortedBy { it.name }
                .map { file ->
                    val language =
                        file.name.removePrefix("$base.").substringBeforeLast('.').takeIf {
                            it.isNotEmpty() && !it.contains('.')
                        }
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                        .setMimeType(subtitleMimeTypes.getValue(file.extension.lowercase()))
                        .setLanguage(language)
                        .setLabel(
                            language
                                ?.let { Locale.forLanguageTag(it) }
                                ?.getDisplayName(Locale.getDefault())
                                ?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                        )
                        .build()
                }
        }
    }
}
