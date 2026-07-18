package com.illuminazionetech.vrclip.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Helpers around the storage permission the yt-dlp pipeline needs to write into the public
 * Download directory: All files access on API 30+, WRITE_EXTERNAL_STORAGE below that.
 */
object StorageUtil {

    fun isStorageAccessGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Launches the system screen where the user can grant All files access. Some devices
     * (notably Quest headsets) do not resolve the app-scoped screen, so this walks through
     * fallbacks: the app-scoped grant screen, the global All files access list, and finally the
     * app details page.
     */
    fun launchAllFilesAccessSettings(context: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        val candidates =
            listOf(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.parse("package:${context.packageName}")),
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}")),
            )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                runCatching { context.startActivity(intent) }.onSuccess {
                    return
                }
            }
        }
    }
}
