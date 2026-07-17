package com.illuminazionetech.vrclip

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.illuminazionetech.vrclip.App.Companion.context
import com.illuminazionetech.vrclip.ui.common.LocalDarkTheme
import com.illuminazionetech.vrclip.ui.common.LocalIsVRMode
import com.illuminazionetech.vrclip.ui.common.SettingsProvider
import com.illuminazionetech.vrclip.ui.page.AppEntry
import com.illuminazionetech.vrclip.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.illuminazionetech.vrclip.ui.theme.VRClipTheme
import com.illuminazionetech.vrclip.util.PreferenceUtil
import com.illuminazionetech.vrclip.util.isQuestDevice
import com.illuminazionetech.vrclip.util.matchUrlFromSharedText
import com.illuminazionetech.vrclip.util.setLanguage
import kotlinx.coroutines.runBlocking
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.KoinContext

class MainActivity : AppCompatActivity() {
    private val dialogViewModel: DownloadDialogViewModel by viewModel()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isQuestDevice() && !PreferenceUtil.containsKey(com.illuminazionetech.vrclip.util.VR_MODE)) {
            PreferenceUtil.updateValue(com.illuminazionetech.vrclip.util.VR_MODE, true)
        }

        if (Build.VERSION.SDK_INT < 33) {
            runBlocking { setLanguage(PreferenceUtil.getLocaleFromPreference()) }
        }
        enableEdgeToEdge()

        setContent {
            KoinContext {
                val windowSizeClass = calculateWindowSizeClass(this)
                val isVR = isQuestDevice()
                SettingsProvider(windowWidthSizeClass = windowSizeClass.widthSizeClass) {
                    CompositionLocalProvider(LocalIsVRMode provides isVR) {
                        VRClipTheme(
                            darkTheme = LocalDarkTheme.current.isDarkTheme() || isVR,
                            isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                        ) {
                            AppEntry(dialogViewModel = dialogViewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val url = intent.getSharedURL()
        if (url != null) {
            dialogViewModel.postAction(DownloadDialogViewModel.Action.ShowSheet(listOf(url)))
        }
    }

    private fun Intent.getSharedURL(): String? {
        val intent = this

        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.dataString
            }

            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedContent ->
                    intent.removeExtra(Intent.EXTRA_TEXT)
                    matchUrlFromSharedText(sharedContent).also { matchedUrl ->
                        if (sharedUrlCached != matchedUrl) {
                            sharedUrlCached = matchedUrl
                        }
                    }
                }
            }

            else -> {
                null
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private var sharedUrlCached = ""
    }
}
