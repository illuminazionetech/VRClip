package com.illuminazionetech.vrclip.ui.page

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.download.DownloaderV2
import com.illuminazionetech.vrclip.download.Task
import com.illuminazionetech.vrclip.ui.common.HapticFeedback.slightHapticFeedback
import com.illuminazionetech.vrclip.ui.common.Route
import com.illuminazionetech.vrclip.ui.common.motion.ExpressiveMotion
import org.koin.compose.koinInject

/** The destinations reachable from the navigation bar or rail. */
enum class TopLevelDestination(
    val route: String,
    val rootRoute: String,
    @StringRes val labelId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Queue(
        Route.HOME,
        Route.HOME,
        R.string.nav_queue,
        Icons.Rounded.Download,
        Icons.Outlined.Download,
    ),
    Library(
        Route.DOWNLOADS,
        Route.DOWNLOADS,
        R.string.nav_library,
        Icons.Rounded.VideoLibrary,
        Icons.Outlined.VideoLibrary,
    ),
    Commands(
        Route.TASK_LIST,
        Route.TASK_LIST,
        R.string.nav_commands,
        Icons.Rounded.Terminal,
        Icons.Outlined.Terminal,
    ),
    Settings(
        Route.SETTINGS,
        Route.SETTINGS_PAGE,
        R.string.settings,
        Icons.Rounded.Settings,
        Icons.Outlined.Settings,
    );

    companion object {
        val rootRoutes: Set<String> = entries.map { it.rootRoute }.toSet()
    }
}

enum class AppNavigationLayout(val suiteType: NavigationSuiteType) {
    BottomBar(NavigationSuiteType.ShortNavigationBarCompact),
    Rail(NavigationSuiteType.WideNavigationRailCollapsed),
    WideRailExpanded(NavigationSuiteType.WideNavigationRailExpanded),
}

/**
 * The app shell: a Material 3 navigation suite that shows a bottom bar on phones and a rail on
 * wider windows and on the Quest panel. The navigation hides (animated) on detail screens and comes
 * back on the root screen of every top-level destination. On rails the primary "new download"
 * action sits in the rail header; on phones the queue page shows it as a FAB.
 */
@Composable
fun AppNavigationScaffold(
    currentDestination: NavDestination?,
    navigationSuiteType: AppNavigationLayout,
    onNavigate: (TopLevelDestination) -> Unit,
    onNewDownload: () -> Unit,
    downloader: DownloaderV2 = koinInject(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val state = rememberNavigationSuiteScaffoldState()
    val showNavigation = currentDestination == null || currentDestination.isTopLevelRoot()

    LaunchedEffect(showNavigation) { if (showNavigation) state.show() else state.hide() }

    val taskMap = downloader.getTaskStateMap()
    val activeDownloads by remember {
        derivedStateOf {
            taskMap.values.count {
                it.downloadState is Task.DownloadState.Running ||
                    it.downloadState is Task.DownloadState.FetchingInfo ||
                    it.downloadState == Task.DownloadState.ReadyWithInfo
            }
        }
    }

    val isRail = navigationSuiteType != AppNavigationLayout.BottomBar

    NavigationSuiteScaffold(
        navigationSuiteType = navigationSuiteType.suiteType,
        state = state,
        containerColor = MaterialTheme.colorScheme.surface,
        primaryActionContent = {
            if (isRail && state.targetValue == NavigationSuiteScaffoldValue.Visible) {
                RailDownloadAction(
                    expanded = navigationSuiteType == AppNavigationLayout.WideRailExpanded,
                    onClick = {
                        view.slightHapticFeedback()
                        onNewDownload()
                    },
                )
            }
        },
        navigationItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentDestination.isInHierarchyOf(destination)
                NavigationSuiteItem(
                    navigationSuiteType = navigationSuiteType.suiteType,
                    selected = selected,
                    onClick = {
                        view.slightHapticFeedback()
                        if (!selected || currentDestination?.route != destination.rootRoute) {
                            onNavigate(destination)
                        }
                    },
                    icon = {
                        val count =
                            if (destination == TopLevelDestination.Queue) activeDownloads else 0
                        BadgedBox(
                            badge = {
                                if (count > 0) Badge { Text(count.coerceAtMost(99).toString()) }
                            }
                        ) {
                            AnimatedNavIcon(destination = destination, selected = selected)
                        }
                    },
                    label = { Text(stringResource(destination.labelId)) },
                )
            }
        },
        content = content,
    )
}

@Composable
private fun RailDownloadAction(expanded: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.new_download)
    if (expanded) {
        ExtendedFloatingActionButton(
            onClick = onClick,
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text(label) },
            modifier = Modifier.padding(vertical = 8.dp),
        )
    } else {
        // The rail places its header at the start edge; center the FAB over the 96dp
        // collapsed rail so it lines up with the destination icons below.
        Box(Modifier.width(96.dp), contentAlignment = Alignment.Center) {
            FloatingActionButton(onClick = onClick, modifier = Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = label)
            }
        }
    }
}

/** Crossfades and springs between the outlined and filled icon of a destination. */
@Composable
private fun AnimatedNavIcon(destination: TopLevelDestination, selected: Boolean) {
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            (fadeIn(ExpressiveMotion.effects()) +
                    scaleIn(ExpressiveMotion.spatial(), initialScale = 0.7f))
                .togetherWith(fadeOut(ExpressiveMotion.effects()))
        },
        label = "navIcon",
    ) { isSelected ->
        Icon(
            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = null,
        )
    }
}
