package com.xrclip.player

import android.content.Context
import android.net.Uri
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlaybackUiState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
)

/**
 * Thin, single-instance wrapper around a [androidx.media3.exoplayer.ExoPlayer], shared by the
 * in-app phone/tablet [com.xrclip.player.gl.VideoGLSurfaceView] (which binds it to a custom GL
 * surface for 360/180/3D rendering) and the stock `PlayerView` path used for flat video.
 */
class PlayerEngine(context: Context) {

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

    private val mutableState = MutableStateFlow(PlaybackUiState())
    val state = mutableState.asStateFlow()

    init {
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    mutableState.update { it.copy(isPlaying = isPlaying) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    mutableState.update {
                        it.copy(
                            isBuffering = playbackState == Player.STATE_BUFFERING,
                            durationMs = exoPlayer.duration.coerceAtLeast(0L),
                        )
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    mutableState.update { it.copy(error = error.message) }
                }
            }
        )
    }

    fun play(path: String) {
        mutableState.update { PlaybackUiState() }
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(path)))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun setVideoSurface(surface: Surface?) {
        exoPlayer.setVideoSurface(surface)
    }

    fun togglePlayPause() {
        exoPlayer.playWhenReady = !exoPlayer.playWhenReady
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    /** Call periodically (e.g. from a UI-side ticker) since ExoPlayer doesn't push position. */
    fun pollPosition() {
        mutableState.update { it.copy(positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)) }
    }

    fun release() {
        exoPlayer.release()
    }
}
