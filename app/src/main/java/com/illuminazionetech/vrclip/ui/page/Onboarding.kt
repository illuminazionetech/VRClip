package com.illuminazionetech.vrclip.ui.page

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.util.ONBOARDING_COMPLETED
import com.illuminazionetech.vrclip.util.PreferenceUtil.getBoolean
import com.illuminazionetech.vrclip.util.PreferenceUtil.updateBoolean
import com.illuminazionetech.vrclip.util.StorageUtil
import com.illuminazionetech.vrclip.util.YtDlpEngine

private enum class OnboardingStep {
    Welcome,
    Storage,
    Notifications,
    Engine,
}

/**
 * First-run setup flow: explains the app, walks the user through the storage grant (All files
 * access on API 30+, legacy write permission below), the notification permission on API 33+,
 * and shows the download engine getting ready. Shown once; every step can be skipped and
 * revisited later from Settings.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingFlow() {
    var completed by remember { mutableStateOf(ONBOARDING_COMPLETED.getBoolean()) }
    if (completed) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var step by remember { mutableStateOf(OnboardingStep.Welcome) }
    var storageGranted by remember {
        mutableStateOf(StorageUtil.isStorageAccessGranted(context))
    }

    // The All files access grant happens in system settings, so re-check when we come back.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                storageGranted = StorageUtil.isStorageAccessGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val legacyStoragePermission =
        rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE) {
            storageGranted = StorageUtil.isStorageAccessGranted(context)
        }

    val notificationPermission =
        if (Build.VERSION.SDK_INT >= 33) {
            rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            null
        }

    fun finish() {
        ONBOARDING_COMPLETED.updateBoolean(true)
        completed = true
    }

    fun advanceFrom(current: OnboardingStep) {
        step =
            when (current) {
                OnboardingStep.Welcome -> OnboardingStep.Storage
                OnboardingStep.Storage ->
                    if (
                        notificationPermission != null &&
                            notificationPermission.status !is PermissionStatus.Granted
                    ) {
                        OnboardingStep.Notifications
                    } else {
                        OnboardingStep.Engine
                    }
                OnboardingStep.Notifications -> OnboardingStep.Engine
                OnboardingStep.Engine -> {
                    finish()
                    return
                }
            }
        // Skip the storage step entirely when access is already there.
        if (step == OnboardingStep.Storage && storageGranted) advanceFrom(OnboardingStep.Storage)
    }

    AlertDialog(
        onDismissRequest = {
            // Dismissing counts as skipping the remaining steps, never as a dead end: all
            // steps stay reachable from Settings.
            finish()
        },
        icon = {
            Icon(
                imageVector =
                    when (step) {
                        OnboardingStep.Welcome -> Icons.Rounded.RocketLaunch
                        OnboardingStep.Storage -> Icons.Rounded.FolderOpen
                        OnboardingStep.Notifications -> Icons.Rounded.NotificationsActive
                        OnboardingStep.Engine -> Icons.Rounded.Download
                    },
                contentDescription = null,
            )
        },
        title = {
            Text(
                text =
                    stringResource(
                        when (step) {
                            OnboardingStep.Welcome -> R.string.onboarding_welcome_title
                            OnboardingStep.Storage -> R.string.onboarding_storage_title
                            OnboardingStep.Notifications -> R.string.enable_notifications
                            OnboardingStep.Engine -> R.string.onboarding_engine_title
                        }
                    )
            )
        },
        text = {
            AnimatedContent(targetState = step, label = "onboardingStep") { currentStep ->
                when (currentStep) {
                    OnboardingStep.Welcome ->
                        Text(stringResource(R.string.onboarding_welcome_desc))
                    OnboardingStep.Storage ->
                        Column {
                            Text(stringResource(R.string.onboarding_storage_desc))
                            if (storageGranted) {
                                Row(
                                    modifier = Modifier.padding(top = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text =
                                            stringResource(R.string.onboarding_storage_granted),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    OnboardingStep.Notifications ->
                        Text(stringResource(R.string.enable_notifications_desc))
                    OnboardingStep.Engine -> EngineStepContent()
                }
            }
        },
        confirmButton = {
            when (step) {
                OnboardingStep.Welcome -> {
                    Button(onClick = { advanceFrom(OnboardingStep.Welcome) }) {
                        Text(stringResource(R.string.get_started))
                    }
                }
                OnboardingStep.Storage -> {
                    if (storageGranted) {
                        Button(onClick = { advanceFrom(OnboardingStep.Storage) }) {
                            Text(stringResource(R.string.onboarding_continue))
                        }
                    } else {
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 30) {
                                    StorageUtil.launchAllFilesAccessSettings(context)
                                } else {
                                    legacyStoragePermission.launchPermissionRequest()
                                }
                            }
                        ) {
                            Text(stringResource(R.string.grant_access))
                        }
                    }
                }
                OnboardingStep.Notifications -> {
                    Button(
                        onClick = {
                            notificationPermission?.launchPermissionRequest()
                            advanceFrom(OnboardingStep.Notifications)
                        }
                    ) {
                        Text(stringResource(R.string.okay))
                    }
                }
                OnboardingStep.Engine -> {
                    Button(onClick = { finish() }) { Text(stringResource(R.string.done)) }
                }
            }
        },
        dismissButton = {
            if (step != OnboardingStep.Welcome && step != OnboardingStep.Engine) {
                TextButton(onClick = { advanceFrom(step) }) {
                    Text(stringResource(R.string.skip))
                }
            }
        },
    )
}

@Composable
private fun EngineStepContent() {
    val engineState by YtDlpEngine.state.collectAsStateWithLifecycle()
    Column {
        Text(stringResource(R.string.onboarding_engine_desc))
        Row(
            modifier = Modifier.padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (val state = engineState) {
                is YtDlpEngine.State.Ready -> {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = state.version ?: stringResource(R.string.status_completed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                is YtDlpEngine.State.InitFailed -> {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.engine_init_failed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text =
                            stringResource(
                                if (engineState is YtDlpEngine.State.Updating)
                                    R.string.engine_updating
                                else R.string.engine_preparing
                            ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
