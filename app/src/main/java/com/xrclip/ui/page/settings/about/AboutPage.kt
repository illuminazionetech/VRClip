package com.xrclip.ui.page.settings.about

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.UpdateDisabled
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.UrlAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xrclip.App
import com.xrclip.App.Companion.packageInfo
import com.xrclip.R
import com.xrclip.ui.common.glassEffect
import com.xrclip.ui.component.BackButton
import com.xrclip.ui.component.ConfirmButton
import com.xrclip.ui.component.PreferenceItem
import com.xrclip.ui.component.PreferenceSwitchWithDivider
import com.xrclip.util.AUTO_UPDATE
import com.xrclip.util.PreferenceUtil
import com.xrclip.util.makeToast

private const val releaseURL = "https://github.com/XRClipTeam/XRClip/releases"
private const val repoUrl = "https://github.com/XRClipTeam/XRClip"
const val weblate = "https://hosted.weblate.org/engage/xrclip/"
const val YtdlpRepository = "https://github.com/yt-dlp/yt-dlp"
private const val githubIssueUrl = "https://github.com/XRClipTeam/XRClip/issues"
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
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .glassEffect(shape = MaterialTheme.shapes.extraLarge, blur = true)
            ) {
                LargeTopAppBar(
                    title = { Text(text = stringResource(id = R.string.about)) },
                    navigationIcon = { BackButton { onNavigateBack() } },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        },
        content = {
            LazyColumn(modifier = Modifier.padding(it)) {
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
                        isSwitchEnabled = !App.isFDroidBuild(),
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
                    PreferenceItem(title = "Package name", description = context.packageName) {
                        clipboardManager.setText(AnnotatedString(context.packageName))
                        makeToast(R.string.info_copied)
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalTextApi::class)
@Composable
@Preview
fun AutoUpdateUnavailableDialog(onDismissRequest: () -> Unit = {}) {
    val uriHandler = LocalUriHandler.current
    val hapticFeedback = LocalHapticFeedback.current
    val hyperLinkText = stringResource(id = R.string.switch_to_github_builds)
    val text = stringResource(id = R.string.auto_update_disabled_msg, "F-Droid", hyperLinkText)

    val annotatedString = buildAnnotatedString {
        append(text)
        val startIndex = text.indexOf(hyperLinkText)
        val endIndex = startIndex + hyperLinkText.length
        addUrlAnnotation(
            UrlAnnotation("https://github.com/XRClipTeam/XRClip/releases/latest"),
            start = startIndex,
            end = endIndex,
        )
        addStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.tertiary,
                textDecoration = TextDecoration.Underline,
            ),
            start = startIndex,
            end = endIndex,
        )
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            ConfirmButton(stringResource(id = R.string.got_it)) { onDismissRequest() }
        },
        icon = { Icon(Icons.Rounded.UpdateDisabled, null) },
        title = {
            Text(
                text = stringResource(id = R.string.feature_unavailable),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            ClickableText(
                text = annotatedString,
                onClick = { index ->
                    annotatedString.getUrlAnnotations(index, index).firstOrNull()?.let {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        uriHandler.openUri(it.item.url)
                    }
                },
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        MaterialTheme.colorScheme.onSurfaceVariant
                    ),
            )
        },
    )
}
