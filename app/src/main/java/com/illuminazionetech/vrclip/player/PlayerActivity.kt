package com.illuminazionetech.vrclip.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.player.quest.ImmersivePlayerActivity
import com.illuminazionetech.vrclip.ui.common.SettingsProvider
import com.illuminazionetech.vrclip.ui.theme.VRClipTheme
import com.illuminazionetech.vrclip.util.PLAYER_QUEST_IMMERSIVE
import com.illuminazionetech.vrclip.util.PreferenceUtil.getBoolean
import com.illuminazionetech.vrclip.util.isQuestDevice
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Full-screen video player for phones and tablets, in its own task so it can shrink into
 * picture-in-picture while the rest of VRClip stays usable. Opens library videos ([EXTRA_VIDEO_ID])
 * and video files shared by other apps (ACTION_VIEW with a content:// or file:// URI).
 */
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    private var isInPip by mutableStateOf(false)
    private var pipReceiverRegistered = false

    private val pipActionReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getIntExtra(EXTRA_PIP_ACTION, -1)) {
                    PIP_PLAY_PAUSE -> viewModel.togglePlayPause()
                    PIP_REWIND -> viewModel.seekBy(-PlayerViewModel.SEEK_STEP_MS)
                    PIP_FORWARD -> viewModel.seekBy(PlayerViewModel.SEEK_STEP_MS)
                }
            }
        }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (redirectToImmersivePlayer(intent)) return
        hideSystemBars()

        val source = sourceFrom(intent)
        if (source == null) {
            finish()
            return
        }
        viewModel.load(source)

        setContent {
            SettingsProvider(calculateWindowSizeClass(this).widthSizeClass) {
                VRClipTheme(darkTheme = true) {
                    PlayerScreen(
                        viewModel = viewModel,
                        isInPictureInPicture = isInPip,
                        canEnterPictureInPicture = supportsPip(),
                        onEnterPictureInPicture = ::enterPip,
                        onNavigateBack = ::finishAfterTransitionWithAnimation,
                        onRequestOrientation = { requestedOrientation = it },
                    )
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state
                    .map {
                        PipInputs(
                            it.isPlaying,
                            it.renderProjection,
                            it.frame?.aspect,
                            it.error == null,
                        )
                    }
                    .distinctUntilChanged()
                    .collect { updatePipParams() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (redirectToImmersivePlayer(intent)) return
        sourceFrom(intent)?.let(viewModel::load)
    }

    override fun onStart() {
        super.onStart()
        registerPipReceiver()
    }

    override fun onStop() {
        super.onStop()
        unregisterPipReceiver()
        // Leaving the app pauses playback, unless the video keeps playing in its own window.
        if (!isInPictureInPictureMode) viewModel.pause()
        viewModel.savePosition()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters picture-in-picture by itself (setAutoEnterEnabled).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && canPipNow()) enterPip()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            // Going through PiP puts the player in the background task shape; make sure the
            // system bars come back hidden when the window is expanded again.
            hideSystemBars()
        } else if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            // The PiP window was dismissed.
            viewModel.pause()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).run {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun finishAfterTransitionWithAnimation() {
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.hold, R.anim.player_exit)
        } else {
            @Suppress("DEPRECATION") overridePendingTransition(R.anim.hold, R.anim.player_exit)
        }
    }

    // region Picture-in-picture

    private data class PipInputs(
        val playing: Boolean,
        val projection: ProjectionMode,
        val aspect: Float?,
        val healthy: Boolean,
    )

    private fun supportsPip(): Boolean =
        !isQuestDevice() &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** 360 and stereo video make no sense in a thumbnail-sized window; plain video does. */
    private fun canPipNow(): Boolean {
        val state = viewModel.state.value
        return supportsPip() &&
            state.isPlaying &&
            state.error == null &&
            !state.renderProjection.requiresImmersiveRendering
    }

    private fun enterPip() {
        if (!supportsPip()) return
        runCatching { enterPictureInPictureMode(buildPipParams()) }
    }

    private fun updatePipParams() {
        if (!supportsPip()) return
        runCatching { setPictureInPictureParams(buildPipParams()) }
    }

    private fun buildPipParams(): PictureInPictureParams {
        val state = viewModel.state.value
        val builder = PictureInPictureParams.Builder()
        val aspect = state.frame?.aspect ?: (16f / 9f)
        // The platform refuses ratios outside 1:2.39 .. 2.39:1.
        val clamped = aspect.coerceIn(1f / 2.39f, 2.39f)
        builder.setAspectRatio(Rational((clamped * 10_000).toInt(), 10_000))
        videoBounds()?.let(builder::setSourceRectHint)
        builder.setActions(pipActions(state.isPlaying))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(canPipNow())
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun videoBounds(): Rect? {
        val view = window.decorView
        if (view.width == 0 || view.height == 0) return null
        val aspect = viewModel.state.value.frame?.aspect ?: return null
        val viewAspect = view.width.toFloat() / view.height
        return if (aspect > viewAspect) {
            val height = (view.width / aspect).toInt()
            val top = (view.height - height) / 2
            Rect(0, top, view.width, top + height)
        } else {
            val width = (view.height * aspect).toInt()
            val left = (view.width - width) / 2
            Rect(left, 0, left + width, view.height)
        }
    }

    private fun pipActions(playing: Boolean): List<RemoteAction> =
        listOf(
            pipAction(PIP_REWIND, R.drawable.ic_pip_rewind, R.string.player_rewind),
            if (playing) pipAction(PIP_PLAY_PAUSE, R.drawable.ic_pip_pause, R.string.player_pause)
            else pipAction(PIP_PLAY_PAUSE, R.drawable.ic_pip_play, R.string.player_play),
            pipAction(PIP_FORWARD, R.drawable.ic_pip_fast_forward, R.string.player_forward),
        )

    private fun pipAction(action: Int, icon: Int, title: Int): RemoteAction {
        val intent =
            Intent(ACTION_PIP_CONTROL).setPackage(packageName).putExtra(EXTRA_PIP_ACTION, action)
        val pending =
            PendingIntent.getBroadcast(
                this,
                action,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val label = getString(title)
        return RemoteAction(Icon.createWithResource(this, icon), label, label, pending)
    }

    private fun registerPipReceiver() {
        if (pipReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            pipActionReceiver,
            IntentFilter(ACTION_PIP_CONTROL),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        pipReceiverRegistered = true
    }

    private fun unregisterPipReceiver() {
        if (!pipReceiverRegistered) return
        runCatching { unregisterReceiver(pipActionReceiver) }
        pipReceiverRegistered = false
    }

    // endregion

    // region Intents

    private fun sourceFrom(intent: Intent?): PlayerSource? {
        intent ?: return null
        val id = intent.getIntExtra(EXTRA_VIDEO_ID, -1)
        if (id >= 0) return PlayerSource.Library(id)
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return null
            return PlayerSource.External(uri, displayName(uri))
        }
        return null
    }

    private fun displayName(uri: Uri): String? =
        if (uri.scheme == "content") {
            runCatching {
                contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }
                .getOrNull()
        } else uri.lastPathSegment

    /** On Quest, videos from other apps also open in the immersive player unless turned off. */
    private fun redirectToImmersivePlayer(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_VIEW || !isQuestDevice()) return false
        if (!PLAYER_QUEST_IMMERSIVE.getBoolean(true)) return false
        val uri = intent.data ?: return false
        // The read permission another app (a file manager, LocalSend) granted for this URI lasts
        // only as long as this activity, which closes now; it carries over to the immersive player
        // only when the URI travels as the intent's data with the grant flag, not as an extra.
        startActivity(
            Intent(this, ImmersivePlayerActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .setDataAndType(uri, intent.type ?: contentResolver.getType(uri))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(ImmersivePlayerActivity.EXTRA_TITLE, displayName(uri))
        )
        finish()
        return true
    }

    // endregion

    companion object {
        const val EXTRA_VIDEO_ID = "com.illuminazionetech.vrclip.player.EXTRA_VIDEO_ID"
        private const val ACTION_PIP_CONTROL = "com.illuminazionetech.vrclip.player.PIP_CONTROL"
        private const val EXTRA_PIP_ACTION = "action"
        private const val PIP_PLAY_PAUSE = 1
        private const val PIP_REWIND = 2
        private const val PIP_FORWARD = 3

        const val ORIENTATION_UNSPECIFIED = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        const val ORIENTATION_LANDSCAPE = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        const val ORIENTATION_PORTRAIT = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT

        fun intent(context: Context, videoId: Int): Intent =
            Intent(context, PlayerActivity::class.java).putExtra(EXTRA_VIDEO_ID, videoId)
    }
}
