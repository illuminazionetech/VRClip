package com.illuminazionetech.vrclip.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A full-screen page that rises over everything (navigation included) and slides back down when
 * dismissed, by back gesture or through the `dismiss` callback handed to [content]. Used for
 * focused, multi-step flows such as picking formats or playlist entries.
 */
@Composable
fun FullScreenSheet(
    onDismissRequest: () -> Unit,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    var dismissing by remember { mutableStateOf(false) }
    val dismiss: () -> Unit = {
        dismissing = true
        visibleState.targetState = false
    }

    LaunchedEffect(dismissing, visibleState.isIdle, visibleState.currentState) {
        if (dismissing && visibleState.isIdle && !visibleState.currentState) onDismissRequest()
    }

    Dialog(
        onDismissRequest = dismiss,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter =
                slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 4 } +
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit =
                slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 4 } +
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                content(dismiss)
            }
        }
    }
}
