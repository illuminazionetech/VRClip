package com.illuminazionetech.vrclip.ui.page

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.util.PreferenceUtil
import com.illuminazionetech.vrclip.util.UpdateUtil
import com.illuminazionetech.vrclip.util.makeToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AppUpdater"

@Composable
fun AppUpdater() {

    val context = LocalContext.current

    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var currentDownloadStatus by remember {
        mutableStateOf(UpdateUtil.DownloadStatus.NotYet as UpdateUtil.DownloadStatus)
    }
    val scope = rememberCoroutineScope()
    var updateJob: Job? = null
    var release by remember { mutableStateOf(UpdateUtil.Release()) }
    val settings =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            UpdateUtil.installLatestApk()
        }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            if (result) {
                UpdateUtil.installLatestApk()
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!context.packageManager.canRequestPackageInstalls())
                        settings.launch(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    else UpdateUtil.installLatestApk()
                }
            }
        }

    LaunchedEffect(Unit) {
        // The update check is a tiny API call, so it is not gated on metered networks; the
        // download itself only starts after the user confirms it in the dialog.
        if (!PreferenceUtil.isAutoUpdateEnabled()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                    UpdateUtil.checkForUpdate()?.let {
                        release = it
                        showUpdateDialog = true
                    }
                }
                .onFailure { Log.w(TAG, "Update check failed", it) }
        }
    }

    if (showUpdateDialog) {
        UpdateDialogImpl(
            onDismissRequest = {
                showUpdateDialog = false
                updateJob?.cancel()
            },
            title = release.name.toString(),
            onConfirmUpdate = {
                updateJob =
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                                UpdateUtil.downloadApk(release = release).collect { downloadStatus
                                    ->
                                    currentDownloadStatus = downloadStatus
                                    if (downloadStatus is UpdateUtil.DownloadStatus.Finished) {
                                        launcher.launch(
                                            Manifest.permission.REQUEST_INSTALL_PACKAGES
                                        )
                                    }
                                }
                            }
                            .onFailure {
                                it.printStackTrace()
                                currentDownloadStatus = UpdateUtil.DownloadStatus.NotYet
                                makeToast(R.string.app_update_failed)
                                return@launch
                            }
                    }
            },
            releaseNote = release.body.toString(),
            downloadStatus = currentDownloadStatus,
        )
    }
}
