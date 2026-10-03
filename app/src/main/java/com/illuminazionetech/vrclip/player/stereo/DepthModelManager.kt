package com.illuminazionetech.vrclip.player.stereo

import android.annotation.SuppressLint
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
 * The depth estimation models used for 2D to 3D conversion (see [DepthModel]). Together they are
 * about 100 MB, so they are not bundled in the APK: they are downloaded on first use from VRClip's
 * `depth-model` release and only accepted if their SHA-256 matches the values pinned in the app.
 *
 * The download runs as a foreground [DepthModelWorker] ([start]), so it survives leaving the app,
 * and resumes where it stopped (see [ModelDownloader]).
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
    private val workDir = File(directory, "download")

    fun fileOf(model: DepthModel) = File(directory, model.fileName)

    private val mutableState =
        MutableStateFlow<State>(if (isReady()) State.Installed else State.NotInstalled)
    val state: StateFlow<State> = mutableState.asStateFlow()

    private val mutex = Mutex()

    init {
        removeObsoleteFiles()
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
            workDir = workDir,
            userAgent = "VRClip/${BuildConfig.VERSION_NAME}",
            freeBytes = ::freeBytes,
        )
    }

    /** True when every model is on the device. */
    fun isReady(): Boolean = DepthModel.entries.all(::isReady)

    fun isReady(model: DepthModel): Boolean =
        fileOf(model).let { it.isFile && it.length() == model.bytes }

    /**
     * Starts the download in the background, with a progress notification. Returns immediately;
     * follow [state]. Does nothing when the models are installed or a download is running.
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

    /** Downloads and verifies the missing models. Safe to call again; returns true when ready. */
    suspend fun download(): Boolean = mutex.withLock {
        if (isReady()) {
            mutableState.value = State.Installed
            return@withLock true
        }
        withContext(Dispatchers.IO) {
            try {
                val total = DepthModel.totalBytes
                var done = DepthModel.entries.filter(::isReady).sumOf { it.bytes }
                mutableState.value = State.Downloading(done, total)
                for (model in DepthModel.entries) {
                    if (isReady(model)) continue
                    downloader.fetch(
                        urls = listOf(model.url),
                        target = fileOf(model),
                        expectedBytes = model.bytes,
                        expectedSha256 = model.sha256,
                        onProgress = { downloaded, _ ->
                            mutableState.value = State.Downloading(done + downloaded, total)
                        },
                        onVerifying = { mutableState.value = State.Verifying },
                    )
                    done += model.bytes
                }
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
        DepthModel.entries.forEach { fileOf(it).delete() }
        workDir.deleteRecursively()
        LiveModelPolicy.reset()
        mutableState.value = State.NotInstalled
    }

    fun resetError() {
        if (mutableState.value is State.Failed) {
            mutableState.value = if (isReady()) State.Installed else State.NotInstalled
        }
    }

    /**
     * Files of earlier versions: VRClip 1.3 used a single Qualcomm AI Hub export, and partial
     * downloads were named after their source rather than their target.
     */
    private fun removeObsoleteFiles() {
        val current = DepthModel.entries.map { it.fileName }.toSet()
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".tflite") && file.name !in current) file.delete()
            if (file.isFile && file.name.endsWith(".part")) file.delete()
        }
        workDir.listFiles()?.forEach { file ->
            if (current.none { file.name.startsWith("$it.") }) file.delete()
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

        // Holds the application context only (see get), which lives as long as the process.
        @SuppressLint("StaticFieldLeak") @Volatile private var instance: DepthModelManager? = null

        fun get(context: Context): DepthModelManager =
            instance
                ?: synchronized(this) {
                    instance ?: DepthModelManager(context.applicationContext).also { instance = it }
                }
    }
}
