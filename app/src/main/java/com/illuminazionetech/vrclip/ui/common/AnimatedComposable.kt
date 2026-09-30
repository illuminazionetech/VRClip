package com.illuminazionetech.vrclip.ui.common

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/*
 * Page transitions, following the Material 3 motion guidance:
 * - switching between top-level destinations (the navigation bar/rail) uses "fade through";
 * - moving deeper into or back out of a hierarchy uses the horizontal "shared axis".
 * Navigation Compose drives these transitions from the predictive back gesture as well, so the
 * same specs animate a back swipe progressively.
 */

/** Material 3 "emphasized decelerate": enters the screen fast and settles softly. */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Material 3 "emphasized accelerate": leaves the screen quickly. */
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

private const val SHARED_AXIS_DURATION = 400
private const val SHARED_AXIS_EXIT_DURATION = 250
private const val FADE_THROUGH_DURATION = 300
private const val FADE_THROUGH_OUT = 90
private const val SLIDE_FRACTION = 0.08f

private fun slideDistance(fullWidth: Int) = (fullWidth * SLIDE_FRACTION).toInt()

fun sharedAxisForwardEnter(): EnterTransition =
    slideInHorizontally(tween(SHARED_AXIS_DURATION, easing = EmphasizedDecelerate)) {
        slideDistance(it)
    } + fadeIn(tween(SHARED_AXIS_DURATION - 100, delayMillis = 100))

fun sharedAxisForwardExit(): ExitTransition =
    slideOutHorizontally(tween(SHARED_AXIS_EXIT_DURATION, easing = EmphasizedAccelerate)) {
        -slideDistance(it)
    } + fadeOut(tween(FADE_THROUGH_OUT))

fun sharedAxisBackwardEnter(): EnterTransition =
    slideInHorizontally(tween(SHARED_AXIS_DURATION, easing = EmphasizedDecelerate)) {
        -slideDistance(it)
    } + fadeIn(tween(SHARED_AXIS_DURATION - 100, delayMillis = 100))

fun sharedAxisBackwardExit(): ExitTransition =
    slideOutHorizontally(tween(SHARED_AXIS_EXIT_DURATION, easing = EmphasizedAccelerate)) {
        slideDistance(it)
    } + fadeOut(tween(FADE_THROUGH_OUT))

fun fadeThroughEnter(): EnterTransition =
    fadeIn(tween(FADE_THROUGH_DURATION - FADE_THROUGH_OUT, delayMillis = FADE_THROUGH_OUT)) +
        scaleIn(
            tween(FADE_THROUGH_DURATION - FADE_THROUGH_OUT, delayMillis = FADE_THROUGH_OUT),
            initialScale = 0.94f,
        )

fun fadeThroughExit(): ExitTransition = fadeOut(tween(FADE_THROUGH_OUT))

/**
 * NavHost-level transitions. [isTopLevel] tells whether a destination belongs to the navigation
 * bar/rail, so switching between two of them fades through instead of sliding.
 */
class NavTransitions(private val isTopLevel: (NavBackStackEntry) -> Boolean) {
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTopLevelSwitch() =
        isTopLevel(initialState) && isTopLevel(targetState)

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (isTopLevelSwitch()) fadeThroughEnter() else sharedAxisForwardEnter()
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (isTopLevelSwitch()) fadeThroughExit() else sharedAxisForwardExit()
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (isTopLevelSwitch()) fadeThroughEnter() else sharedAxisBackwardEnter()
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (isTopLevelSwitch()) fadeThroughExit() else sharedAxisBackwardExit()
    }
}

/** A destination using the NavHost-level transitions. */
fun NavGraphBuilder.animatedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) = composable(route = route, arguments = arguments, deepLinks = deepLinks, content = content)

/** A destination that rises from the bottom edge, for full-screen detail views such as logs. */
fun NavGraphBuilder.slideInVerticallyComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) =
    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
        enterTransition = {
            slideInVertically(tween(SHARED_AXIS_DURATION, easing = EmphasizedDecelerate)) {
                it / 4
            } + fadeIn(tween(SHARED_AXIS_DURATION - 100, delayMillis = 60))
        },
        exitTransition = { fadeOut(tween(FADE_THROUGH_OUT)) },
        popEnterTransition = { fadeIn(tween(FADE_THROUGH_DURATION)) },
        popExitTransition = {
            slideOutVertically(tween(SHARED_AXIS_EXIT_DURATION, easing = EmphasizedAccelerate)) {
                it / 4
            } + fadeOut(tween(SHARED_AXIS_EXIT_DURATION))
        },
        content = content,
    )
