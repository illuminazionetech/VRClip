package com.illuminazionetech.vrclip.ui.page.settings.network

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.SignalCellular4Bar
import androidx.compose.material.icons.rounded.SignalCellularConnectedNoInternet4Bar
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.ui.common.booleanState
import com.illuminazionetech.vrclip.ui.component.BackButton
import com.illuminazionetech.vrclip.ui.component.PreferenceInfo
import com.illuminazionetech.vrclip.ui.component.PreferenceItem
import com.illuminazionetech.vrclip.ui.component.PreferenceSubtitle
import com.illuminazionetech.vrclip.ui.component.PreferenceSwitch
import com.illuminazionetech.vrclip.ui.component.PreferenceSwitchWithDivider
import com.illuminazionetech.vrclip.util.ARIA2C
import com.illuminazionetech.vrclip.util.CELLULAR_DOWNLOAD
import com.illuminazionetech.vrclip.util.COOKIES
import com.illuminazionetech.vrclip.util.CUSTOM_COMMAND
import com.illuminazionetech.vrclip.util.FORCE_IPV4
import com.illuminazionetech.vrclip.util.PROXY
import com.illuminazionetech.vrclip.util.PreferenceUtil.getBoolean
import com.illuminazionetech.vrclip.util.PreferenceUtil.updateBoolean
import com.illuminazionetech.vrclip.util.PreferenceUtil.updateValue
import com.illuminazionetech.vrclip.util.RATE_LIMIT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkPreferences(navigateToCookieProfilePage: () -> Unit = {}, onNavigateBack: () -> Unit) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )

    var showConcurrentDownloadDialog by remember { mutableStateOf(false) }
    var showRateLimitDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }
    var aria2c by remember { mutableStateOf(ARIA2C.getBoolean()) }
    var proxy by PROXY.booleanState
    var isCookiesEnabled by COOKIES.booleanState
    var forceIpv4 by FORCE_IPV4.booleanState

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(modifier = Modifier, text = stringResource(id = R.string.network)) },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
        content = {
            val isCustomCommandEnabled by CUSTOM_COMMAND.booleanState

            LazyColumn(contentPadding = it) {
                if (isCustomCommandEnabled)
                    item {
                        PreferenceInfo(
                            text = stringResource(id = R.string.custom_command_enabled_hint)
                        )
                    }
                item { PreferenceSubtitle(text = stringResource(R.string.general_settings)) }
                item {
                    var isRateLimitEnabled by remember { mutableStateOf(RATE_LIMIT.getBoolean()) }

                    PreferenceSwitchWithDivider(
                        title = stringResource(R.string.rate_limit),
                        description = stringResource(R.string.rate_limit_desc),
                        icon = Icons.Rounded.Speed,
                        enabled = !isCustomCommandEnabled,
                        isChecked = isRateLimitEnabled,
                        onChecked = {
                            isRateLimitEnabled = !isRateLimitEnabled
                            updateValue(RATE_LIMIT, isRateLimitEnabled)
                        },
                        onClick = { showRateLimitDialog = true },
                    )
                }
                item {
                    var isDownloadWithCellularEnabled by remember {
                        mutableStateOf(CELLULAR_DOWNLOAD.getBoolean())
                    }
                    PreferenceSwitch(
                        title = stringResource(R.string.download_with_cellular),
                        description = stringResource(R.string.download_with_cellular_desc),
                        icon =
                            if (isDownloadWithCellularEnabled) Icons.Rounded.SignalCellular4Bar
                            else Icons.Rounded.SignalCellularConnectedNoInternet4Bar,
                        isChecked = isDownloadWithCellularEnabled,
                        onClick = {
                            isDownloadWithCellularEnabled = !isDownloadWithCellularEnabled
                            updateValue(CELLULAR_DOWNLOAD, isDownloadWithCellularEnabled)
                        },
                    )
                }

                item { PreferenceSubtitle(text = stringResource(id = R.string.advanced_settings)) }

                item {
                    PreferenceSwitch(
                        title = stringResource(R.string.aria2),
                        icon = Icons.Rounded.Bolt,
                        description = stringResource(R.string.aria2_desc),
                        isChecked = aria2c,
                        onClick = {
                            aria2c = !aria2c
                            updateValue(ARIA2C, aria2c)
                        },
                    )
                }
                item {
                    PreferenceSwitchWithDivider(
                        title = stringResource(id = R.string.proxy),
                        description = stringResource(id = R.string.proxy_desc),
                        icon = Icons.Rounded.VpnKey,
                        isChecked = proxy,
                        onChecked = {
                            proxy = !proxy
                            PROXY.updateBoolean(proxy)
                        },
                        onClick = { showProxyDialog = true },
                        enabled = !isCustomCommandEnabled,
                    )
                }
                item {
                    PreferenceItem(
                        title = stringResource(id = R.string.concurrent_download),
                        description = stringResource(R.string.concurrent_download_desc),
                        icon = Icons.Rounded.OfflineBolt,
                        enabled = !aria2c && !isCustomCommandEnabled,
                    ) {
                        showConcurrentDownloadDialog = true
                    }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(R.string.force_ipv4),
                        description = stringResource(id = R.string.force_ipv4_desc),
                        icon = Icons.Rounded.SettingsEthernet,
                        enabled = !isCustomCommandEnabled,
                        isChecked = forceIpv4,
                    ) {
                        forceIpv4 = !forceIpv4
                        FORCE_IPV4.updateBoolean(forceIpv4)
                    }
                }
                item {
                    PreferenceItem(
                        title = stringResource(R.string.cookies),
                        description = stringResource(R.string.cookies_desc),
                        icon = Icons.Rounded.Cookie,
                        onClick = { navigateToCookieProfilePage() },
                    )
                }
            }
        },
    )

    if (showConcurrentDownloadDialog) {
        ConcurrentDownloadDialog { showConcurrentDownloadDialog = false }
    }

    if (showRateLimitDialog) {
        RateLimitDialog { showRateLimitDialog = false }
    }
    if (showProxyDialog) {
        ProxyConfigurationDialog { showProxyDialog = false }
    }
}
