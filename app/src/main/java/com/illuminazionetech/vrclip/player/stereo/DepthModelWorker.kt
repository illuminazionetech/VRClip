package com.illuminazionetech.vrclip.player.stereo

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.util.NotificationUtil
import com.illuminazionetech.vrclip.util.toFileSizeText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Downloads the depth model as a foreground job, so it keeps going when the user leaves the app or
 * the system reclaims its memory, and shows its progress in a notification.
 */
class DepthModelWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val manager = DepthModelManager.get(applicationContext)

    override suspend fun doWork(): Result {
        runCatching { setForeground(foregroundInfo(progress(-1f, ""))) }
            .onFailure { Log.w(TAG, "Cannot run in the foreground", it) }
        return try {
            val ok = coroutineScope {
                val updates = launch {
                    var lastPercent = -2
                    manager.state.collectLatest { state ->
                        val notification =
                            when (state) {
                                is DepthModelManager.State.Downloading -> {
                                    val percent = (state.progress * 100).toInt()
                                    if (percent == lastPercent) return@collectLatest
                                    lastPercent = percent
                                    progress(
                                        state.progress,
                                        "${state.downloadedBytes.toFileSizeText(applicationContext)} / " +
                                            state.totalBytes.toFileSizeText(applicationContext),
                                    )
                                }
                                DepthModelManager.State.Verifying ->
                                    progress(
                                        -1f,
                                        applicationContext.getString(
                                            R.string.stereo_model_checking
                                        ),
                                    )
                                else -> return@collectLatest
                            }
                        NotificationUtil.post(NOTIFICATION_ID, notification)
                    }
                }
                manager.download().also { updates.cancel() }
            }
            NotificationUtil.cancel(NOTIFICATION_ID)
            if (ok) Result.success() else Result.failure()
        } catch (e: CancellationException) {
            NotificationUtil.cancel(NOTIFICATION_ID)
            manager.onJobStopped()
            throw e
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(progress(-1f, ""))

    private fun foregroundInfo(notification: android.app.Notification) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }

    private fun progress(progress: Float, text: String) =
        NotificationCompat.Builder(applicationContext, NotificationUtil.STEREO_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vrclip)
            .setContentTitle(applicationContext.getString(R.string.stereo_model_downloading))
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, (progress.coerceAtLeast(0f) * 100).toInt(), progress < 0f)
            .addAction(
                0,
                applicationContext.getString(R.string.cancel),
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
            )
            .build()

    companion object {
        private const val TAG = "DepthModelWorker"
        private const val WORK_NAME = "depth-model-download"
        private const val NOTIFICATION_ID = 39_999

        fun enqueue(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<DepthModelWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
