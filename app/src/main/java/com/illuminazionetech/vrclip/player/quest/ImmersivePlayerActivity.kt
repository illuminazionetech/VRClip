package com.illuminazionetech.vrclip.player.quest

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedDispatcher
import android.view.KeyEvent
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.illuminazionetech.vrclip.MainActivity
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.player.FrameInfo
import com.illuminazionetech.vrclip.player.PlayerSource
import com.illuminazionetech.vrclip.player.PlayerUiState
import com.illuminazionetech.vrclip.player.PlayerViewModel
import com.illuminazionetech.vrclip.player.ProjectionDetector
import com.illuminazionetech.vrclip.player.ProjectionMode
import com.illuminazionetech.vrclip.player.StereoLayout
import com.illuminazionetech.vrclip.ui.common.LocalIsVRMode
import com.illuminazionetech.vrclip.ui.common.SettingsProvider
import com.illuminazionetech.vrclip.ui.theme.VRClipTheme
import com.illuminazionetech.vrclip.util.PLAYER_QUEST_PASSTHROUGH_DEFAULT
import com.illuminazionetech.vrclip.util.PreferenceUtil
import com.illuminazionetech.vrclip.util.PreferenceUtil.getBoolean
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.runtime.StereoMode
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.DpPerMeterDisplayOptions
import com.meta.spatial.toolkit.Equirect180ShapeOptions
import com.meta.spatial.toolkit.Equirect360ShapeOptions
import com.meta.spatial.toolkit.Grabbable
import com.meta.spatial.toolkit.MediaPanelRenderOptions
import com.meta.spatial.toolkit.MediaPanelSettings
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PixelDisplayOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.toolkit.Visible
import com.meta.spatial.toolkit.VideoSurfacePanelRegistration
import com.meta.spatial.vr.VRFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Immersive Meta Quest player built on the Meta Spatial SDK: flat and stereo 3D video on a large
 * virtual screen (native stereo, one picture per eye), 360/180 video wrapped around the viewer,
 * live 2D to 3D conversion shown in real stereo, optional passthrough behind flat video, and a
 * grabbable control bar. Playback logic (resume, tracks, live conversion) is the same
 * [PlayerViewModel] the phone player uses.
 */
class ImmersivePlayerActivity : AppSystemActivity() {

