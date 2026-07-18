package com.illuminazionetech.vrclip.ui.page

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.meta.spatial.uiset.navigation.SpatialSideNavItem
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.ui.common.LocalIsVRMode
import com.illuminazionetech.vrclip.ui.common.LocalWindowWidthState
import com.illuminazionetech.vrclip.ui.common.Route
import com.illuminazionetech.vrclip.ui.common.motion.ExpressiveMotion
import com.illuminazionetech.vrclip.ui.common.tonalSurface
import com.illuminazionetech.vrclip.ui.page.downloadv2.DownloadPageImplV2
import kotlinx.coroutines.launch

/**
 * A navigation destination with a proper selected (filled, rounded) and unselected (outlined)
 * icon pair, so the selection state reads at a glance everywhere the destination appears: the
 * drawer, the rail, and the VR side navigation.
 */
private class NavDestination(
    val route: String,
    val labelId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val navigateTo: String = route,
)

private val TopLevelDestinations: List<NavDestination>
    get() =
        listOf(
            NavDestination(
                route = Route.HOME,
                labelId = R.string.download_queue,
                selectedIcon = Icons.Rounded.Download,
                unselectedIcon = Icons.Outlined.Download,
            ),
            NavDestination(
                route = Route.DOWNLOADS,
                labelId = R.string.downloads_history,
                selectedIcon = Icons.AutoMirrored.Rounded.List,
                unselectedIcon = Icons.AutoMirrored.Outlined.List,
            ),
            NavDestination(
                route = Route.TASK_LIST,
                labelId = R.string.custom_command,
                selectedIcon = Icons.Rounded.Terminal,
                unselectedIcon = Icons.Outlined.Terminal,
            ),
            NavDestination(
                route = Route.SETTINGS_PAGE,
                labelId = R.string.settings,
                selectedIcon = Icons.Rounded.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                navigateTo = Route.SETTINGS,
            ),
        )

private val QuickSettingsDestinations: List<NavDestination>
    get() =
        listOf(
            NavDestination(
                route = Route.GENERAL_DOWNLOAD_PREFERENCES,
                labelId = R.string.general_settings,
                selectedIcon = Icons.Rounded.Tune,
                unselectedIcon = Icons.Outlined.Tune,
            ),
            NavDestination(
                route = Route.DOWNLOAD_DIRECTORY,
                labelId = R.string.download_directory,
                selectedIcon = Icons.Rounded.Folder,
                unselectedIcon = Icons.Outlined.Folder,
            ),
            NavDestination(
                route = Route.COOKIE_PROFILE,
                labelId = R.string.cookies,
                selectedIcon = Icons.Rounded.Cookie,
                unselectedIcon = Icons.Outlined.Cookie,
            ),
            NavDestination(
                route = Route.TROUBLESHOOTING,
                labelId = R.string.trouble_shooting,
                selectedIcon = Icons.Rounded.BugReport,
                unselectedIcon = Icons.Outlined.BugReport,
            ),
            NavDestination(
                route = Route.ABOUT,
                labelId = R.string.about,
                selectedIcon = Icons.Rounded.Info,
                unselectedIcon = Icons.Outlined.Info,
            ),
        )

/** Crossfades and springs between the selected and unselected icon of a destination. */
@Composable
private fun AnimatedNavIcon(
    destination: NavDestination,
    selected: Boolean,
    contentDescription: String? = null,
) {
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
            contentDescription = contentDescription,
        )
    }
}

@Composable
fun NavigationDrawer(
    modifier: Modifier = Modifier,
    drawerState: DrawerState,
    windowWidth: WindowWidthSizeClass = LocalWindowWidthState.current,
    currentRoute: String? = null,
    currentTopDestination: String? = null,
    showQuickSettings: Boolean = true,
    onNavigateToRoute: (String) -> Unit,
    onDismissRequest: suspend () -> Unit,
    gesturesEnabled: Boolean = true,
    footer: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()

    when (windowWidth) {
        WindowWidthSizeClass.Compact,
        WindowWidthSizeClass.Medium -> {
            ModalNavigationDrawer(
                gesturesEnabled = gesturesEnabled,
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerState = drawerState,
                        modifier = Modifier.fillMaxHeight().width(320.dp).padding(16.dp),
                        drawerShape = MaterialTheme.shapes.extraLarge,
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        drawerTonalElevation = 0.dp,
                    ) {
                        NavigationDrawerSheetContent(
                            modifier = Modifier,
                            currentRoute = currentRoute,
                            showQuickSettings = showQuickSettings,
                            onNavigateToRoute = onNavigateToRoute,
                            onDismissRequest = onDismissRequest,
                            footer = footer,
                        )
                    }
                },
                content = content,
            )
        }
        WindowWidthSizeClass.Expanded -> {
            ModalNavigationDrawer(
                gesturesEnabled = drawerState.isOpen,
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerState = drawerState,
                        modifier = modifier.width(360.dp).padding(16.dp),
                        drawerShape = MaterialTheme.shapes.extraLarge,
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        drawerTonalElevation = 0.dp,
                    ) {
                        NavigationDrawerSheetContent(
                            modifier = Modifier,
                            currentRoute = currentRoute,
                            showQuickSettings = showQuickSettings,
                            onNavigateToRoute = onNavigateToRoute,
                            onDismissRequest = onDismissRequest,
                            footer = footer,
                        )
                    }
                },
            ) {
                Row {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.zIndex(1f),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxHeight().systemBarsPadding().width(92.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.height(8.dp))
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Menu,
                                    contentDescription =
                                        stringResource(R.string.show_navigation_drawer),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            NavigationRailContent(
                                modifier = Modifier,
                                currentTopDestination = currentTopDestination,
                                onNavigateToRoute = onNavigateToRoute,
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    content()
                }
            }
        }
    }
}

