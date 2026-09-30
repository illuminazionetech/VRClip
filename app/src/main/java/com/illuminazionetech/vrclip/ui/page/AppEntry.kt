package com.illuminazionetech.vrclip.ui.page

import android.webkit.CookieManager
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.illuminazionetech.vrclip.player.PlayerLauncher
import com.illuminazionetech.vrclip.player.PlayerScreen
import com.illuminazionetech.vrclip.ui.common.LocalIsVRMode
import com.illuminazionetech.vrclip.ui.common.LocalWindowWidthState
import com.illuminazionetech.vrclip.ui.common.NavTransitions
import com.illuminazionetech.vrclip.ui.common.Route
import com.illuminazionetech.vrclip.ui.common.animatedComposable
import com.illuminazionetech.vrclip.ui.common.arg
import com.illuminazionetech.vrclip.ui.common.id
import com.illuminazionetech.vrclip.ui.common.slideInVerticallyComposable
import com.illuminazionetech.vrclip.ui.common.zoomComposable
import com.illuminazionetech.vrclip.ui.page.command.TaskListPage
import com.illuminazionetech.vrclip.ui.page.command.TaskLogPage
import com.illuminazionetech.vrclip.ui.page.downloadv2.DownloadPageV2
import com.illuminazionetech.vrclip.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.illuminazionetech.vrclip.ui.page.settings.SettingsPage
import com.illuminazionetech.vrclip.ui.page.settings.about.AboutPage
import com.illuminazionetech.vrclip.ui.page.settings.about.CreditsPage
import com.illuminazionetech.vrclip.ui.page.settings.about.UpdatePage
import com.illuminazionetech.vrclip.ui.page.settings.appearance.AppearancePreferences
import com.illuminazionetech.vrclip.ui.page.settings.appearance.DarkThemePreferences
import com.illuminazionetech.vrclip.ui.page.settings.appearance.LanguagePage
import com.illuminazionetech.vrclip.ui.page.settings.command.TemplateEditPage
import com.illuminazionetech.vrclip.ui.page.settings.command.TemplateListPage
import com.illuminazionetech.vrclip.ui.page.settings.directory.DownloadDirectoryPreferences
import com.illuminazionetech.vrclip.ui.page.settings.format.DownloadFormatPreferences
import com.illuminazionetech.vrclip.ui.page.settings.format.SubtitlePreference
import com.illuminazionetech.vrclip.ui.page.settings.general.GeneralDownloadPreferences
import com.illuminazionetech.vrclip.ui.page.settings.interaction.InteractionPreferencePage
import com.illuminazionetech.vrclip.ui.page.settings.network.CookieProfilePage
import com.illuminazionetech.vrclip.ui.page.settings.network.CookiesViewModel
import com.illuminazionetech.vrclip.ui.page.settings.network.NetworkPreferences
import com.illuminazionetech.vrclip.ui.page.settings.network.WebViewPage
import com.illuminazionetech.vrclip.ui.page.settings.player.PlayerPreferences
import com.illuminazionetech.vrclip.ui.page.settings.troubleshooting.TroubleShootingPage
import com.illuminazionetech.vrclip.ui.page.videolist.VideoListPage
import org.koin.androidx.compose.koinViewModel

@Composable
fun AppEntry(dialogViewModel: DownloadDialogViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val sheetState by dialogViewModel.sheetStateFlow.collectAsStateWithLifecycle()
    val cookiesViewModel: CookiesViewModel = koinViewModel()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val onNavigateBack: () -> Unit = {
        if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
            navController.popBackStack()
        }
    }

    // A shared link or the download button opens the configuration sheet, which lives on the
    // queue page: bring that page to the front first.
    LaunchedEffect(sheetState) {
        if (
            sheetState is DownloadDialogViewModel.SheetState.Configure &&
                navController.currentDestination?.route != Route.HOME
        ) {
            navController.navigateToTopLevel(TopLevelDestination.Queue)
        }
    }

    val transitions = remember { NavTransitions(isTopLevel = { it.destination.isTopLevelRoot() }) }

    AppNavigationScaffold(
        currentDestination = currentDestination,
        navigationSuiteType =
            navigationSuiteTypeFor(LocalWindowWidthState.current, LocalIsVRMode.current),
        onNavigate = { navController.navigateToTopLevel(it) },
        onNewDownload = {
            dialogViewModel.postAction(DownloadDialogViewModel.Action.ShowSheet())
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = Route.HOME,
            enterTransition = transitions.enter,
            exitTransition = transitions.exit,
            popEnterTransition = transitions.popEnter,
            popExitTransition = transitions.popExit,
        ) {
            animatedComposable(Route.HOME) { DownloadPageV2(dialogViewModel = dialogViewModel) }
            animatedComposable(Route.DOWNLOADS) {
                VideoListPage(
                    onNavigateToPlayer = { info ->
                        PlayerLauncher.launch(
                            context = context,
                            videoId = info.id,
                            videoPath = info.videoPath,
                            projectionOverride = info.projectionOverride,
                            onNavigateToPlayer = { id -> navController.navigate(Route.PLAYER id id) },
                        )
                    }
                )
            }
            zoomComposable(
                Route.PLAYER arg Route.VIDEO_ID,
                arguments = listOf(navArgument(Route.VIDEO_ID) { type = NavType.IntType }),
            ) {
                PlayerScreen(
                    videoId = it.arguments?.getInt(Route.VIDEO_ID) ?: -1,
                    onNavigateBack = onNavigateBack,
                )
            }
            animatedComposable(Route.TASK_LIST) {
                TaskListPage(onNavigateToDetail = { navController.navigate(Route.TASK_LOG id it) })
            }
            slideInVerticallyComposable(
                Route.TASK_LOG arg Route.TASK_HASHCODE,
                arguments = listOf(navArgument(Route.TASK_HASHCODE) { type = NavType.IntType }),
            ) {
                TaskLogPage(
                    onNavigateBack = onNavigateBack,
                    taskHashCode = it.arguments?.getInt(Route.TASK_HASHCODE) ?: -1,
                )
            }

            settingsGraph(
                onNavigateBack = onNavigateBack,
                onNavigateTo = { route ->
                    navController.navigate(route = route) { launchSingleTop = true }
                },
                cookiesViewModel = cookiesViewModel,
            )
        }

        OnboardingFlow()
        AppUpdater()
        YtdlpUpdater()
    }
}

