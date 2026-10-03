package com.illuminazionetech.vrclip.player.stereo

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.util.DatabaseUtil
import com.illuminazionetech.vrclip.util.toFileSizeText
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Asks before downloading the depth model and shows the download; calls [onReady] once the verified
 * model is on the device. The download runs as a background job, so closing the dialog does not
 * stop it.
 */
@Composable
fun DepthModelDialog(onDismiss: () -> Unit, onReady: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { DepthModelManager.get(context) }
    val state by manager.state.collectAsStateWithLifecycle()
    val busy = state.isBusy()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(state) { if (state is DepthModelManager.State.Installed) onReady() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.ViewInAr, contentDescription = null) },
        title = { Text(stringResource(R.string.stereo_model_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.stereo_model_desc,
                        DepthModel.totalBytes.toFileSizeText(),
                    )
                )
                DepthModelStatus(state = state, modifier = Modifier.padding(top = 20.dp))
            }
        },
        confirmButton = {
            if (busy) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.hide)) }
            } else {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        manager.resetError()
                        manager.start()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(
                        stringResource(
                            if (state is DepthModelManager.State.Failed) R.string.retry
                            else R.string.stereo_model_download
                        )
                    )
                }
            }
        },
        dismissButton = {
            if (busy) {
                TextButton(onClick = manager::cancel) { Text(stringResource(R.string.cancel)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** True while the model is being queued, downloaded or checked. */
fun DepthModelManager.State.isBusy(): Boolean =
    this is DepthModelManager.State.Queued ||
        this is DepthModelManager.State.Downloading ||
        this is DepthModelManager.State.Verifying

/**
 * Progress and outcome of the model download, shared by the dialog, the settings page and the Meta
 * Quest control panel. Confirms completion and failure with a haptic tick.
 */
@Composable
fun DepthModelStatus(state: DepthModelManager.State, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    var previous by remember { mutableStateOf(state) }
    LaunchedEffect(state) {
        if (previous.isBusy()) {
            when (state) {
                is DepthModelManager.State.Installed ->
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                is DepthModelManager.State.Failed ->
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                else -> Unit
            }
        }
        previous = state
    }
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                .using(SizeTransform(clip = false))
        },
        modifier = modifier.fillMaxWidth(),
        label = "modelState",
    ) { current ->
        when (current) {
            DepthModelManager.State.Queued,
            DepthModelManager.State.Verifying ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text =
                            stringResource(
                                if (current is DepthModelManager.State.Queued)
                                    R.string.stereo_model_queued
                                else R.string.stereo_model_checking
                            ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            is DepthModelManager.State.Downloading -> {
                val progress by
                    animateFloatAsState(
                        targetValue = current.progress.coerceIn(0f, 1f),
                        animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
                        label = "modelProgress",
                    )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text =
                                "${current.downloadedBytes.toFileSizeText()} / " +
                                    current.totalBytes.toFileSizeText(),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(current.progress.coerceIn(0f, 1f) * 100).roundToInt()}%",
                            style =
                                MaterialTheme.typography.labelLarge.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                        )
                    }
                    Text(
                        text = stringResource(R.string.stereo_model_background),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is DepthModelManager.State.Failed ->
                Text(
                    text =
                        when (current.reason) {
                            DepthModelManager.Reason.Network ->
                                stringResource(R.string.stereo_model_failed)
                            DepthModelManager.Reason.Corrupted ->
                                stringResource(R.string.stereo_model_corrupted)
                            DepthModelManager.Reason.NoSpace ->
                                stringResource(
                                    R.string.stereo_model_no_space,
                                    DepthModel.totalBytes.toFileSizeText(),
                                )
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            else -> Spacer(Modifier.height(0.dp))
        }
    }
}

/**
 * Confirms a permanent 2D to 3D conversion: explains that the 3D file replaces the original, what
 * the result will be (size, codec, HDR to SDR) and what it needs (the model download, time), then
 * starts [StereoConversionWorker].
 */
@Composable
fun ConvertTo3dDialog(videoId: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { DepthModelManager.get(context) }
    var starting by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val details by
        produceState<Result<Pair<SourceInfo, ConversionPlan>>?>(initialValue = null, videoId) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val info = DatabaseUtil.getInfoById(videoId)
                        val source = StereoConverter.probe(File(info.videoPath))
                        source to StereoConverter.plan(source)
                    }
                }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.ViewInAr, contentDescription = null) },
        title = { Text(stringResource(R.string.stereo_convert_title)) },
        text = {
            Column {
                Text(stringResource(R.string.stereo_convert_desc))
                val result = details
                val plan = result?.getOrNull()
                if (plan != null) {
                    val (source, conversion) = plan
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text =
                            stringResource(
                                R.string.stereo_convert_output,
                                conversion.width,
                                conversion.height,
                                if (conversion.mimeType.endsWith("hevc")) "HEVC" else "H.264",
                                stringResource(
                                    if (conversion.packing == StereoPacking.Full)
                                        R.string.stereo_packing_full
                                    else R.string.stereo_packing_half
                                ),
                            ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (source.isHdr) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.stereo_convert_hdr))
                    }
                } else if (result?.isFailure == true) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text =
                            stringResource(
                                if (
                                    result.exceptionOrNull() is ConversionException &&
                                        (result.exceptionOrNull() as ConversionException).reason ==
                                            ConversionException.Reason.EncoderUnavailable
                                )
                                    R.string.stereo_error_encoder
                                else R.string.stereo_error_missing
                            ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (!manager.isReady()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(
                            R.string.stereo_convert_needs_model,
                            DepthModel.totalBytes.toFileSizeText(),
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = details?.isSuccess == true && !starting,
                onClick = {
                    starting = true
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    StereoConversionWorker.start(context, videoId)
                    Toast.makeText(context, R.string.stereo_convert_started, Toast.LENGTH_LONG)
                        .show()
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.stereo_convert_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
