package com.illuminazionetech.vrclip.ui.page

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Vrpano
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.ui.common.MorphingShapeBox
import com.illuminazionetech.vrclip.util.ONBOARDING_COMPLETED
import com.illuminazionetech.vrclip.util.PreferenceUtil.getBoolean
import com.illuminazionetech.vrclip.util.PreferenceUtil.updateBoolean
import com.illuminazionetech.vrclip.util.StorageUtil
import com.illuminazionetech.vrclip.util.YtDlpEngine

private enum class OnboardingStep {
    Welcome,
    Immersive,
    Storage,
    Notifications,
    Engine,
}

/**
 * First-run setup, shown once as a full-screen sequence of slides: what the app does, the storage
 * grant it needs (All files access on API 30+, legacy write permission below), the notification
 * permission on API 33+, and the download engine getting ready. Every step can be skipped and
 * revisited later from Settings; steps already satisfied are skipped automatically.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingFlow() {
    var completed by remember { mutableStateOf(ONBOARDING_COMPLETED.getBoolean()) }
    if (completed) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var step by rememberSaveable { mutableStateOf(OnboardingStep.Welcome) }
    var forward by remember { mutableStateOf(true) }
    var storageGranted by remember { mutableStateOf(StorageUtil.isStorageAccessGranted(context)) }

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
    val needsNotificationStep =
        notificationPermission != null && notificationPermission.status !is PermissionStatus.Granted

    val steps =
        OnboardingStep.entries.filter {
            it != OnboardingStep.Notifications || needsNotificationStep || step == it
        }

    fun finish() {
        ONBOARDING_COMPLETED.updateBoolean(true)
        completed = true
    }

    fun next() {
        forward = true
        var index = steps.indexOf(step) + 1
        // Skip the storage slide when access is already there.
        if (steps.getOrNull(index) == OnboardingStep.Storage && storageGranted) index++
        steps.getOrNull(index)?.let { step = it } ?: finish()
    }

    fun previous() {
        forward = false
        steps.getOrNull(steps.indexOf(step) - 1)?.let { step = it }
    }

    Dialog(
        onDismissRequest = ::finish,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
    ) {
        BackHandler(enabled = step != OnboardingStep.Welcome) { previous() }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Spacer(Modifier.weight(1f))
                    if (step != OnboardingStep.Engine) {
                        TextButton(onClick = ::finish) { Text(stringResource(R.string.skip)) }
                    }
                }

                val motionScheme = MaterialTheme.motionScheme
                AnimatedContent(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    targetState = step,
                    transitionSpec = {
                        val direction = if (forward) 1 else -1
                        (slideInHorizontally(motionScheme.defaultSpatialSpec()) {
                                direction * it / 3
                            } + fadeIn(motionScheme.defaultEffectsSpec()))
                            .togetherWith(
                                slideOutHorizontally(motionScheme.fastSpatialSpec()) {
                                    -direction * it / 3
                                } + fadeOut(motionScheme.fastEffectsSpec())
                            )
                    },
                    label = "onboardingStep",
                ) { current ->
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        StepIllustration(current)
                        Spacer(Modifier.height(40.dp))
                        Text(
                            text = stringResource(current.titleId()),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 480.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(current.descriptionId()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 480.dp),
                        )
                        when (current) {
                            OnboardingStep.Storage ->
                                if (storageGranted) {
                                    StatusLine(
                                        icon = Icons.Rounded.CheckCircle,
                                        text = stringResource(R.string.onboarding_storage_granted),
                                    )
                                }
                            OnboardingStep.Engine -> EngineStatusLine()
                            else -> {}
                        }
                    }
                }

                PageIndicator(count = steps.size, selected = steps.indexOf(step))
                Spacer(Modifier.height(24.dp))

                Button(
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(min = 56.dp),
                    onClick = {
                        when (step) {
                            OnboardingStep.Storage ->
                                if (storageGranted) next()
                                else if (Build.VERSION.SDK_INT >= 30) {
                                    StorageUtil.launchAllFilesAccessSettings(context)
                                } else {
                                    legacyStoragePermission.launchPermissionRequest()
                                }
                            OnboardingStep.Notifications -> {
                                notificationPermission?.launchPermissionRequest()
                                next()
                            }
                            OnboardingStep.Engine -> finish()
                            else -> next()
                        }
                    },
                ) {
                    Text(
                        text =
                            stringResource(
                                when (step) {
                                    OnboardingStep.Welcome -> R.string.get_started
                                    OnboardingStep.Storage ->
                                        if (storageGranted) R.string.onboarding_continue
                                        else R.string.grant_access
                                    OnboardingStep.Notifications -> R.string.onboarding_allow
                                    OnboardingStep.Engine -> R.string.onboarding_start_using
                                    else -> R.string.onboarding_continue
                                }
                            ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                // A secondary "not now" keeps permission slides skippable one by one.
                Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                    if (
                        (step == OnboardingStep.Storage && !storageGranted) ||
                            step == OnboardingStep.Notifications
                    ) {
                        TextButton(onClick = ::next) { Text(stringResource(R.string.not_now)) }
                    }
                }
            }
        }
    }
}

private fun OnboardingStep.titleId(): Int =
    when (this) {
        OnboardingStep.Welcome -> R.string.onboarding_welcome_title
        OnboardingStep.Immersive -> R.string.onboarding_immersive_title
        OnboardingStep.Storage -> R.string.onboarding_storage_title
        OnboardingStep.Notifications -> R.string.onboarding_notifications_title
        OnboardingStep.Engine -> R.string.onboarding_engine_title
    }

private fun OnboardingStep.descriptionId(): Int =
    when (this) {
        OnboardingStep.Welcome -> R.string.onboarding_welcome_desc
        OnboardingStep.Immersive -> R.string.onboarding_immersive_desc
        OnboardingStep.Storage -> R.string.onboarding_storage_desc
        OnboardingStep.Notifications -> R.string.enable_notifications_desc
        OnboardingStep.Engine -> R.string.onboarding_engine_desc
    }

/** A large Material 3 Expressive shape per slide, gently rotating as the slides change. */
@Composable
private fun StepIllustration(step: OnboardingStep) {
    val (shape, icon) =
        when (step) {
            OnboardingStep.Welcome -> MaterialShapes.Cookie12Sided to null
            OnboardingStep.Immersive -> MaterialShapes.Flower to Icons.Rounded.Vrpano
            OnboardingStep.Storage -> MaterialShapes.Clover4Leaf to Icons.Rounded.FolderOpen
            OnboardingStep.Notifications ->
                MaterialShapes.SoftBurst to Icons.Rounded.NotificationsActive
            OnboardingStep.Engine -> MaterialShapes.Sunny to Icons.Rounded.Download
        }
    val container =
        when (step) {
            OnboardingStep.Immersive -> MaterialTheme.colorScheme.tertiaryContainer
            OnboardingStep.Notifications -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        }
    val animatedContainer by
        animateColorAsState(
            targetValue = container,
            animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
            label = "illustrationColor",
        )
    MorphingShapeBox(
        shape = shape,
        color = animatedContainer,
        rotation = step.ordinal * 30f,
        modifier = Modifier.size(200.dp),
    ) {
        if (icon == null) {
            Image(
                painter = painterResource(R.drawable.vrclip_seal),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.contentColorForContainer(container),
            )
        }
        if (step == OnboardingStep.Immersive) {
            FeatureBadge(Icons.Rounded.ViewInAr, Modifier.align(Alignment.TopEnd))
            FeatureBadge(Icons.Rounded.Public, Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
private fun androidx.compose.material3.ColorScheme.contentColorForContainer(
    container: androidx.compose.ui.graphics.Color
) =
    when (container) {
        tertiaryContainer -> onTertiaryContainer
        secondaryContainer -> onSecondaryContainer
        else -> onPrimaryContainer
    }

@Composable
private fun FeatureBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** Dots for the slides; the current one stretches into a pill. */
@Composable
private fun PageIndicator(count: Int, selected: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            val active = index == selected
            val width by
                animateDpAsState(
                    targetValue = if (active) 28.dp else 8.dp,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "dotWidth",
                )
            val color by
                animateColorAsState(
                    targetValue =
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    label = "dotColor",
                )
            Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, error: Boolean = false) {
    val color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(modifier = Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun EngineStatusLine() {
    val engineState by YtDlpEngine.state.collectAsStateWithLifecycle()
    when (val state = engineState) {
        is YtDlpEngine.State.Ready ->
            StatusLine(
                icon = Icons.Rounded.CheckCircle,
                text = state.version ?: stringResource(R.string.status_completed),
            )
        is YtDlpEngine.State.InitFailed ->
            StatusLine(
                icon = Icons.Rounded.ErrorOutline,
                text = stringResource(R.string.engine_init_failed),
                error = true,
            )
        else ->
            Row(
                modifier = Modifier.padding(top = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoadingIndicator(modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text =
                        stringResource(
                            if (state is YtDlpEngine.State.Updating) R.string.engine_updating
                            else R.string.engine_preparing
                        ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}