@Composable
fun NavigationDrawerSheetContent(
    modifier: Modifier = Modifier,
    currentRoute: String? = null,
    showQuickSettings: Boolean = true,
    onNavigateToRoute: (String) -> Unit,
    onDismissRequest: suspend () -> Unit,
    footer: @Composable (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()

    @Composable
    fun DrawerItem(destination: NavDestination) {
        val selected = currentRoute == destination.route
        NavigationDrawerItem(
            label = { Text(stringResource(destination.labelId)) },
            icon = { AnimatedNavIcon(destination = destination, selected = selected) },
            onClick = {
                scope
                    .launch { onDismissRequest() }
                    .invokeOnCompletion { onNavigateToRoute(destination.navigateTo) }
            },
            selected = selected,
            colors =
                NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = Color.Transparent
                ),
        )
    }

    Column(
        modifier =
            modifier
                .padding(horizontal = 12.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
    ) {
        Spacer(Modifier.height(72.dp))
        ProvideTextStyle(MaterialTheme.typography.titleSmall) {
            TopLevelDestinations.forEach { destination -> DrawerItem(destination) }

            if (showQuickSettings) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Column(
                    modifier =
                        Modifier.padding(start = 16.dp).padding(top = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.settings),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier,
                    )
                }

                QuickSettingsDestinations.forEach { destination -> DrawerItem(destination) }
            }
        }
        Spacer(Modifier.weight(1f))
        footer?.invoke()
    }
}

@Composable
fun NavigationRailItemVariant(
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit),
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor by
        animateColorAsState(
            targetValue =
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            animationSpec = ExpressiveMotion.effects(),
            label = "railItemContainer",
        )
    val contentColor by
        animateColorAsState(
            targetValue =
                if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = ExpressiveMotion.effects(),
            label = "railItemContent",
        )
    Box(
        modifier =
            modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.large)
                .background(containerColor)
                .selectable(selected = selected, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { icon() }
    }
}

@Composable
fun NavigationRailContent(
    modifier: Modifier = Modifier,
    currentTopDestination: String? = null,
    onNavigateToRoute: (String) -> Unit,
) {
    if (LocalIsVRMode.current) {
        Column(
            modifier = modifier.padding(12.dp).selectableGroup().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TopLevelDestinations.forEach { destination ->
                val selected = currentTopDestination == destination.route
                SpatialSideNavItem(
                    primaryLabel = stringResource(destination.labelId),
                    icon = { AnimatedNavIcon(destination = destination, selected = selected) },
                    selected = selected,
                    onClick = { onNavigateToRoute(destination.route) },
                )
            }
        }
    } else {
        Column(
            modifier =
                modifier
                    .padding(12.dp)
                    .tonalSurface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    )
                    .selectableGroup()
                    .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TopLevelDestinations.forEach { destination ->
                val selected = currentTopDestination == destination.route
                NavigationRailItemVariant(
                    icon = {
                        AnimatedNavIcon(
                            destination = destination,
                            selected = selected,
                            contentDescription = stringResource(destination.labelId),
                        )
                    },
                    modifier = Modifier,
                    selected = selected,
                    onClick = { onNavigateToRoute(destination.route) },
                )
            }
        }
    }
}

@Preview(device = "spec:width=673dp,height=841dp")
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun ExpandedPreview() {
    val widthDp = LocalConfiguration.current.screenWidthDp
    var currentRoute = remember { mutableStateOf(Route.HOME) }

    CompositionLocalProvider(
        LocalWindowWidthState provides
            if (widthDp > 480) WindowWidthSizeClass.Expanded
            else if (widthDp > 360) WindowWidthSizeClass.Medium else WindowWidthSizeClass.Compact
    ) {
        Row {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            NavigationDrawer(
                currentRoute = currentRoute.value,
                currentTopDestination = currentRoute.value,
                drawerState = drawerState,
                onNavigateToRoute = { currentRoute.value = it },
                onDismissRequest = {},
            ) {
                DownloadPageImplV2(taskDownloadStateMap = remember { mutableStateMapOf() }) { _, _
                    ->
                }
            }
        }
    }
}
