package com.illuminazionetech.vrclip.player.stereo

import android.content.Context
import android.os.storage.StorageManager
import android.util.Log
import com.illuminazionetech.vrclip.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * The depth estimation model used for 2D to 3D conversion: Depth Anything V2 Small (Apache-2.0
 * weights), as exported to TFLite by Qualcomm AI Hub (518x518 RGB in 0..1, relative inverse depth
 * out). It is about 99 MB, so it is not bundled in the APK: it is downloaded on first use and only
 * accepted if its SHA-256 matches the value pinned here.
 *
 * The download runs as a foreground [DepthModelWorker] ([start]), so it survives leaving the app;
 * it resumes where it stopped and falls back to a second source (see [ModelDownloader]).
 */
class DepthModelManager private constructor(private val context: Context) {

    sealed interface State {
        data object NotInstalled : State

        /** Waiting for the background job to start. */
        data object Queued : State

        data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : State {
            val progress: Float
                get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else -1f
        }

        data object Verifying : State

        data object Installed : State

        data class Failed(val reason: Reason) : State
    }

    enum class Reason {
        Network,
        NoSpace,
        Corrupted,
    }

    private val directory = File(context.noBackupFilesDir, "models")
    val modelFile = File(directory, MODEL_FILE_NAME)

    private val mutableState =
        MutableStateFlow<State>(if (isReady()) State.Installed else State.NotInstalled)
    val state: StateFlow<State> = mutableState.asStateFlow()

    private val mutex = Mutex()

    init {
        // Left behind by versions before 1.2.1, which downloaded without resuming.
        File(directory, "$MODEL_FILE_NAME.part").delete()
    }

    private val downloader by lazy {
        ModelDownloader(
            client =
                OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(45, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .retryOnConnectionFailure(true)
                    .build(),
            workDir = File(directory, "download"),
            userAgent = "VRClip/${BuildConfig.VERSION_NAME}",
            freeBytes = ::freeBytes,
        )
    }

    fun isReady(): Boolean = modelFile.isFile && modelFile.length() == MODEL_BYTES

    /**
     * Starts the download in the background, with a progress notification. Returns immediately;
     * follow [state]. Does nothing when the model is installed or a download is running.
     */
    fun start() {
        if (isReady()) {
            mutableState.value = State.Installed
            return
        }
        val current = mutableState.value
        if (current is State.Downloading || current is State.Verifying) return
        mutableState.value = State.Queued
        DepthModelWorker.enqueue(context)
    }

    /** Downloads, unpacks and verifies the model. Safe to call again; returns true when ready. */
    suspend fun download(): Boolean = mutex.withLock {
        if (isReady()) {
            mutableState.value = State.Installed
            return@withLock true
        }
        withContext(Dispatchers.IO) {
            try {
                mutableState.value = State.Downloading(0, SOURCES.first().downloadBytes)
                downloader.fetch(
                    sources = SOURCES,
                    target = modelFile,
                    expectedBytes = MODEL_BYTES,
                    expectedSha256 = MODEL_SHA256,
                    onProgress = { done, total ->
                        mutableState.value = State.Downloading(done, total)
                    },
                    onVerifying = { mutableState.value = State.Verifying },
                )
                mutableState.value = State.Installed
                true
            } catch (e: CancellationException) {
                mutableState.value = State.NotInstalled
                throw e
            } catch (e: ModelDownloader.Failure) {
                Log.w(TAG, "Depth model download failed (${e.kind})", e)
                mutableState.value =
                    State.Failed(
                        when (e.kind) {
                            ModelDownloader.Kind.Network -> Reason.Network
                            ModelDownloader.Kind.NoSpace -> Reason.NoSpace
                            ModelDownloader.Kind.Corrupted -> Reason.Corrupted
                        }
                    )
                false
            } catch (e: Exception) {
                Log.w(TAG, "Depth model download failed", e)
                mutableState.value = State.Failed(Reason.Network)
                false
            }
        }
    }

    /** Called when the background job could not even start (for example, it was cancelled). */
    internal fun onJobStopped() {
        val current = mutableState.value
        if (current is State.Queued || current is State.Downloading) {
            mutableState.value = if (isReady()) State.Installed else State.NotInstalled
        }
    }

    fun cancel() {
        DepthModelWorker.cancel(context)
        onJobStopped()
    }

    fun delete() {
        cancel()
        modelFile.delete()
        File(directory, "download").deleteRecursively()
        mutableState.value = State.NotInstalled
    }

    fun resetError() {
        if (mutableState.value is State.Failed) {
            mutableState.value = if (isReady()) State.Installed else State.NotInstalled
        }
    }

    private fun freeBytes(): Long {
        directory.mkdirs()
        val storage = context.getSystemService(StorageManager::class.java)
        return runCatching { storage.getAllocatableBytes(storage.getUuidForPath(directory)) }
            .getOrElse { directory.usableSpace }
    }

    companion object {
        private const val TAG = "DepthModelManager"
        private const val MODEL_FILE_NAME = "depth_anything_v2_small_518.tflite"

        /** Qualcomm AI Hub Models release 0.63.0, float TFLite export (a zip archive). */
        const val DOWNLOAD_URL =
            "https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/models/" +
                "depth_anything_v2/releases/v0.63.0/depth_anything_v2-tflite-float.zip"
        const val DOWNLOAD_BYTES = 91_864_237L

        /**
         * The same model file, published by VRClip's CI as an asset of the `depth-model` release
         * after checking it against [MODEL_SHA256]. Used when the first source cannot be reached.
         */
        const val MIRROR_URL =
            "https://github.com/illuminazionetech/VRClip/releases/download/depth-model/" +
                MODEL_FILE_NAME

        const val MODEL_BYTES = 98_920_480L
        const val MODEL_SHA256 = "b40c1b6365033c127dbba693e499bacd56f59eba53def9d4bfa4a26e8778bea7"
        const val INPUT_SIZE = 518

        private val SOURCES =
            listOf(
                ModelDownloader.ZipSource(DOWNLOAD_URL, DOWNLOAD_BYTES, entrySuffix = ".tflite"),
                ModelDownloader.FileSource(MIRROR_URL, MODEL_BYTES),
            )

        @Volatile private var instance: DepthModelManager? = null

        fun get(context: Context): DepthModelManager =
            instance
                ?: synchronized(this) {
                    instance ?: DepthModelManager(context.applicationContext).also { instance = it }
                }
    }
}
