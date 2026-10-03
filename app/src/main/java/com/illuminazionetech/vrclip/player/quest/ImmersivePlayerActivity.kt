package com.illuminazionetech.vrclip.player.quest

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.window.OnBackInvokedDispatcher
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.illuminazionetech.vrclip.MainActivity
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.player.FrameInfo
import com.illuminazionetech.vrclip.player.Live3dState
import com.illuminazionetech.vrclip.player.PlayerSource
import com.illuminazionetech.vrclip.player.PlayerUiState
import com.illuminazionetech.vrclip.player.PlayerViewModel
import com.illuminazionetech.vrclip.player.ProjectionDetector
import com.illuminazionetech.vrclip.player.ProjectionMode
import com.illuminazionetech.vrclip.player.StereoLayout
import com.illuminazionetech.vrclip.player.SubtitleOverlay
import com.illuminazionetech.vrclip.ui.common.Haptic
import com.illuminazionetech.vrclip.ui.common.LocalIsVRMode
import com.illuminazionetech.vrclip.ui.common.SettingsProvider
import com.illuminazionetech.vrclip.ui.theme.VRClipTheme
import com.illuminazionetech.vrclip.util.PLAYER_QUEST_PASSTHROUGH_DEFAULT
import com.illuminazionetech.vrclip.util.PreferenceUtil
import com.illuminazionetech.vrclip.util.PreferenceUtil.getBoolean
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.core.Entity
import com.meta.spatial.core.PerformanceLevel
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.SpatialSDKExperimentalAPI
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.runtime.LayerFilters
import com.meta.spatial.runtime.PanelShapeLayerBlendType
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.runtime.Scene
import com.meta.spatial.runtime.SessionState
import com.meta.spatial.runtime.StereoMode
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.DpPerMeterDisplayOptions
import com.meta.spatial.toolkit.Equirect180ShapeOptions
import com.meta.spatial.toolkit.Equirect360ShapeOptions
import com.meta.spatial.toolkit.Grabbable
import com.meta.spatial.toolkit.GrabbableType
import com.meta.spatial.toolkit.MediaPanelRenderOptions
import com.meta.spatial.toolkit.MediaPanelSettings
import com.meta.spatial.toolkit.MeshCollision
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.PanelCreationSystem
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PanelRenderMode
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.PixelDisplayOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.TransformParent
import com.meta.spatial.toolkit.UIPanelRenderOptions
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.toolkit.VideoSurfacePanelRegistration
import com.meta.spatial.toolkit.Visible
import com.meta.spatial.vr.HandMicrogestureLocomotionSystem
import com.meta.spatial.vr.LocomotionSystem
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
        ViewModelProvider(
            store,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application),
        )[PlayerViewModel::class.java]
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllerHaptics = ControllerHaptics()

    private var videoEntity: Entity? = null
    private var videoPanelId = 0
    private var nextVideoPanelId = VIDEO_PANEL_ID_BASE
    private var videoSurface: Surface? = null
    private var subtitleEntity: Entity? = null
    private var subtitlePanelId = 0
    private var controlsEntity: Entity? = null
    private var focused = false
    private var resumeAfterMenu = false
    private var panelConfig: VideoPanelConfig? = null
    private var placed = false
    private var ticksWaiting = 0
    private var passthrough = false

    // Wanted by the UI state, applied on the scene's thread in onSceneTick.
    @Volatile private var videoFrameRate = 0f
    @Volatile private var liveConversion = false
    private var appliedFrameRate = -1f
    private var appliedLiveConversion: Boolean? = null

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
        scope.launch {
            viewModel.state
                .map { subtitlesShown(it) }
                .distinctUntilChanged()
                .collect { shown -> subtitleEntity?.setComponent(Visible(shown)) }
        }
        scope.launch {
            viewModel.state.collect { state ->
                videoFrameRate = state.frame?.frameRate ?: 0f
                liveConversion =
                    state.live3d == Live3dState.On || state.live3d == Live3dState.Starting
            }
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
        // A video opened from another app arrives as the intent's data, with its read permission.
        val data = intent.data
        val source =
            when {
                id >= 0 -> PlayerSource.Library(id)
                data != null -> PlayerSource.External(data, intent.getStringExtra(EXTRA_TITLE))
                path != null ->
                    PlayerSource.External(
                        if (path.startsWith("content://") || path.startsWith("file://"))
                            path.toUri()
                        else Uri.fromFile(java.io.File(path)),
                        intent.getStringExtra(EXTRA_TITLE),
                    )
                else -> null
            }
        if (source == null) {
            finish()
            return
        }
        // Live 2D to 3D here feeds the stereo layer: both views side by side.
        viewModel.setLiveTarget(headset = true)
        // A projection chosen elsewhere (library menu) applies before the file is even opened.
        ProjectionMode.fromStorageKey(intent.getStringExtra(EXTRA_PROJECTION))?.let {
            viewModel.setProjectionOverride(it)
        }
        viewModel.load(source)
    }

    // The video panel is registered per video layout instead (see createVideoPanel).
    override fun registerPanels(): List<PanelRegistration> =
        listOf(
            ComposeViewPanelRegistration(
                R.id.panel_controls,
                composeViewCreator = { _, context ->
                    ComposeView(context).apply {
                        setContent {
                            SettingsProvider(WindowWidthSizeClass.Expanded) {
                                CompositionLocalProvider(
                                    LocalIsVRMode provides true,
                                    LocalHapticFeedback provides controllerHaptics,
                                    LocalControllerHaptics provides controllerHaptics,
                                ) {
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
            )
        )

    @OptIn(SpatialSDKExperimentalAPI::class)
    override fun onSceneReady() {
        super.onSceneReady()
        scene.setReferenceSpace(ReferenceSpace.LOCAL)
        // Video and the panels are Rec. 709 (sRGB) content; left alone, the headset would treat
        // them as Rec. 2020 and oversaturate them.
        runCatching { scene.setColorSpace(Scene.ColorSpace.REC709) }
        // A cinema seat stays put: the thumbsticks and hand gestures that would walk or turn the
        // viewer away from the screen, or off the center of a 360 sphere, control playback instead.
        systemManager.tryFindSystem<LocomotionSystem>()?.enableLocomotion(false)
        systemManager.tryFindSystem<HandMicrogestureLocomotionSystem>()?.enableHandLocomotion(false)
        applyPassthrough()
    }

    override fun onSceneTick() {
        super.onSceneTick()
        val pressed = controllerHaptics.tick(spatial)
        if (pressed != 0) runOnUiThread { onControllerButtons(pressed) }
        applyDisplaySettings()
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

    /**
     * Playback without pointing at the control bar: A or X plays and pauses, either thumbstick
     * pushed left or right jumps 10 s, B or Y brings the screen back in front of the viewer.
     */
    private fun onControllerButtons(pressed: Int) {
        fun any(vararg bits: Int) = bits.any { pressed and it != 0 }
        when {
            any(ButtonBits.ButtonA, ButtonBits.ButtonX) -> {
                controllerHaptics.play(Haptic.Tap)
                viewModel.togglePlayPause()
            }
            any(ButtonBits.ButtonThumbRR, ButtonBits.ButtonThumbLR) -> {
                controllerHaptics.play(Haptic.Step)
                viewModel.seekBy(PlayerViewModel.SEEK_STEP_MS)
            }
            any(ButtonBits.ButtonThumbRL, ButtonBits.ButtonThumbLL) -> {
                controllerHaptics.play(Haptic.Step)
                viewModel.seekBy(-PlayerViewModel.SEEK_STEP_MS)
            }
            any(ButtonBits.ButtonB, ButtonBits.ButtonY) -> {
                controllerHaptics.play(Haptic.Step)
                placeEntities()
            }
        }
    }

    /**
     * Refresh rate matched to the video's frame rate, and the GPU and CPU held at their sustained
     * high level while live 2D to 3D runs its depth model, at the low one for plain playback (the
     * compositor draws the video layer, the app only its control bar).
     */
    @OptIn(SpatialSDKExperimentalAPI::class)
    private fun applyDisplaySettings() {
        val frameRate = videoFrameRate
        if (frameRate != appliedFrameRate) {
            appliedFrameRate = frameRate
            videoDisplayRates(frameRate).firstOrNull {
                runCatching { scene.requestExactDisplayRate(it) }.getOrDefault(false)
            }
        }
        val live = liveConversion
        if (live != appliedLiveConversion) {
            appliedLiveConversion = live
            runCatching {
                spatial.setPerformanceLevel(
                    if (live) PerformanceLevel.SUSTAINED_HIGH else PerformanceLevel.SUSTAINED_LOW
                )
            }
        }
    }

    /**
     * The Meta Quest system menu takes the focus away without pausing the activity: playback pauses
     * while it is open and goes on when it closes, as in Meta's media samples. The session also
     * passes through VISIBLE on its way to FOCUSED at launch, while the video is still loading;
     * only a focus that was there and went away counts, or the video would never start.
     */
    override fun onSessionStateChanged(state: SessionState) {
        super.onSessionStateChanged(state)
        runOnUiThread {
            when (state) {
                SessionState.VISIBLE ->
                    if (focused) {
                        focused = false
                        // Wanting to play counts too: buffering is not playing yet.
                        resumeAfterMenu = viewModel.player.playWhenReady
                        viewModel.pause()
                    }
                SessionState.FOCUSED -> {
                    focused = true
                    if (resumeAfterMenu) {
                        resumeAfterMenu = false
                        viewModel.play()
                    }
                }
                else -> focused = false
            }
        }
    }

    private fun createEntities() {
        val config = panelConfig ?: return
        videoEntity = createVideoPanel(config)
        subtitleEntity = createSubtitlePanel(config)
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
        val forward =
            head.forward().let { Vector3(it.x, 0f, it.z) }.normalizedOr(Vector3(0f, 0f, 1f))
        placeVideo(config, head, forward)
        placeSubtitles(config, head, forward)
        val controls =
            if (config.mode.isSpherical) {
                head.t + forward * 0.9f + Vector3(0f, -0.45f, 0f)
            } else {
                val (_, screenHeight) = screenSize(config.eyeAspect)
                head.t +
                    forward * (SCREEN_DISTANCE - 1.4f) +
                    Vector3(0f, -(screenHeight / 2f).coerceAtMost(0.6f) - 0.2f, 0f)
            }
        // Turned toward the eyes, so the bar below eye level tilts up to face the viewer.
        val towardPanel = (controls - head.t).normalizedOr(forward)
        controlsEntity?.setComponent(
            Transform(Pose(controls, Quaternion.fromDirection(towardPanel, Vector3(0f, 1f, 0f))))
        )
    }

    private fun placeVideo(
        config: VideoPanelConfig,
        head: Pose = scene.getViewerPose().removePitchAndRoll(),
        forward: Vector3 =
            head.forward().let { Vector3(it.x, 0f, it.z) }.normalizedOr(Vector3(0f, 0f, 1f)),
    ) {
        val pose =
            if (config.mode.isSpherical) Pose(head.t, head.q)
            else Pose(head.t + forward * SCREEN_DISTANCE, head.q)
        videoEntity?.setComponent(Transform(pose))
    }

    /** On a flat screen subtitles ride on the screen; inside a sphere they float in front. */
    private fun placeSubtitles(
        config: VideoPanelConfig,
        head: Pose = scene.getViewerPose().removePitchAndRoll(),
        forward: Vector3 =
            head.forward().let { Vector3(it.x, 0f, it.z) }.normalizedOr(Vector3(0f, 0f, 1f)),
    ) {
        if (!config.mode.isSpherical) return
        val at = head.t + forward * SPHERE_SUBTITLE_DISTANCE + Vector3(0f, -0.25f, 0f)
        subtitleEntity?.setComponent(Transform(Pose(at, head.q)))
    }

    /**
     * A new layout (another video, live 3D on or off, another projection) needs a new video panel.
     * The screen stays where the viewer put it, unless it turns into a sphere or back.
     */
    private fun rebuildVideoPanel(config: VideoPanelConfig) {
        if (config == panelConfig && videoEntity != null) return
        val previous = panelConfig
        val pose = videoEntity?.tryGetComponent<Transform>()?.transform
        destroyVideoPanel()
        panelConfig = config
        videoEntity = createVideoPanel(config)
        if (
            pose != null && previous != null && previous.mode.isSpherical == config.mode.isSpherical
        ) {
            videoEntity?.setComponent(Transform(pose))
        } else {
            placeVideo(config)
        }
        subtitleEntity = createSubtitlePanel(config)
        placeSubtitles(config)
        applyPassthrough(config)
    }

    /**
     * Registers a video panel for [config] under a new id and creates its entity, as Meta's media
     * samples do for each video: the panel's settings are fixed when it is created.
     */
    private fun createVideoPanel(config: VideoPanelConfig): Entity {
        val id = nextVideoPanelId++
        registerPanel(
            VideoSurfacePanelRegistration(
                id,
                surfaceConsumer = { _, surface ->
                    // Until the first frame arrives the layer shows black, not stale memory.
                    SurfacePainter.paintBlack(surface)
                    videoSurface = surface
                    viewModel.attachSurface(surface)
                },
                settingsCreator = { panelSettings(config) },
            )
        )
        videoPanelId = id
        return Entity.create(
            listOfNotNull(
                Panel(id),
                Transform(Pose()),
                // A flat screen can be grabbed and turned to face the viewer; a sphere surrounds
                // the viewer and stays centred.
                if (config.mode.isSpherical) null else Grabbable(true, GrabbableType.PIVOT_Y),
            )
        )
    }

    /**
     * Lets go of the video surface before the panel that owns it goes away (rendering into a
     * destroyed surface fails the decoder or the 2D to 3D effect), then forgets the registration.
     */
    private fun destroyVideoPanel() {
        subtitleEntity?.destroy()
        subtitleEntity = null
        unregisterPanel(subtitlePanelId)
        subtitlePanelId = 0
        videoSurface?.let(viewModel::detachSurface)
        videoSurface = null
        videoEntity?.destroy()
        videoEntity = null
        unregisterPanel(videoPanelId)
        videoPanelId = 0
    }

    /** Forgets a panel registered at run time, as Meta's media samples do. */
    private fun unregisterPanel(id: Int) {
        if (id == 0) return
        panelRegistrations.remove(id)
        systemManager.tryFindSystem<PanelCreationSystem>()?.panelCreator?.remove(id)
    }

    private fun subtitlesShown(state: PlayerUiState) =
        !state.subtitlesOff && state.textTracks.isNotEmpty()

    /**
     * A transparent panel the size of the screen, just in front of it and attached to it, where the
     * subtitles of the selected track are drawn (the video layer itself has none). Inside a 360 or
     * 180 sphere it is a smaller panel in front of the viewer instead. It never takes the pointer.
     */
    private fun createSubtitlePanel(config: VideoPanelConfig): Entity {
        val id = nextVideoPanelId++
        val (width, height) =
            if (config.mode.isSpherical) SPHERE_SUBTITLE_WIDTH to SPHERE_SUBTITLE_WIDTH * 9f / 16f
            else screenSize(config.eyeAspect)
        registerPanel(
            ComposeViewPanelRegistration(
                id,
                composeViewCreator = { _, context ->
                    ComposeView(context).apply {
                        setContent { SubtitleOverlay(player = viewModel.player) }
                    }
                },
                settingsCreator = {
                    UIPanelSettings(
                        shape = QuadShapeOptions(width = width, height = height),
                        display = DpPerMeterDisplayOptions(dpPerMeter = SUBTITLE_DP_PER_METER),
                        // Blended, not masked (the default): smooth text edges and the caption
                        // background's own transparency, over a transparent window.
                        rendering =
                            UIPanelRenderOptions(
                                PanelRenderMode.Layer(
                                    layerBlendType = PanelShapeLayerBlendType.ALPHA_BLEND
                                )
                            ),
                        style = PanelStyleOptions(R.style.Theme_VRClip_PanelTransparent),
                    )
                },
            )
        )
        subtitlePanelId = id
        val components =
            mutableListOf(
                Panel(id, MeshCollision.NoCollision),
                Visible(subtitlesShown(viewModel.state.value)),
            )
        val screen = videoEntity
        if (!config.mode.isSpherical && screen != null) {
            // A centimetre toward the viewer (local -Z), so it never fights the screen for depth.
            components += Transform(Pose(Vector3(0f, 0f, -0.01f)))
            components += TransformParent(screen)
        } else {
            components += Transform(Pose())
        }
        return Entity.create(components)
    }

    /**
     * Size of the flat screen in meters: 3.2 m wide for 16:9 at most, and never taller than a 16:9
     * screen, so portrait and square videos stay on the same wall instead of towering over it.
     */
    private fun screenSize(eyeAspect: Float): Pair<Float, Float> {
        val aspect = eyeAspect.coerceIn(0.3f, 4f)
        val width = minOf(SCREEN_WIDTH, SCREEN_MAX_HEIGHT * aspect)
        return width to width / aspect
    }

    private fun configFor(state: PlayerUiState): VideoPanelConfig? {
        val frame = state.frame ?: return null
        val mode = state.renderProjection
        val (width, height) = state.outputBufferSize ?: (frame.width to frame.height)
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
                ?: ProjectionDetector.detect(
                        state.detectionName.ifEmpty { state.videoPath.orEmpty() },
                        frame,
                    )
                    .mode
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
                    screenSize(config.eyeAspect).let { (width, height) ->
                        QuadShapeOptions(width = width, height = height)
                    }
            }
        val stereo =
            when (config.mode.stereoLayout) {
                StereoLayout.LeftRight -> StereoMode.LeftRight
                StereoLayout.TopBottom -> StereoMode.UpDown
                StereoLayout.None -> StereoMode.None
            }
        return MediaPanelSettings(
            shape = shape,
            display = PixelDisplayOptions(width = config.width, height = config.height),
            rendering =
                MediaPanelRenderOptions(
                    stereoMode = stereo,
                    // Layers are composited in order, not by depth: keep the video behind the
                    // control bar and the controller rays, as Meta's media sample does.
                    zIndex = -1,
                    // A large video downscaled onto the screen shimmers without supersampling.
                    layerFilters =
                        if (config.mode.isSpherical) 0 else LayerFilters.QUALITY_SUPER_SAMPLING,
                ),
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
     * Leaves the immersive player and brings the VRClip library back as a panel in the Horizon home
     * environment (the way Meta's hybrid sample hands over from immersive to 2D).
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

    override fun onPause() {
        // Leaving the app pauses for good, even if the system menu was open before.
        resumeAfterMenu = false
        viewModel.pause()
        viewModel.savePosition()
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        videoSurface?.let(viewModel::detachSurface)
        videoSurface = null
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
        private const val SCREEN_MAX_HEIGHT = 1.8f
        private const val SCREEN_DISTANCE = 3f

        private const val SPHERE_SUBTITLE_WIDTH = 1.6f
        private const val SPHERE_SUBTITLE_DISTANCE = 1.6f
        private const val SUBTITLE_DP_PER_METER = 500f

        /** Ids for the video panels, far from the R.id range. */
        private const val VIDEO_PANEL_ID_BASE = 0x5649_0001
        private const val SPHERE_RADIUS = 50f
        private const val CONTROLS_WIDTH = 1.2f
        private const val CONTROLS_HEIGHT = 0.3f
        private const val CONTROLS_DP_PER_METER = 900f
    }
}
