package com.illuminazionetech.vrclip.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Vrpano
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import android.content.Intent
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.database.objects.DownloadedVideoInfo
import com.illuminazionetech.vrclip.player.gl.StereoOutputMode
import com.illuminazionetech.vrclip.player.gl.VideoGLSurfaceView
import com.illuminazionetech.vrclip.ui.component.BackButton
import com.illuminazionetech.vrclip.util.DatabaseUtil
import com.illuminazionetech.vrclip.util.FileUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * In-app phone/tablet player for downloaded videos. Flat video plays through a stock
 * [PlayerView]; 360/180/stereo-3D content routes through [VideoGLSurfaceView] instead. On Meta
 * Quest, [PlayerLauncher] sends the user to the immersive Spatial SDK player instead of this
 * screen.
 */
@Composable
fun PlayerScreen(
    videoId: Int,
    onNavigateBack: () -> Unit,
    playerEngine: PlayerEngine = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var info by remember { mutableStateOf<DownloadedVideoInfo?>(null) }
    var projectionMode by remember { mutableStateOf(ProjectionMode.FLAT) }
    var outputMode by remember { mutableStateOf(StereoOutputMode.SingleEye) }
    var showProjectionMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val playbackState by playerEngine.state.collectAsStateWithLifecycle()

    LaunchedEffect(videoId) {
        val loaded = DatabaseUtil.getInfoById(videoId)
        info = loaded
        val override = ProjectionMode.fromStorageKey(loaded.projectionOverride)
        projectionMode = override ?: ProjectionDetector.detectProjection(loaded.videoPath)
        playerEngine.play(loaded.videoPath)
    }

    LaunchedEffect(Unit) {
        while (true) {
            playerEngine.pollPosition()
            delay(500)
        }
    }

    DisposableEffect(Unit) { onDispose { playerEngine.setVideoSurface(null) } }

    fun applyProjectionOverride(mode: ProjectionMode?) {
        val path = info?.videoPath ?: return
        projectionMode = mode ?: ProjectionDetector.detectProjection(path)
        scope.launch { DatabaseUtil.updateProjectionOverride(path, mode?.name) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (projectionMode.requiresImmersiveRendering) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoGLSurfaceView(ctx).apply {
                        setProjectionMode(projectionMode)
                        setOutputMode(outputMode)
                        onSurfaceReady = { surface -> playerEngine.setVideoSurface(surface) }
                    }
                },
                update = { view ->
                    view.setProjectionMode(projectionMode)
                    view.setOutputMode(outputMode)
                },
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = playerEngine.exoPlayer
                        useController = false
                    }
                },
                onRelease = { it.player = null },
            )
        }

        PlayerTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = info?.videoTitle.orEmpty(),
            projectionLabel = projectionMode.displayName(),
            onNavigateBack = onNavigateBack,
            showProjectionMenu = showProjectionMenu,
            onShowProjectionMenu = { showProjectionMenu = true },
            projectionMenu = {
                ProjectionMenu(
                    current = ProjectionMode.fromStorageKey(info?.projectionOverride),
                    onDismiss = { showProjectionMenu = false },
                    onSelect = {
                        applyProjectionOverride(it)
                        showProjectionMenu = false
                    },
                )
            },
            showOverflowMenu = showOverflowMenu,
            onShowOverflowMenu = { showOverflowMenu = true },
            overflowMenu = {
                PlayerOverflowMenu(
                    onDismiss = { showOverflowMenu = false },
                    onOpenExternally = {
                        showOverflowMenu = false
                        info?.videoPath?.let { path -> FileUtil.openFile(path) {} }
                    },
                    onShare = {
                        showOverflowMenu = false
                        info?.videoPath?.let { path ->
                            FileUtil.createIntentForSharingFile(path)?.let {
                                context.startActivity(Intent.createChooser(it, null))
                            }
                        }
                    },
                    onDelete = {
                        showOverflowMenu = false
                        info?.let { current ->
                            scope.launch {
                                DatabaseUtil.deleteInfoList(listOf(current), deleteFile = true)
                            }
                        }
                        onNavigateBack()
                    },
                )
            },
        )

        PlayerBottomControls(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            isPlaying = playbackState.isPlaying,
            positionMs = playbackState.positionMs,
            durationMs = playbackState.durationMs,
            isStereo = projectionMode.isStereo,
            outputMode = outputMode,
            onTogglePlayPause = playerEngine::togglePlayPause,
            onSeek = playerEngine::seekTo,
            onToggleOutputMode = {
                outputMode =
                    if (outputMode == StereoOutputMode.SingleEye) StereoOutputMode.SplitScreen
                    else StereoOutputMode.SingleEye
            },
        )

        playbackState.error?.let { error ->
            Text(
                text = stringResource(R.string.player_error, error),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
    }
}

@Composable
private fun PlayerTopBar(
    modifier: Modifier,
    title: String,
    projectionLabel: String,
    onNavigateBack: () -> Unit,
    showProjectionMenu: Boolean,
    onShowProjectionMenu: () -> Unit,
    projectionMenu: @Composable () -> Unit,
    showOverflowMenu: Boolean,
    onShowOverflowMenu: () -> Unit,
    overflowMenu: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        color = Color.Black.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onNavigateBack)
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Box {
                IconButton(onClick = onShowProjectionMenu) {
                    Icon(
                        Icons.Rounded.Public,
                        contentDescription = projectionLabel,
                        tint = Color.White,
                    )
                }
                if (showProjectionMenu) projectionMenu()
            }
            Box {
                IconButton(onClick = onShowOverflowMenu) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = Color.White)
                }
                if (showOverflowMenu) overflowMenu()
            }
        }
    }
}

@Composable
private fun PlayerOverflowMenu(
    onDismiss: () -> Unit,
    onOpenExternally: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.player_open_externally)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
            onClick = onOpenExternally,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.share)) },
            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
            onClick = onShare,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete)) },
            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            onClick = onDelete,
        )
    }
}

@Composable
private fun PlayerBottomControls(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    isStereo: Boolean,
    outputMode: StereoOutputMode,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleOutputMode: () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                Slider(
                    modifier = Modifier.weight(1f),
                    value = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
                    onValueChange = { fraction -> onSeek((fraction * durationMs).toLong()) },
                )
                if (isStereo) {
                    IconButton(onClick = onToggleOutputMode) {
                        Icon(
                            imageVector = Icons.Rounded.Vrpano,
                            contentDescription =
                                stringResource(
                                    if (outputMode == StereoOutputMode.SplitScreen)
                                        R.string.player_stereo_output_split
                                    else R.string.player_stereo_output_single
                                ),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}
