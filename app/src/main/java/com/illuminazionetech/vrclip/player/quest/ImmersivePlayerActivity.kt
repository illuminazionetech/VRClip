package com.illuminazionetech.vrclip.player.quest

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.runtime.StereoMode
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.Equirect180ShapeOptions
import com.meta.spatial.toolkit.Equirect360ShapeOptions
import com.meta.spatial.toolkit.MediaPanelRenderOptions
import com.meta.spatial.toolkit.MediaPanelSettings
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PixelDisplayOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.VideoSurfacePanelRegistration
import com.meta.spatial.vr.VRFeature
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.player.ProjectionDetector
import com.illuminazionetech.vrclip.player.ProjectionMode

/**
 * Full immersive Meta Spatial SDK scene for 360°/180°/stereo-3D/flat video playback on Quest —
 * launched by [com.illuminazionetech.vrclip.player.PlayerLauncher] instead of the flat-panel [com.illuminazionetech.vrclip.player.PlayerScreen]
 * whenever the app is running on a Quest headset. A [VideoSurfacePanelRegistration] projects the
 * decoded ExoPlayer frames onto an equirectangular/half-dome/flat mesh sized and stereo-configured
 * from the video's [ProjectionMode]; the user exits via the standard Quest system gesture (no
 * custom in-scene UI is registered here to keep this first immersive pass small and reliable).
 */
class ImmersivePlayerActivity : AppSystemActivity() {

    private var exoPlayer: ExoPlayer? = null

    override fun registerFeatures(): List<SpatialFeature> = listOf(VRFeature(this), ComposeFeature())

    override fun registerPanels(): List<PanelRegistration> {
        val videoPath = intent.getStringExtra(EXTRA_VIDEO_PATH)
        val projection =
            ProjectionMode.fromStorageKey(intent.getStringExtra(EXTRA_PROJECTION))
                ?: ProjectionDetector.detectProjection(videoPath.orEmpty())

        return listOf(
            VideoSurfacePanelRegistration(
                R.id.panel_video,
                surfaceConsumer = { _, surface ->
                    exoPlayer?.release()
                    exoPlayer =
                        ExoPlayer.Builder(this).build().apply {
                            repeatMode = Player.REPEAT_MODE_ONE
                            setVideoSurface(surface)
                            if (videoPath != null) {
                                setMediaItem(MediaItem.fromUri(Uri.parse(videoPath)))
                                prepare()
                                playWhenReady = true
                            }
                        }
                },
                settingsCreator = {
                    MediaPanelSettings(
                        shape = shapeFor(projection),
                        display = PixelDisplayOptions(width = 100, height = 100),
                        rendering = MediaPanelRenderOptions(stereoMode = stereoModeFor(projection)),
                    )
                },
            )
        )
    }

    private fun shapeFor(mode: ProjectionMode) =
        when {
            mode.is360 -> Equirect360ShapeOptions(radius = 300f)
            mode.is180 -> Equirect180ShapeOptions(radius = 300f)
            else -> QuadShapeOptions(width = 4f, height = 2.25f)
        }

    private fun stereoModeFor(mode: ProjectionMode): StereoMode =
        when (mode) {
            ProjectionMode.STEREO_360_TB,
            ProjectionMode.STEREO_180_TB,
            ProjectionMode.OU_3D -> StereoMode.UpDown

            ProjectionMode.STEREO_360_LR,
            ProjectionMode.STEREO_180_LR,
            ProjectionMode.SBS_3D -> StereoMode.LeftRight

            else -> StereoMode.None
        }

    override fun onDestroy() {
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VIDEO_ID = "com.illuminazionetech.vrclip.player.quest.EXTRA_VIDEO_ID"
        const val EXTRA_VIDEO_PATH = "com.illuminazionetech.vrclip.player.quest.EXTRA_VIDEO_PATH"
        const val EXTRA_PROJECTION = "com.illuminazionetech.vrclip.player.quest.EXTRA_PROJECTION"
    }
}
