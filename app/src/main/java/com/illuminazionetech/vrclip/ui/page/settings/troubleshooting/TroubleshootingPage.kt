package com.illuminazionetech.vrclip.ui.page.settings.troubleshooting

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.ui.common.Route
import com.illuminazionetech.vrclip.ui.component.PreferenceInfo
import com.illuminazionetech.vrclip.ui.component.PreferenceItem
import com.illuminazionetech.vrclip.ui.component.PreferenceSubtitle
import com.illuminazionetech.vrclip.ui.page.settings.BasePreferencePage

@Composable
fun TroubleShootingPage(
    modifier: Modifier = Modifier,
    onNavigateTo: (String) -> Unit,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    BasePreferencePage(
        modifier = modifier,
        title = stringResource(R.string.trouble_shooting),
        onBack = onBack,
    ) {
        LazyColumn(contentPadding = it) {
            item {
                OutlinedCard(modifier = Modifier.padding(16.dp)) {
                    PreferenceInfo(
                        modifier = Modifier,
                        text = stringResource(R.string.issue_tracker_hint),
                    )
                    val issueTrackerUrl = "https://github.com/illuminazionetech/VRClip/issues"
                    PreferenceItem(
                        title = stringResource(R.string.issue_tracker),
                        description = null,
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        onClick = { uriHandler.openUri(issueTrackerUrl) },
                    )

                    val knownIssueUrlYtdlp = "https://github.com/yt-dlp/yt-dlp/issues/3766"
                    PreferenceItem(
                        title = stringResource(R.string.ytdlp_issue_tracker),
                        description = null,
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        onClick = { uriHandler.openUri(knownIssueUrlYtdlp) },
                    )

                    Spacer(Modifier.height(8.dp))
                }
            }
            item { PreferenceSubtitle(text = stringResource(R.string.update)) }
            item {
                PreferenceItem(
                    title = stringResource(id = R.string.ytdlp_update_action),
                    description = stringResource(R.string.general_settings),
                    icon = Icons.Rounded.Update,
                    onClick = { onNavigateTo(Route.GENERAL_DOWNLOAD_PREFERENCES) },
                )
            }

            item { PreferenceSubtitle(text = stringResource(R.string.network)) }
            item {
                PreferenceItem(
                    title = stringResource(R.string.cookies),
                    description = stringResource(R.string.cookies_desc),
                    icon = Icons.Rounded.Cookie,
                    onClick = { onNavigateTo(Route.COOKIE_PROFILE) },
                )
            }
            item { PreferenceSubtitle(text = stringResource(R.string.download_directory)) }
            item {
                PreferenceItem(
                    title = stringResource(id = R.string.restrict_filenames),
                    description = stringResource(id = R.string.restrict_filenames_desc),
                    icon = Icons.Rounded.Spellcheck,
                    onClick = { onNavigateTo(Route.DOWNLOAD_DIRECTORY) },
                )
            }
        }
    }
}
