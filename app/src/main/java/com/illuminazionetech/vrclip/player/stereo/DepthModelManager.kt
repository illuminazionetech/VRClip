package com.illuminazionetech.vrclip.player.stereo

import android.content.Context
import android.util.Log
import com.illuminazionetech.vrclip.BuildConfig
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The depth estimation model used for 2D to 3D conversion: Depth Anything V2 Small (Apache-2.0
 * weights), as exported to TFLite by Qualcomm AI Hub (518x518 RGB in 0..1, relative inverse depth
 * out). It is about 95 MB, so it is not bundled in the APK: it is downloaded on first use from a
 * versioned URL and only accepted if its SHA-256 matches the value pinned here.
 */
class DepthModelManager private constructor(context: Context) {

    sealed interface State {
        data object NotInstalled : State

        data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : State {
            val progress: Float
                get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else -1f
        }

        data object Installed : State

        data class Failed(val corrupted: Boolean) : State
    }

    private val directory = File(context.noBackupFilesDir, "models")
    val modelFile = File(directory, MODEL_FILE_NAME)

    private val mutableState =
        MutableStateFlow<State>(if (modelFile.isFile) State.Installed else State.NotInstalled)
    val state: StateFlow<State> = mutableState.asStateFlow()

    private val mutex = Mutex()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun isReady(): Boolean = modelFile.isFile && modelFile.length() == MODEL_BYTES

    /** Downloads, unpacks and verifies the model. Safe to call again; returns true when ready. */
    suspend fun download(): Boolean = mutex.withLock {
        if (isReady()) {
            mutableState.value = State.Installed
            return@withLock true
        }
        withContext(Dispatchers.IO) {
            try {
                fetch()
                mutableState.value = State.Installed
                true
            } catch (e: CancellationException) {
                mutableState.value = State.NotInstalled
                throw e
            } catch (e: CorruptedModelException) {
                Log.w(TAG, "Depth model failed verification", e)
                mutableState.value = State.Failed(corrupted = true)
                false
            } catch (e: Exception) {
                Log.w(TAG, "Depth model download failed", e)
                mutableState.value = State.Failed(corrupted = false)
                false
            }
        }
    }

    fun delete() {
        modelFile.delete()
        mutableState.value = State.NotInstalled
    }

    fun resetError() {
        if (mutableState.value is State.Failed) {
            mutableState.value = if (isReady()) State.Installed else State.NotInstalled
        }
    }

    private suspend fun fetch() {
        directory.mkdirs()
        val partial = File(directory, "$MODEL_FILE_NAME.part")
        partial.delete()
        val request =
            Request.Builder()
                .url(DOWNLOAD_URL)
                .header("User-Agent", "VRClip/${BuildConfig.VERSION_NAME}")
                .build()
        mutableState.value = State.Downloading(0, DOWNLOAD_BYTES)
        val digest = MessageDigest.getInstance("SHA-256")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: DOWNLOAD_BYTES
            val counting = CountingInputStream(body.byteStream())
            ZipInputStream(counting).use { zip ->
                var found = false
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || !entry.name.endsWith(".tflite")) continue
                    found = true
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var lastReported = 0L
                        while (currentCoroutineContext().isActive) {
                            val read = zip.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            if (counting.count - lastReported >= 512 * 1024) {
                                lastReported = counting.count
                                mutableState.value = State.Downloading(counting.count, total)
                            }
                        }
                    }
                    break
                }
                if (!found) throw CorruptedModelException("no model in the archive")
            }
        }
        currentCoroutineContext().ensureActiveOrDelete(partial)
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (hash != MODEL_SHA256 || partial.length() != MODEL_BYTES) {
            partial.delete()
            throw CorruptedModelException("hash $hash, size ${partial.length()}")
        }
        modelFile.delete()
        if (!partial.renameTo(modelFile)) throw IOException("cannot move the model into place")
    }

    private fun kotlin.coroutines.CoroutineContext.ensureActiveOrDelete(file: File) {
        if (!isActive) {
            file.delete()
            throw CancellationException("download cancelled")
        }
    }

    private class CorruptedModelException(message: String) : IOException(message)

    private class CountingInputStream(private val source: java.io.InputStream) :
        java.io.FilterInputStream(source) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) count += it }

        override fun skip(n: Long): Long = super.skip(n).also { count += it }
    }

    companion object {
        private const val TAG = "DepthModelManager"
        private const val MODEL_FILE_NAME = "depth_anything_v2_small_518.tflite"

        /** Qualcomm AI Hub Models release 0.63.0, float TFLite export. */
        const val DOWNLOAD_URL =
            "https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/models/" +
                "depth_anything_v2/releases/v0.63.0/depth_anything_v2-tflite-float.zip"
        const val DOWNLOAD_BYTES = 91_864_237L
        const val MODEL_BYTES = 98_920_480L
        const val MODEL_SHA256 = "b40c1b6365033c127dbba693e499bacd56f59eba53def9d4bfa4a26e8778bea7"
        const val INPUT_SIZE = 518

        @Volatile private var instance: DepthModelManager? = null

        fun get(context: Context): DepthModelManager =
            instance
                ?: synchronized(this) {
                    instance ?: DepthModelManager(context.applicationContext).also { instance = it }
                }
    }
}
