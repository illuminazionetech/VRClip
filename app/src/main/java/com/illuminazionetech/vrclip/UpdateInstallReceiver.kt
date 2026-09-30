package com.illuminazionetech.vrclip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.illuminazionetech.vrclip.util.AppUpdateManager

/**
 * Receives the result of the self-update install session started by [AppUpdateManager]. When the
 * system needs the user to confirm the install it hands back an activity intent to launch.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation =
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
            if (confirmation != null) {
                runCatching {
                        context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    .onFailure {
                        Log.w(TAG, "Cannot show the install confirmation", it)
                        AppUpdateManager.onInstallResult(PackageInstaller.STATUS_FAILURE_BLOCKED)
                    }
            }
            return
        }
        Log.d(TAG, "Install finished with status $status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        AppUpdateManager.onInstallResult(status)
    }

    companion object {
        private const val TAG = "UpdateInstallReceiver"
        const val ACTION_INSTALL_STATUS = "com.illuminazionetech.vrclip.action.UPDATE_INSTALL_STATUS"
    }
}
