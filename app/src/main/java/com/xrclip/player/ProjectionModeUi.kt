package com.xrclip.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.xrclip.R

@Composable
fun ProjectionMode.displayName(): String =
    stringResource(
        when (this) {
            ProjectionMode.FLAT -> R.string.player_projection_flat
            ProjectionMode.MONO_360 -> R.string.player_projection_mono_360
            ProjectionMode.STEREO_360_TB -> R.string.player_projection_stereo_360_tb
            ProjectionMode.STEREO_360_LR -> R.string.player_projection_stereo_360_lr
            ProjectionMode.MONO_180 -> R.string.player_projection_mono_180
            ProjectionMode.STEREO_180_TB -> R.string.player_projection_stereo_180_tb
            ProjectionMode.STEREO_180_LR -> R.string.player_projection_stereo_180_lr
            ProjectionMode.SBS_3D -> R.string.player_projection_sbs_3d
            ProjectionMode.OU_3D -> R.string.player_projection_ou_3d
        }
    )
