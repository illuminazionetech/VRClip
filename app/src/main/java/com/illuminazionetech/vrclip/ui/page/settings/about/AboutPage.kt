package com.illuminazionetech.vrclip.ui.page.settings.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.UpdateDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.illuminazionetech.vrclip.App
import com.illuminazionetech.vrclip.App.Companion.packageInfo
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.ui.component.BackButton
import com.illuminazionetech.vrclip.ui.component.PreferenceItem
import com.illuminazionetech.vrclip.ui.component.PreferenceSwitchWithDivider
import com.illuminazionetech.vrclip.util.AUTO_UPDATE
import com.illuminazionetech.vrclip.util.PreferenceUtil
import com.illuminazionetech.vrclip.util.makeToast

private const val releaseURL = "https://github.com/illuminazionetech/VRClip/releases/latest"
private const val repoUrl = "https://github.com/illuminazionetech/VRClip"
const val weblate = "https://hosted.weblate.org/engage/xrclip/"
const val YtdlpRepository = "https://github.com/yt-dlp/yt-dlp"
private const val githubIssueUrl = "https://github.com/illuminazionetech/VRClip/issues"
private const val TAG = "AboutPage"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPage(
    onNavigateBack: () -> Unit,
    onNavigateToCreditsPage: () -> Unit,
    onNavigateToUpdatePage: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isAutoUpdateEnabled by remember { mutableStateOf(PreferenceUtil.isAutoUpdateEnabled()) }

    val info = App.getVersionReport()
    val versionName = packageInfo.versionName

    val uriHandler = LocalUriHandler.current
    fun openUrl(url: String) {
        uriHandler.openUri(url)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(id = R.string.about)) },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
                colors =
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
        content = {
            LazyColumn(modifier = Modifier.padding(it)) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.vrclip_mark),
                            contentDescription = null,
                            modifier = Modifier.size(96.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }
                item {
                    PreferenceItem(
                        title = stringResource(R.string.readme),
                        description = stringResource(R.string.readme_desc),
                        icon = Icons.Rounded.Description,
                    ) {
                        openUrl(repoUrl)
                    }
                }
                item {
                    PreferenceItem(
                        title = stringResource(R.string.release),
                        description = stringResource(R.string.release_desc),
                        icon = Icons.Rounded.NewReleases,
                    ) {
                        openUrl(releaseURL)
                    }
                }
                item {
                    PreferenceItem(
                        title = stringResource(id = R.string.credits),
                        description = stringResource(id = R.string.credits_desc),
                        icon = Icons.Rounded.AutoAwesome,
                    ) {
                        onNavigateToCreditsPage()
                    }
                }
                item {
                    PreferenceSwitchWithDivider(
                        title = stringResource(R.string.auto_update),
                        description = stringResource(R.string.check_for_updates_desc),
                        icon =
                            if (isAutoUpdateEnabled) Icons.Rounded.Update
                            else Icons.Rounded.UpdateDisabled,
                        isChecked = isAutoUpdateEnabled,
                        onClick = onNavigateToUpdatePage,
                        onChecked = {
                            isAutoUpdateEnabled = !isAutoUpdateEnabled
                            PreferenceUtil.updateValue(AUTO_UPDATE, isAutoUpdateEnabled)
                        },
                    )
                }
                item {
                    PreferenceItem(
                        title = stringResource(R.string.version),
                        description = versionName,
                        icon = Icons.Rounded.Info,
                    ) {
                        clipboardManager.setText(AnnotatedString(info))
                        makeToast(R.string.info_copied)
                    }
                }
                item {
                    PreferenceItem(
                        title = stringResource(R.string.package_name),
                        description = context.packageName,
                    ) {
                        clipboardManager.setText(AnnotatedString(context.packageName))
                        makeToast(R.string.info_copied)
                    }
                }
            }
        },
    )
}
