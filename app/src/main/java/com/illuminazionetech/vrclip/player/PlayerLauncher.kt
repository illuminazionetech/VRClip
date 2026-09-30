package com.illuminazionetech.vrclip.player

import android.content.Context
import android.content.Intent
import androidx.core.app.ActivityOptionsCompat
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.player.quest.ImmersivePlayerActivity
import com.illuminazionetech.vrclip.util.PLAYER_QUEST_IMMERSIVE
import com.illuminazionetech.vrclip.util.PreferenceUtil.getBoolean
import com.illuminazionetech.vrclip.util.isQuestDevice

/**
 * Single entry point for "play this downloaded video": the Meta Spatial SDK immersive player on
 * Quest (unless the user turned off the "immersive player" setting), [PlayerActivity] everywhere
 * else.
 */
object PlayerLauncher {
    fun launch(
        context: Context,
        videoId: Int,
        videoPath: String,
        title: String? = null,
        projectionOverride: String? = null,
    ) {
        if (isQuestDevice() && PLAYER_QUEST_IMMERSIVE.getBoolean(true)) {
            // Horizon OS starts an immersive activity in its own task, as Meta's hybrid sample does.
            context.startActivity(
                Intent(context, ImmersivePlayerActivity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(ImmersivePlayerActivity.EXTRA_VIDEO_ID, videoId)
                    .putExtra(ImmersivePlayerActivity.EXTRA_VIDEO_PATH, videoPath)
                    .putExtra(ImmersivePlayerActivity.EXTRA_TITLE, title)
                    .putExtra(ImmersivePlayerActivity.EXTRA_PROJECTION, projectionOverride)
            )
        } else {
            val options =
                ActivityOptionsCompat.makeCustomAnimation(context, R.anim.player_enter, R.anim.hold)
            context.startActivity(PlayerActivity.intent(context, videoId), options.toBundle())
        }
    }
}
