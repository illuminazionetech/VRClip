package com.illuminazionetech.vrclip.ui.page.settings.appearance

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.download.Task
import com.illuminazionetech.vrclip.ui.common.LocalDarkTheme
import com.illuminazionetech.vrclip.ui.common.Route
import com.illuminazionetech.vrclip.ui.component.BackButton
import com.illuminazionetech.vrclip.ui.component.PreferenceItem
import com.illuminazionetech.vrclip.ui.component.PreferenceSwitch
import com.illuminazionetech.vrclip.ui.component.PreferenceSwitchWithDivider
import com.illuminazionetech.vrclip.ui.page.downloadv2.ActionButton
import com.illuminazionetech.vrclip.ui.page.downloadv2.CardStateIndicator
import com.illuminazionetech.vrclip.ui.page.downloadv2.VideoCardV2
import com.illuminazionetech.vrclip.util.DarkThemePreference.Companion.OFF
import com.illuminazionetech.vrclip.util.DarkThemePreference.Companion.ON
import com.illuminazionetech.vrclip.util.PreferenceUtil
import com.illuminazionetech.vrclip.util.toDisplayName
import java.util.Locale
import kotlinx.coroutines.Job

private val DrawableList =
    listOf(R.drawable.sample, R.drawable.sample1, R.drawable.sample2, R.drawable.sample3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearancePreferences(onNavigateBack: () -> Unit, onNavigateTo: (String) -> Unit) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )

    val index = remember { DrawableList.indices.random() }

    val image = remember(index) { DrawableList[index] }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(modifier = Modifier, text = stringResource(id = R.string.look_and_feel))
                },
                navigationIcon = { BackButton(onNavigateBack) },
                scrollBehavior = scrollBehavior,
            )
        },
        content = {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(it)) {
                val downloadState = Task.DownloadState.Running(Job(), "", 0.8f)
                VideoCardV2(
                    modifier = Modifier.padding(18.dp).clearAndSetSemantics {},
                    title = stringResource(R.string.video_title_sample_text),
                    uploader = stringResource(R.string.video_creator_sample_text),
                    thumbnailModel = image,
                    stateIndicator = {
                        CardStateIndicator(modifier = Modifier, downloadState = downloadState)
                    },
                    actionButton = {
                        ActionButton(modifier = Modifier, downloadState = downloadState) {}
                    },
                ) {}
                val isDarkTheme = LocalDarkTheme.current.isDarkTheme()
                PreferenceSwitchWithDivider(
                    title = stringResource(id = R.string.dark_theme),
                    icon = if (isDarkTheme) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                    isChecked = isDarkTheme,
                    description = LocalDarkTheme.current.getDarkThemeDesc(),
                    onChecked = {
                        PreferenceUtil.modifyDarkThemePreference(if (isDarkTheme) OFF else ON)
                    },
                    onClick = { onNavigateTo(Route.DARK_THEME) },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val dynamicColor by
                        PreferenceUtil.AppSettingsStateFlow.collectAsState()
                    PreferenceSwitch(
                        title = stringResource(R.string.dynamic_color),
                        description = stringResource(R.string.dynamic_color_desc),
                        icon = Icons.Rounded.Palette,
                        isChecked = dynamicColor.dynamicColor,
                    ) {
                        PreferenceUtil.modifyDynamicColorPreference(!dynamicColor.dynamicColor)
                    }
                }
                PreferenceItem(
                    title = stringResource(R.string.language),
                    icon = Icons.Rounded.Language,
                    description = Locale.getDefault().toDisplayName(),
                ) {
                    onNavigateTo(Route.LANGUAGES)
                }
            }
        },
    )
}