    private val store = ViewModelStore()
    private val viewModel: PlayerViewModel by lazy {
        ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[
            PlayerViewModel::class.java]
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var videoEntity: Entity? = null
    private var controlsEntity: Entity? = null
    private var panelConfig: VideoPanelConfig? = null
    private var placed = false
    private var ticksWaiting = 0
    private var passthrough = false

    /** Everything the video panel is built from; a change means rebuilding the panel. */
    private data class VideoPanelConfig(
        val mode: ProjectionMode,
        val width: Int,
        val height: Int,
        val eyeAspect: Float,
    )

    override fun registerFeatures(): List<SpatialFeature> =
        listOf(VRFeature(this), ComposeFeature())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        passthrough = PLAYER_QUEST_PASSTHROUGH_DEFAULT.getBoolean(false)
        registerBackHandler()
        load(intent)
        scope.launch {
            viewModel.state
                .map { configFor(it) }
                .distinctUntilChanged()
                .collect { config -> if (config != null && placed) rebuildVideoPanel(config) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        load(intent)
    }

    private fun load(intent: Intent) {
        val id = intent.getIntExtra(EXTRA_VIDEO_ID, -1)
        val path = intent.getStringExtra(EXTRA_VIDEO_PATH)
        val source =
            when {
                id >= 0 -> PlayerSource.Library(id)
                path != null ->
                    PlayerSource.External(
                        if (path.startsWith("content://") || path.startsWith("file://")) path.toUri()
                        else Uri.fromFile(java.io.File(path)),
                        intent.getStringExtra(EXTRA_TITLE),
                    )
                else -> null
            }
        if (source == null) {
            finish()
            return
        }
        // A projection chosen elsewhere (library menu) applies before the file is even opened.
        ProjectionMode.fromStorageKey(intent.getStringExtra(EXTRA_PROJECTION))?.let {
            viewModel.setProjectionOverride(it)
        }
        viewModel.load(source)
    }

    override fun registerPanels(): List<PanelRegistration> =
        listOf(
            VideoSurfacePanelRegistration(
                R.id.panel_video,
                surfaceConsumer = { _, surface -> viewModel.attachSurface(surface) },
                settingsCreator = { panelSettings(panelConfig ?: fallbackConfig()) },
            ),
            ComposeViewPanelRegistration(
                R.id.panel_controls,
                composeViewCreator = { _, context ->
                    ComposeView(context).apply {
                        setContent {
                            SettingsProvider(WindowWidthSizeClass.Expanded) {
                                CompositionLocalProvider(LocalIsVRMode provides true) {
                                    VRClipTheme(darkTheme = true) {
                                        ImmersiveControls(
                                            viewModel = viewModel,
                                            passthrough = passthrough,
                                            onTogglePassthrough = ::togglePassthrough,
                                            onRecenter = ::placeEntities,
                                            onClose = ::returnToLibrary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                settingsCreator = {
                    UIPanelSettings(
                        shape = QuadShapeOptions(width = CONTROLS_WIDTH, height = CONTROLS_HEIGHT),
                        display = DpPerMeterDisplayOptions(dpPerMeter = CONTROLS_DP_PER_METER),
                    )
                },
            ),
        )

    override fun onSceneReady() {
        super.onSceneReady()
        scene.setReferenceSpace(ReferenceSpace.LOCAL)
        applyPassthrough()
    }

    override fun onSceneTick() {
        super.onSceneTick()
        // Wait until the headset reports a real pose, so the screen lands where the user looks.
        if (!placed) {
            ticksWaiting++
            val pose = scene.getViewerPose()
            val tracked = pose.t.x != 0f || pose.t.y != 0f || pose.t.z != 0f || pose.q.w != 1f
            if (tracked || ticksWaiting > 90) {
                placed = true
                panelConfig = configFor(viewModel.state.value) ?: fallbackConfig()
                createEntities()
            }
        }
    }

    private fun createEntities() {
        val config = panelConfig ?: return
        videoEntity = Entity.create(listOf(Panel(R.id.panel_video), Transform(Pose())))
        controlsEntity =
            Entity.create(
                listOf(
                    Panel(R.id.panel_controls),
                    Transform(Pose()),
                    Grabbable(),
                    Visible(true),
                )
            )
        placeEntities()
        applyPassthrough(config)
    }

    /** Puts the screen (or the sphere) and the control bar in front of where the user looks. */
    private fun placeEntities() {
        val config = panelConfig ?: return
        val head = scene.getViewerPose().removePitchAndRoll()
        val forward = head.forward().let { Vector3(it.x, 0f, it.z) }.normalizedOr(Vector3(0f, 0f, 1f))
        val facing = head.q
        val controls: Vector3
        if (config.mode.isSpherical) {
            videoEntity?.setComponent(Transform(Pose(head.t, facing)))
            controls = head.t + forward * 0.9f + Vector3(0f, -0.45f, 0f)
        } else {
            val screenHeight = SCREEN_WIDTH / config.eyeAspect.coerceIn(0.4f, 4f)
            videoEntity?.setComponent(Transform(Pose(head.t + forward * SCREEN_DISTANCE, facing)))
            controls =
                head.t + forward * (SCREEN_DISTANCE - 1.4f) +
                    Vector3(0f, -(screenHeight / 2f).coerceAtMost(0.6f) - 0.2f, 0f)
        }
        // Turned toward the eyes, so the bar below eye level tilts up to face the viewer.
        val towardPanel = (controls - head.t).normalizedOr(forward)
        controlsEntity?.setComponent(
            Transform(Pose(controls, Quaternion.fromDirection(towardPanel, Vector3(0f, 1f, 0f))))
        )
    }

    private fun rebuildVideoPanel(config: VideoPanelConfig) {
        if (config == panelConfig && videoEntity != null) return
        val old = videoEntity
        old?.let { entity ->
            // The old surface goes away with its panel; the new panel hands over a new one.
            entity.destroy()
        }
        panelConfig = config
        videoEntity = Entity.create(listOf(Panel(R.id.panel_video), Transform(Pose())))
        placeEntities()
        applyPassthrough(config)
    }

    private fun configFor(state: PlayerUiState): VideoPanelConfig? {
        val frame = state.frame ?: return null
        val mode = state.renderProjection
        val (width, height) =
            state.outputBufferSize
                ?: (frame.width to frame.height)
        return VideoPanelConfig(
            mode = mode,
            width = width,
            height = height,
            eyeAspect = ProjectionMode.eyeAspectRatio(mode, state.renderFrameAspect),
        )
    }

    /** Before the player knows the frame, read it from the file so the first panel is right. */
    private fun fallbackConfig(): VideoPanelConfig {
        val state = viewModel.state.value
        val frame = probeFrame(state.videoPath) ?: FrameInfo(1920, 1080)
        val mode =
            state.projectionOverride
                ?: ProjectionDetector.detect(state.detectionName.ifEmpty { state.videoPath.orEmpty() }, frame).mode
        return VideoPanelConfig(
            mode = mode,
            width = frame.width,
            height = frame.height,
            eyeAspect = ProjectionMode.eyeAspectRatio(mode, frame.aspect),
        )
    }

    private fun probeFrame(path: String?): FrameInfo? {
        path ?: return null
        val extractor = MediaExtractor()
        return try {
            if (path.startsWith("content://")) extractor.setDataSource(this, path.toUri(), null)
            else extractor.setDataSource(path.removePrefix("file://"))
            (0 until extractor.trackCount)
                .map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                ?.let { format ->
                    val w = format.getInteger(MediaFormat.KEY_WIDTH)
                    val h = format.getInteger(MediaFormat.KEY_HEIGHT)
                    val rotation =
                        if (format.containsKey(MediaFormat.KEY_ROTATION))
                            format.getInteger(MediaFormat.KEY_ROTATION)
                        else 0
                    if (rotation % 180 == 0) FrameInfo(w, h) else FrameInfo(h, w)
                }
        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    private fun panelSettings(config: VideoPanelConfig): MediaPanelSettings {
        val shape =
            when {
                config.mode.is360 -> Equirect360ShapeOptions(radius = SPHERE_RADIUS)
                config.mode.is180 -> Equirect180ShapeOptions(radius = SPHERE_RADIUS)
                else ->
                    QuadShapeOptions(
                        width = SCREEN_WIDTH,
                        height = SCREEN_WIDTH / config.eyeAspect.coerceIn(0.4f, 4f),
                    )
            }
        val stereo =
            when (config.mode.stereoLayout) {
                StereoLayout.LeftRight -> StereoMode.LeftRight
                StereoLayout.TopBottom -> StereoMode.UpDown
                StereoLayout.None -> StereoMode.None
            }
        return MediaPanelSettings(
            shape = shape,
            // The swapchain must match the video: a smaller one would blur the picture.
            display = PixelDisplayOptions(width = config.width, height = config.height),
            rendering = MediaPanelRenderOptions(stereoMode = stereo),
        )
    }

    private fun togglePassthrough() {
        passthrough = !passthrough
        PreferenceUtil.updateValue(PLAYER_QUEST_PASSTHROUGH_DEFAULT, passthrough)
        applyPassthrough()
    }

    /** Passthrough only makes sense behind a screen; 360/180 video covers the whole view. */
    private fun applyPassthrough(config: VideoPanelConfig? = panelConfig) {
        val spherical = config?.mode?.isSpherical == true
        runCatching { scene.enablePassthrough(passthrough && !spherical) }
    }

    /** Back leaves the player for the library (predictive back callback on Android 13+). */
    private fun registerBackHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                returnToLibrary()
            }
        }
    }

    // VrActivity is a plain Activity (no OnBackPressedDispatcher): below Android 13 this is the
    // only way to see Back, and from 13 on the callback above takes over.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Only reached below Android 13; newer versions use the back callback.")
    override fun onBackPressed() {
        returnToLibrary()
    }

    /**
     * Leaves the immersive player and brings the VRClip library back as a panel in the Horizon
     * home environment (the way Meta's hybrid sample hands over from immersive to 2D).
     */
    private fun returnToLibrary() {
        viewModel.savePosition()
        val panel =
            Intent(applicationContext, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending =
            PendingIntent.getActivity(
                applicationContext,
                0,
                panel,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("extra_launch_in_home_pending_intent", pending)
            )
        }
        finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Controller buttons bring the control bar back if it was hidden.
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_BUTTON_X -> controlsEntity?.setComponent(Visible(true))
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        viewModel.pause()
        viewModel.savePosition()
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        store.clear()
        super.onDestroy()
    }

    private fun Vector3.normalizedOr(fallback: Vector3): Vector3 {
        val length = kotlin.math.sqrt(x * x + y * y + z * z)
        return if (length < 1e-4f) fallback else Vector3(x / length, y / length, z / length)
    }

    companion object {
        const val EXTRA_VIDEO_ID = "com.illuminazionetech.vrclip.player.quest.EXTRA_VIDEO_ID"
        const val EXTRA_VIDEO_PATH = "com.illuminazionetech.vrclip.player.quest.EXTRA_VIDEO_PATH"
        const val EXTRA_PROJECTION = "com.illuminazionetech.vrclip.player.quest.EXTRA_PROJECTION"
        const val EXTRA_TITLE = "com.illuminazionetech.vrclip.player.quest.EXTRA_TITLE"

        /** A 3.2 m wide screen 3 m away covers about 56°, like a large cinema seat. */
        private const val SCREEN_WIDTH = 3.2f
        private const val SCREEN_DISTANCE = 3f
        private const val SPHERE_RADIUS = 50f
        private const val CONTROLS_WIDTH = 1.1f
        private const val CONTROLS_HEIGHT = 0.24f
        private const val CONTROLS_DP_PER_METER = 900f
    }
}
