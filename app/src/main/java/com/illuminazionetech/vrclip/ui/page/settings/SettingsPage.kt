package com.illuminazionetech.vrclip.ui.page.settings

import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.illuminazionetech.vrclip.ui.component.SettingsGroupTitle
import com.illuminazionetech.vrclip.ui.component.groupPosition
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.EnergySavingsLeaf
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.SignalCellular4Bar
import androidx.compose.material.icons.rounded.SignalWifi4Bar
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Vrpano
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.ViewComfy
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.illuminazionetech.vrclip.App
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.ui.common.Route
import com.illuminazionetech.vrclip.ui.common.intState
import com.illuminazionetech.vrclip.ui.component.BackButton
import com.illuminazionetech.vrclip.ui.component.PreferencesHintCard
import com.illuminazionetech.vrclip.ui.component.SettingItem
import com.illuminazionetech.vrclip.util.EXTRACT_AUDIO
import com.illuminazionetech.vrclip.util.PreferenceUtil.getBoolean
import com.illuminazionetech.vrclip.util.PreferenceUtil.updateInt

@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(onNavigateTo: (String) -> Unit) {
    val context = LocalContext.current
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var showBatteryHint by remember {
        mutableStateOf(!pm.isIgnoringBatteryOptimizations(context.packageName))
    }
    val intent = remember {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
        }
    }
    val isActivityAvailable: Boolean = remember {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            context.packageManager
                .queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .isNotEmpty()
        else
            context.packageManager
                .queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_SYSTEM_ONLY.toLong()),
                )
                .isNotEmpty()
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            showBatteryHint = !pm.isIgnoringBatteryOptimizations(context.packageName)
        }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(id = R.string.settings)) },
                scrollBehavior = scrollBehavior,
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
    ) {
        LazyColumn(modifier = Modifier, contentPadding = it) {
            item {
                AnimatedVisibility(
                    visible = showBatteryHint && isActivityAvailable,
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    PreferencesHintCard(
                        title = stringResource(R.string.battery_configuration),
                        icon = Icons.Rounded.EnergySavingsLeaf,
                        description = stringResource(R.string.battery_configuration_desc),
                    ) {
                        launcher.launch(intent)
                        showBatteryHint =
                            !pm.isIgnoringBatteryOptimizations(context.packageName)
                    }
                }
            }
            val downloads =
                listOf(
                    SettingsEntry(
                        R.string.general_settings,
                        R.string.general_settings_desc,
                        Icons.Rounded.SettingsApplications,
                        Route.GENERAL_DOWNLOAD_PREFERENCES,
                    ),
                    SettingsEntry(
                        R.string.download_directory,
                        R.string.download_directory_desc,
                        Icons.Rounded.Folder,
                        Route.DOWNLOAD_DIRECTORY,
                    ),
                    SettingsEntry(
                        R.string.format,
                        R.string.format_settings_desc,
                        if (EXTRACT_AUDIO.getBoolean()) Icons.Rounded.AudioFile
                        else Icons.Rounded.VideoFile,
                        Route.DOWNLOAD_FORMAT,
                    ),
                    SettingsEntry(
                        R.string.network,
                        R.string.network_settings_desc,
                        if (App.connectivityManager.isActiveNetworkMetered)
                            Icons.Rounded.SignalCellular4Bar
                        else Icons.Rounded.SignalWifi4Bar,
                        Route.NETWORK_PREFERENCES,
                    ),
                    SettingsEntry(
                        R.string.custom_command,
                        R.string.custom_command_desc,
                        Icons.Rounded.Terminal,
                        Route.TEMPLATE,
                    ),
                )
            val experience =
                listOf(
                    SettingsEntry(
                        R.string.player_settings_title,
                        R.string.player_settings_subtitle,
                        Icons.Rounded.Vrpano,
                        Route.PLAYER_PREFERENCES,
                    ),
                    SettingsEntry(
                        R.string.look_and_feel,
                        R.string.display_settings,
                        Icons.Rounded.Palette,
                        Route.APPEARANCE,
                    ),
                    SettingsEntry(
                        R.string.interface_and_interaction,
                        R.string.settings_before_download,
                        Icons.Rounded.ViewComfy,
                        Route.INTERACTION,
                    ),
                )
            val support =
                listOf(
                    SettingsEntry(
                        R.string.trouble_shooting,
                        R.string.trouble_shooting_desc,
                        Icons.Rounded.BugReport,
                        Route.TROUBLESHOOTING,
                    ),
                    SettingsEntry(R.string.about, R.string.about_page, Icons.Rounded.Info, Route.ABOUT),
                )
            settingsGroup(R.string.settings_group_downloads, downloads, onNavigateTo)
            settingsGroup(R.string.settings_group_experience, experience, onNavigateTo)
            settingsGroup(R.string.settings_group_support, support, onNavigateTo)
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private class SettingsEntry(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: ImageVector,
    val route: String,
)

private fun LazyListScope.settingsGroup(
    @StringRes title: Int,
    entries: List<SettingsEntry>,
    onNavigateTo: (String) -> Unit,
) {
    item(key = "title_$title") { SettingsGroupTitle(stringResource(title)) }
    entries.forEachIndexed { index, entry ->
        item(key = entry.route) {
            SettingItem(
                title = stringResource(entry.title),
                description = stringResource(entry.description),
                icon = entry.icon,
                position = groupPosition(index, entries.size),
            ) {
                onNavigateTo(entry.route)
            }
        }
    }
}
