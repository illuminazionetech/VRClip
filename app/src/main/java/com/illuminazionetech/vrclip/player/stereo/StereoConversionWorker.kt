package com.illuminazionetech.vrclip.player.stereo

import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.storage.StorageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.player.PlayerActivity
import com.illuminazionetech.vrclip.player.ProjectionMode
import com.illuminazionetech.vrclip.util.DatabaseUtil
import com.illuminazionetech.vrclip.util.NotificationUtil
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Permanent 2D to 3D conversion of a library video, run by WorkManager as a foreground job so it
 * survives leaving the app. The new file is written next to the original under a hidden name and
 * only replaces it once the export completed and the result checks out (a video track of the
 * planned size and the full duration); until then the original stays untouched, and any failure
 * or cancellation just deletes the partial file. An MP4 source is replaced in place; other
 * containers become an .mp4 with the same name, since the output is always MP4.
 */
class StereoConversionWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val videoId = inputData.getInt(KEY_VIDEO_ID, -1)
    private val notificationId = NOTIFICATION_BASE + videoId.coerceAtLeast(0)
    private var title = ""
    private var lastReported = -1

    override suspend fun doWork(): Result {
        val info = runCatching { DatabaseUtil.getInfoById(videoId) }.getOrNull()
        if (info == null) return failure(Failure.Missing)
        title = info.videoTitle
        val input = File(info.videoPath)
        if (info.videoPath.startsWith("content://") || !input.isFile || !input.canRead()) {
            return failure(Failure.Missing)
        }
        val directory = input.parentFile
        if (directory == null || !directory.canWrite()) return failure(Failure.NotWritable)

        runCatching { setForeground(foregroundInfo(progress = -1f)) }
            .onFailure { Log.w(TAG, "Cannot run in the foreground", it) }

        // One conversion at a time: each one keeps the GPU and the encoder busy on its own.
        return conversionLock.withLock { convert(input, directory) }
    }

    private suspend fun convert(input: File, directory: File): Result {
        val model = DepthModelManager.get(applicationContext)
        if (!model.isReady() && !model.download()) return failure(Failure.Model)

        val temp = File(directory, ".${input.nameWithoutExtension}.vrclip-3d.part.mp4")
        try {
            val source = StereoConverter.probe(input)
            val plan = StereoConverter.plan(source)
            val needed = plan.estimatedBytes(source.durationUs) + SPACE_MARGIN_BYTES
            if (freeBytes(directory) < needed) return failure(Failure.NoSpace)

            StereoConverter.convert(
                context = applicationContext,
                input = input,
                output = temp,
                plan = plan,
                settings = StereoSettings.load(),
                onProgress = ::reportProgress,
            )

            val result = StereoConverter.probe(temp)
            val complete =
                result.hasVideo &&
                    (source.durationUs <= 0 || result.durationUs >= source.durationUs * 0.97)
            if (!complete) {
                temp.delete()
                return failure(Failure.Failed)
            }

            val target =
                if (input.extension.equals("mp4", ignoreCase = true)) input
                else uniqueSibling(directory, input.nameWithoutExtension, "mp4")
            // The video may have been deleted from the library while it was being converted.
            val stillInLibrary = runCatching { DatabaseUtil.getInfoById(videoId) }.getOrNull() != null
            if (!stillInLibrary || !input.exists()) {
                temp.delete()
                return failure(Failure.Missing)
            }
            val replaced =
                withContext(NonCancellable) {
                    if (!temp.renameTo(target)) return@withContext false
                    if (target != input) input.delete()
                    DatabaseUtil.replaceVideoFile(videoId, target.absolutePath, ProjectionMode.SBS_3D.name)
                    true
                }
            if (!replaced) {
                temp.delete()
                return failure(Failure.NotWritable)
            }
            MediaScannerConnection.scanFile(
                applicationContext,
                arrayOf(target.absolutePath, input.absolutePath).distinct().toTypedArray(),
                null,
                null,
            )
            notifyDone(success = true, message = applicationContext.getString(R.string.stereo_convert_done))
            return Result.success(workDataOf(KEY_OUTPUT to target.absolutePath))
        } catch (e: CancellationException) {
            temp.delete()
            NotificationUtil.cancel(notificationId)
            throw e
        } catch (e: ConversionException) {
            Log.e(TAG, "Conversion failed", e)
            temp.delete()
            return failure(
                when (e.reason) {
                    ConversionException.Reason.NoVideo,
                    ConversionException.Reason.Unreadable -> Failure.Missing
                    ConversionException.Reason.EncoderUnavailable -> Failure.Encoder
                    ConversionException.Reason.Failed -> Failure.Failed
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Conversion failed", e)
            temp.delete()
            return failure(Failure.Failed)
        }
    }

    /** Space the system can give us, counting cached data it would clear to make room. */
    private fun freeBytes(directory: File): Long {
        val storage = applicationContext.getSystemService(StorageManager::class.java)
        return runCatching { storage.getAllocatableBytes(storage.getUuidForPath(directory)) }
            .getOrElse { directory.usableSpace }
    }

    private fun reportProgress(progress: Float) {
        val percent = (progress * 100).toInt()
        if (percent == lastReported) return
        lastReported = percent
        setProgressAsync(workDataOf(KEY_PROGRESS to progress))
        NotificationUtil.post(notificationId, buildProgressNotification(progress))
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(-1f)

    private fun foregroundInfo(progress: Float): ForegroundInfo {
        val notification = buildProgressNotification(progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun buildProgressNotification(progress: Float) =
        NotificationCompat.Builder(applicationContext, NotificationUtil.STEREO_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vrclip)
            .setContentTitle(applicationContext.getString(R.string.stereo_converting))
            .setContentText(title)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, (progress.coerceAtLeast(0f) * 100).toInt(), progress < 0f)
            .addAction(
                0,
                applicationContext.getString(R.string.cancel),
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
            )
            .build()

    private fun notifyDone(success: Boolean, message: String) {
        val builder =
            NotificationCompat.Builder(applicationContext, NotificationUtil.STEREO_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_vrclip)
                .setContentTitle(message)
                .setContentText(title)
                .setAutoCancel(true)
        if (success) {
            builder.setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    notificationId,
                    PlayerActivity.intent(applicationContext, videoId),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
        }
        NotificationUtil.cancel(notificationId)
        NotificationUtil.post(notificationId + 1, builder.build())
    }

    private fun failure(reason: Failure): Result {
        val message =
            applicationContext.getString(
                when (reason) {
                    Failure.Missing -> R.string.stereo_error_missing
                    Failure.NotWritable -> R.string.stereo_error_not_writable
                    Failure.NoSpace -> R.string.stereo_error_space
                    Failure.Model -> R.string.stereo_model_failed
                    Failure.Encoder -> R.string.stereo_error_encoder
                    Failure.Failed -> R.string.stereo_error_failed
                }
            )
        notifyDone(success = false, message = message)
        return Result.failure(workDataOf(KEY_FAILURE to reason.name))
    }

    enum class Failure {
        Missing,
        NotWritable,
        NoSpace,
        Model,
        Encoder,
        Failed,
    }

    /** Conversion state of one video, for the UI. */
    sealed interface Status {
        data object None : Status

        data class Running(val progress: Float) : Status

        data object Done : Status

        data class Failed(val reason: Failure) : Status
    }

    companion object {
        private const val TAG = "StereoConversion"
        private const val KEY_VIDEO_ID = "video_id"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_OUTPUT = "output"
        private const val KEY_FAILURE = "failure"
        private const val NOTIFICATION_BASE = 40_000
        private const val SPACE_MARGIN_BYTES = 200L * 1024 * 1024

        private fun workName(videoId: Int) = "stereo-convert-$videoId"

        fun start(context: Context, videoId: Int) {
            val request =
                OneTimeWorkRequestBuilder<StereoConversionWorker>()
                    .setInputData(workDataOf(KEY_VIDEO_ID to videoId))
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag(TAG_CONVERSION)
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(workName(videoId), ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context, videoId: Int) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(videoId))
        }

        fun status(context: Context, videoId: Int): Flow<Status> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(workName(videoId)).map {
                infos ->
                val info = infos.lastOrNull() ?: return@map Status.None
                when (info.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED -> Status.Running(-1f)
                    WorkInfo.State.RUNNING ->
                        Status.Running(info.progress.getFloat(KEY_PROGRESS, -1f))
                    WorkInfo.State.SUCCEEDED -> Status.Done
                    WorkInfo.State.FAILED ->
                        Status.Failed(
                            runCatching {
                                    Failure.valueOf(info.outputData.getString(KEY_FAILURE).orEmpty())
                                }
                                .getOrDefault(Failure.Failed)
                        )
                    WorkInfo.State.CANCELLED -> Status.None
                }
            }

        const val TAG_CONVERSION = "stereo-conversion"

        private val conversionLock = Mutex()

        private fun uniqueSibling(directory: File, base: String, extension: String): File {
            var candidate = File(directory, "$base.$extension")
            var index = 1
            while (candidate.exists()) {
                candidate = File(directory, "$base ($index).$extension")
                index++
            }
            return candidate
        }
    }
}
