package com.xrclip.player

import android.content.Context
import android.content.Intent
import com.xrclip.player.quest.ImmersivePlayerActivity
import com.xrclip.util.isQuestDevice

/**
 * Single entry point for "play this downloaded video": routes to the Meta Spatial SDK immersive
 * player on Quest, or to the in-app [PlayerScreen] (via [onNavigateToPlayer]) everywhere else.
 */
object PlayerLauncher {
    fun launch(
        context: Context,
        videoId: Int,
        videoPath: String,
        projectionOverride: String? = null,
        onNavigateToPlayer: (Int) -> Unit,
    ) {
        if (isQuestDevice()) {
            context.startActivity(
                Intent(context, ImmersivePlayerActivity::class.java)
                    .putExtra(ImmersivePlayerActivity.EXTRA_VIDEO_ID, videoId)
                    .putExtra(ImmersivePlayerActivity.EXTRA_VIDEO_PATH, videoPath)
                    .putExtra(ImmersivePlayerActivity.EXTRA_PROJECTION, projectionOverride)
            )
        } else {
            onNavigateToPlayer(videoId)
        }
    }
}
