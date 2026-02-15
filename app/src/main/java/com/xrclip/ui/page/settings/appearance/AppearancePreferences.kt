package com.xrclip.ui.page.settings.appearance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.xrclip.R
import com.xrclip.download.Task
import com.xrclip.ui.common.LocalDarkTheme
import com.xrclip.ui.common.Route
import com.xrclip.ui.component.BackButton
import com.xrclip.ui.component.PreferenceItem
import com.xrclip.ui.component.PreferenceSwitchWithDivider
import com.xrclip.ui.page.downloadv2.ActionButton
import com.xrclip.ui.page.downloadv2.CardStateIndicator
import com.xrclip.ui.page.downloadv2.VideoCardV2
import com.xrclip.util.DarkThemePreference.Companion.OFF
import com.xrclip.util.DarkThemePreference.Companion.ON
import com.xrclip.util.PreferenceUtil
import com.xrclip.util.toDisplayName
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
