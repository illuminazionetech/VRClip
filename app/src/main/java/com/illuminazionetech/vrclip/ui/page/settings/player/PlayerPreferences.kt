package com.illuminazionetech.vrclip.ui.page.settings.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.ScreenRotationAlt
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Vrpano
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.player.stereo.DepthModelDialog
import com.illuminazionetech.vrclip.player.stereo.DepthModelManager
import com.illuminazionetech.vrclip.player.stereo.StereoSettings
import com.illuminazionetech.vrclip.ui.component.BackButton
import com.illuminazionetech.vrclip.ui.component.PreferenceItem
import com.illuminazionetech.vrclip.ui.component.PreferenceSubtitle
import com.illuminazionetech.vrclip.ui.component.PreferenceSwitch
import com.illuminazionetech.vrclip.util.PLAYER_CARDBOARD_DEFAULT
import com.illuminazionetech.vrclip.util.PLAYER_GYRO
import com.illuminazionetech.vrclip.util.PLAYER_QUEST_IMMERSIVE
import com.illuminazionetech.vrclip.util.PLAYER_QUEST_PASSTHROUGH_DEFAULT
import com.illuminazionetech.vrclip.util.PreferenceUtil
import com.illuminazionetech.vrclip.util.PreferenceUtil.getBoolean
import com.illuminazionetech.vrclip.util.toFileSizeText
import kotlin.math.roundToInt

/** Player behavior on phone and Meta Quest, and the 2D to 3D conversion settings. */
@Composable
fun PlayerPreferences(onNavigateBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val model = remember { DepthModelManager.get(context) }
    val modelState by model.state.collectAsStateWithLifecycle()

    var questImmersive by remember { mutableStateOf(PLAYER_QUEST_IMMERSIVE.getBoolean(true)) }
    var questPassthrough by
        remember { mutableStateOf(PLAYER_QUEST_PASSTHROUGH_DEFAULT.getBoolean(false)) }
    var cardboardDefault by remember { mutableStateOf(PLAYER_CARDBOARD_DEFAULT.getBoolean(false)) }
    var gyro by remember { mutableStateOf(PLAYER_GYRO.getBoolean(true)) }
    val initialStereo = remember { StereoSettings.load() }
    var strength by remember { mutableFloatStateOf(initialStereo.strength) }
    var popOut by remember { mutableFloatStateOf(initialStereo.popOut) }
    var showModelDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(id = R.string.player_settings_title)) },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item { PreferenceSubtitle(text = stringResource(R.string.player_settings_title)) }
            item {
                PreferenceSwitch(
                    title = stringResource(R.string.player_gyro_default),
                    description = stringResource(R.string.player_gyro_default_desc),
                    icon = Icons.Rounded.ScreenRotationAlt,
                    isChecked = gyro,
                    onClick = {
                        gyro = !gyro
                        PreferenceUtil.updateValue(PLAYER_GYRO, gyro)
                    },
                )
            }
            item {
                PreferenceSwitch(
                    title = stringResource(R.string.player_default_cardboard),
                    description = stringResource(R.string.player_default_cardboard_desc),
                    icon = Icons.Rounded.ViewInAr,
                    isChecked = cardboardDefault,
                    onClick = {
                        cardboardDefault = !cardboardDefault
                        PreferenceUtil.updateValue(PLAYER_CARDBOARD_DEFAULT, cardboardDefault)
                    },
                )
            }

            item { PreferenceSubtitle(text = "Meta Quest") }
            item {
                PreferenceSwitch(
                    title = stringResource(R.string.player_default_immersive_quest),
                    description = stringResource(R.string.player_default_immersive_quest_desc),
                    icon = Icons.Rounded.Vrpano,
                    isChecked = questImmersive,
                    onClick = {
                        questImmersive = !questImmersive
                        PreferenceUtil.updateValue(PLAYER_QUEST_IMMERSIVE, questImmersive)
                    },
                )
            }
            item {
                PreferenceSwitch(
                    title = stringResource(R.string.player_default_passthrough),
                    description = stringResource(R.string.player_default_passthrough_desc),
                    icon = Icons.Rounded.Public,
                    isChecked = questPassthrough,
                    enabled = questImmersive,
                    onClick = {
                        questPassthrough = !questPassthrough
                        PreferenceUtil.updateValue(PLAYER_QUEST_PASSTHROUGH_DEFAULT, questPassthrough)
                    },
                )
            }

            item { PreferenceSubtitle(text = stringResource(R.string.stereo_settings)) }
            item {
                SliderPreference(
                    icon = Icons.Rounded.Layers,
                    title = stringResource(R.string.stereo_strength),
                    description = stringResource(R.string.stereo_strength_desc),
                    value = strength,
                    range = StereoSettings.STRENGTH_RANGE,
                    steps = 7,
                    label = "${(strength * 1000).roundToInt() / 10f}%",
                    onValueChange = { strength = it },
                    onValueChangeFinished = { StereoSettings(strength, popOut).save() },
                )
            }
            item {
                SliderPreference(
                    icon = Icons.Rounded.OpenInFull,
                    title = stringResource(R.string.stereo_pop_out),
                    description = stringResource(R.string.stereo_pop_out_desc),
                    value = popOut,
                    range = StereoSettings.POP_OUT_RANGE,
                    steps = 5,
                    label = "${(popOut * 100).roundToInt()}%",
                    onValueChange = { popOut = it },
                    onValueChangeFinished = { StereoSettings(strength, popOut).save() },
                )
            }
            item {
                val installed = modelState is DepthModelManager.State.Installed
                PreferenceItem(
                    title =
                        if (installed)
                            stringResource(
                                R.string.stereo_model_installed,
                                DepthModelManager.MODEL_BYTES.toFileSizeText(),
                            )
                        else stringResource(R.string.stereo_model_not_installed),
                    description = "Depth Anything V2 Small · Apache-2.0",
                    icon = if (installed) Icons.Rounded.ViewInAr else Icons.Rounded.Download,
                    trailingIcon =
                        if (installed) {
                            {
                                IconButton(onClick = { model.delete() }) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = stringResource(R.string.stereo_model_delete),
                                    )
                                }
                            }
                        } else null,
                    onClick = { if (!installed) showModelDialog = true },
                )
            }
        }
    }

    if (showModelDialog) {
        DepthModelDialog(onDismiss = { showModelDialog = false }, onReady = { showModelDialog = false })
    }
}

@Composable
private fun SliderPreference(
    icon: ImageVector,
    title: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column {
        PreferenceItem(
            title = title,
            description = description,
            icon = icon,
            trailingIcon = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            steps = steps,
            modifier = Modifier.padding(start = 56.dp, end = 24.dp, bottom = 8.dp),
        )
    }
}