/** Phones get a bottom bar; tablets, foldables, landscape phones and the Quest panel a rail. */
private fun navigationSuiteTypeFor(width: WindowWidthSizeClass, isVR: Boolean) =
    when {
        isVR -> AppNavigationLayout.WideRailExpanded
        width == WindowWidthSizeClass.Compact -> AppNavigationLayout.BottomBar
        else -> AppNavigationLayout.Rail
    }

/** True for the root screen of each top-level destination (where the navigation is shown). */
fun NavDestination.isTopLevelRoot(): Boolean = route in TopLevelDestination.rootRoutes

fun NavDestination?.isInHierarchyOf(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.route == destination.route } == true

fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

fun NavGraphBuilder.settingsGraph(
    onNavigateBack: () -> Unit,
    onNavigateTo: (route: String) -> Unit,
    cookiesViewModel: CookiesViewModel,
) {
    navigation(startDestination = Route.SETTINGS_PAGE, route = Route.SETTINGS) {
        animatedComposable(Route.SETTINGS_PAGE) { SettingsPage(onNavigateTo = onNavigateTo) }
        animatedComposable(Route.GENERAL_DOWNLOAD_PREFERENCES) {
            GeneralDownloadPreferences(onNavigateBack = { onNavigateBack() }) {
                onNavigateTo(Route.TEMPLATE)
            }
        }
        animatedComposable(Route.PLAYER_PREFERENCES) { PlayerPreferences(onNavigateBack) }
        animatedComposable(Route.DOWNLOAD_FORMAT) {
            DownloadFormatPreferences(onNavigateBack = onNavigateBack) {
                onNavigateTo(Route.SUBTITLE_PREFERENCES)
            }
        }
        animatedComposable(Route.SUBTITLE_PREFERENCES) { SubtitlePreference { onNavigateBack() } }
        animatedComposable(Route.ABOUT) {
            AboutPage(
                onNavigateBack = onNavigateBack,
                onNavigateToCreditsPage = { onNavigateTo(Route.CREDITS) },
                onNavigateToUpdatePage = { onNavigateTo(Route.AUTO_UPDATE) },
            )
        }
        animatedComposable(Route.CREDITS) { CreditsPage(onNavigateBack) }
        animatedComposable(Route.AUTO_UPDATE) { UpdatePage(onNavigateBack) }
        animatedComposable(Route.APPEARANCE) {
            AppearancePreferences(onNavigateBack = onNavigateBack, onNavigateTo = onNavigateTo)
        }
        animatedComposable(Route.INTERACTION) { InteractionPreferencePage(onBack = onNavigateBack) }
        animatedComposable(Route.LANGUAGES) { LanguagePage { onNavigateBack() } }
        animatedComposable(Route.DOWNLOAD_DIRECTORY) {
            DownloadDirectoryPreferences { onNavigateBack() }
        }
        animatedComposable(Route.TEMPLATE) {
            TemplateListPage(onNavigateBack = onNavigateBack) {
                onNavigateTo(Route.TEMPLATE_EDIT id it)
            }
        }
        animatedComposable(
            Route.TEMPLATE_EDIT arg Route.TEMPLATE_ID,
            arguments = listOf(navArgument(Route.TEMPLATE_ID) { type = NavType.IntType }),
        ) {
            TemplateEditPage(onNavigateBack, it.arguments?.getInt(Route.TEMPLATE_ID) ?: -1)
        }
        animatedComposable(Route.DARK_THEME) { DarkThemePreferences { onNavigateBack() } }
        animatedComposable(Route.NETWORK_PREFERENCES) {
            NetworkPreferences(
                navigateToCookieProfilePage = { onNavigateTo(Route.COOKIE_PROFILE) }
            ) {
                onNavigateBack()
            }
        }
        animatedComposable(Route.COOKIE_PROFILE) {
            CookieProfilePage(
                cookiesViewModel = cookiesViewModel,
                navigateToCookieGeneratorPage = { onNavigateTo(Route.COOKIE_GENERATOR_WEBVIEW) },
            ) {
                onNavigateBack()
            }
        }
        animatedComposable(Route.COOKIE_GENERATOR_WEBVIEW) {
            WebViewPage(cookiesViewModel = cookiesViewModel) {
                onNavigateBack()
                CookieManager.getInstance().flush()
            }
        }
        animatedComposable(Route.TROUBLESHOOTING) {
            TroubleShootingPage(onNavigateTo = onNavigateTo, onBack = onNavigateBack)
        }
    }
}
