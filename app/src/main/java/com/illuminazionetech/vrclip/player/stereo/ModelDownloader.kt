package com.illuminazionetech.vrclip.player.stereo

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches a large file that must match a pinned size and SHA-256, from the first of its URLs that
 * delivers it. Each download goes to a `.part` file and resumes with an HTTP range request after a
 * dropped connection, a retry or a restart of the app, so a 50 MB model does not start over because
 * the phone switched networks. Nothing reaches [fetch]'s target until it is verified.
 */
internal class ModelDownloader(
    private val client: OkHttpClient,
    private val workDir: File,
    private val userAgent: String,
    private val freeBytes: () -> Long,
    private val retryDelaysMs: List<Long> = listOf(2_000, 6_000),
) {

    enum class Kind {
        /** No source could be reached or completed (offline, timeouts, server errors). */
        Network,
        /** A source delivered data that did not match the pinned size and hash. */
        Corrupted,
        /** There is not enough free space for the download. */
        NoSpace,
    }

    class Failure(val kind: Kind, message: String, cause: Throwable? = null) :
        IOException(message, cause)

    /** The source does not have the file (404, 403): try the next one without retrying. */
    private class Unavailable(message: String) : IOException(message)

    /**
     * Downloads and verifies the file into [target], trying [urls] in order. [onProgress] receives
     * the bytes downloaded so far and [expectedBytes]; [onVerifying] is called before the hash
     * check.
     */
    suspend fun fetch(
        urls: List<String>,
        target: File,
        expectedBytes: Long,
        expectedSha256: String,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        onVerifying: () -> Unit = {},
    ): File {
        workDir.mkdirs()
        var failure: Failure? = null
        for ((index, url) in urls.withIndex()) {
            val part = partFile(target, index)
            val needed =
                expectedBytes - part.length().coerceAtMost(expectedBytes) + SPACE_MARGIN_BYTES
            if (freeBytes() < needed) {
                failure = moreUseful(failure, Failure(Kind.NoSpace, "need $needed bytes"))
                continue
            }
            try {
                downloadWithRetries(url, expectedBytes, part, onProgress)
                onVerifying()
                verifyInto(part, target, expectedBytes, expectedSha256)
                return target
            } catch (e: CancellationException) {
                throw e
            } catch (e: Unavailable) {
                failure = moreUseful(failure, Failure(Kind.Network, e.message.orEmpty(), e))
            } catch (e: Failure) {
                // Corrupted data is discarded so the next attempt does not resume from it.
                if (e.kind == Kind.Corrupted) part.delete()
                failure = moreUseful(failure, e)
            } catch (e: IOException) {
                // Reading the download or writing the model failed after a complete transfer:
                // most likely the storage filled up meanwhile.
                val kind = if (freeBytes() < expectedBytes) Kind.NoSpace else Kind.Corrupted
                if (kind == Kind.Corrupted) part.delete()
                failure = moreUseful(failure, Failure(kind, "cannot install the file", e))
            }
        }
        throw failure ?: Failure(Kind.Network, "no source")
    }

    /** Partial download of [target] from its URL number [index]. */
    fun partFile(target: File, index: Int) = File(workDir, "${target.name}.$index.part")

    /** Reports the failure the user can act on: space first, then bad data, then the network. */
    private fun moreUseful(current: Failure?, next: Failure): Failure =
        if (current != null && current.kind.ordinal >= next.kind.ordinal) current else next

    private suspend fun downloadWithRetries(
        url: String,
        total: Long,
        part: File,
        onProgress: (Long, Long) -> Unit,
    ) {
        var attempt = 0
        while (true) {
            try {
                download(url, total, part, onProgress)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Unavailable) {
                throw e
            } catch (e: Failure) {
                throw e
            } catch (e: IOException) {
                if (freeBytes() < SPACE_MARGIN_BYTES) {
                    throw Failure(Kind.NoSpace, "storage full while downloading", e)
                }
                if (attempt >= retryDelaysMs.size) {
                    throw Failure(Kind.Network, "download of $url failed", e)
                }
                delay(retryDelaysMs[attempt++])
            }
        }
    }

    private suspend fun download(
        url: String,
        total: Long,
        part: File,
        onProgress: (Long, Long) -> Unit,
    ) {
        var existing = if (part.isFile) part.length() else 0L
        if (existing > total) {
            part.delete()
            existing = 0L
        }
        if (existing == total) {
            onProgress(total, total)
            return
        }
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                // A partial file is only useful if the bytes come back unchanged.
                .header("Accept-Encoding", "identity")
                .apply { if (existing > 0) header("Range", "bytes=$existing-") }
                .build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                206 -> Unit
                200 -> existing = 0L // The server ignored the range: start over.
                416 -> {
                    // Nothing left to send for that range; the file is either complete or stale.
                    part.delete()
                    throw IOException("range not satisfiable")
                }
                403,
                404,
                410 -> throw Unavailable("HTTP ${response.code} for $url")
                else -> throw IOException("HTTP ${response.code} for $url")
            }
            RandomAccessFile(part, "rw").use { output ->
                output.setLength(existing)
                output.seek(existing)
                val buffer = ByteArray(BUFFER_BYTES)
                var written = existing
                var reported = -1L
                response.body.byteStream().use { input ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        if (written + read > total) {
                            throw Failure(Kind.Corrupted, "$url is larger than expected")
                        }
                        output.write(buffer, 0, read)
                        written += read
                        if (written - reported >= PROGRESS_STEP_BYTES || written == total) {
                            reported = written
                            onProgress(written, total)
                        }
                    }
                }
                if (written != total) throw IOException("connection closed at $written of $total")
            }
        }
    }

    private fun verifyInto(part: File, target: File, bytes: Long, sha256: String) {
        if (part.length() != bytes || sha256(part) != sha256) {
            throw Failure(Kind.Corrupted, "downloaded file does not match")
        }
        target.parentFile?.mkdirs()
        target.delete()
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
    }

    companion object {
        private const val BUFFER_BYTES = 128 * 1024
        private const val PROGRESS_STEP_BYTES = 256 * 1024L
        private const val SPACE_MARGIN_BYTES = 16L * 1024 * 1024

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
