package com.xrclip.ui.page.settings.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Vrpano
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.xrclip.R
import com.xrclip.ui.component.BackButton
import com.xrclip.ui.component.PreferenceSwitch
import com.xrclip.util.PLAYER_CARDBOARD_DEFAULT
import com.xrclip.util.PLAYER_QUEST_IMMERSIVE
import com.xrclip.util.PLAYER_QUEST_PASSTHROUGH_DEFAULT
import com.xrclip.util.PreferenceUtil
import com.xrclip.util.PreferenceUtil.getBoolean

/** Default behavior for the immersive/3D/360 player, on both phone and Meta Quest. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPreferences(onNavigateBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var questImmersive by remember { mutableStateOf(PLAYER_QUEST_IMMERSIVE.getBoolean(true)) }
    var questPassthrough by
        remember { mutableStateOf(PLAYER_QUEST_PASSTHROUGH_DEFAULT.getBoolean(false)) }
    var cardboardDefault by remember { mutableStateOf(PLAYER_CARDBOARD_DEFAULT.getBoolean(false)) }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(id = R.string.player_settings_title)) },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
        content = {
            LazyColumn(modifier = Modifier, contentPadding = it) {
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
                            PreferenceUtil.updateValue(
                                PLAYER_QUEST_PASSTHROUGH_DEFAULT,
                                questPassthrough,
                            )
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
            }
        },
    )
}
