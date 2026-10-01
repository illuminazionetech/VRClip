package com.illuminazionetech.vrclip.player.stereo

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illuminazionetech.vrclip.App
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.util.DatabaseUtil
import com.illuminazionetech.vrclip.util.toFileSizeText
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Asks before downloading the depth model (about 92 MB) and shows the download; calls [onReady]
 * once the verified model is on the device. The download keeps going if the dialog is closed.
 */
@Composable
fun DepthModelDialog(onDismiss: () -> Unit, onReady: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { DepthModelManager.get(context) }
    val state by manager.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) { if (state is DepthModelManager.State.Installed) onReady() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
        title = { Text(stringResource(R.string.stereo_model_title)) },
        text = {
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                label = "modelState",
            ) { current ->
                Column {
                    Text(
                        stringResource(
                            R.string.stereo_model_desc,
                            DepthModelManager.DOWNLOAD_BYTES.toFileSizeText(),
                        )
                    )
                    when (current) {
                        is DepthModelManager.State.Downloading -> {
                            Spacer(Modifier.height(20.dp))
                            if (current.progress >= 0f) {
                                LinearWavyProgressIndicator(
                                    progress = { current.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text =
                                    "${current.downloadedBytes.toFileSizeText()} / " +
                                        current.totalBytes.toFileSizeText(),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        is DepthModelManager.State.Failed -> {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text =
                                    stringResource(
                                        if (current.corrupted) R.string.stereo_model_corrupted
                                        else R.string.stereo_model_failed
                                    ),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        else -> Unit
                    }
                }
            }
        },
        confirmButton = {
            if (state !is DepthModelManager.State.Downloading) {
                Button(
                    onClick = {
                        manager.resetError()
                        App.applicationScope.launch { manager.download() }
                    }
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
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(
                        if (state is DepthModelManager.State.Downloading) R.string.hide
                        else R.string.cancel
                    )
                )
            }
        },
    )
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
                            DepthModelManager.DOWNLOAD_BYTES.toFileSizeText(),
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
