package com.illuminazionetech.vrclip.player.stereo

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches a large file that must match a pinned size and SHA-256, from the first [Source] that
 * delivers it. Each download goes to a `.part` file and resumes with an HTTP range request after a
 * dropped connection, a retry or a restart of the app, so a 90 MB model does not start over because
 * the phone switched networks. Nothing reaches [fetch]'s target until it is verified.
 */
internal class ModelDownloader(
    private val client: OkHttpClient,
    private val workDir: File,
    private val userAgent: String,
    private val freeBytes: () -> Long,
    private val retryDelaysMs: List<Long> = listOf(2_000, 6_000),
) {

    sealed interface Source {
        val url: String
        val downloadBytes: Long
    }

    /** A zip archive holding the file as an entry whose name ends with [entrySuffix]. */
    data class ZipSource(
        override val url: String,
        override val downloadBytes: Long,
        val entrySuffix: String,
    ) : Source

    /** The file itself. */
    data class FileSource(override val url: String, override val downloadBytes: Long) : Source

    enum class Kind {
        /** No source could be reached or completed (offline, timeouts, server errors). */
        Network,
        /** A source delivered data that did not match the pinned size and hash. */
        Corrupted,
        /** There is not enough free space for the download and the unpacked file. */
        NoSpace,
    }

    class Failure(val kind: Kind, message: String, cause: Throwable? = null) :
        IOException(message, cause)

    /** The source does not have the file (404, 403): try the next one without retrying. */
    private class Unavailable(message: String) : IOException(message)

    /**
     * Downloads and verifies the file into [target]. [onProgress] receives bytes downloaded and the
     * total for the current source; [onVerifying] is called before the hash check.
     */
    suspend fun fetch(
        sources: List<Source>,
        target: File,
        expectedBytes: Long,
        expectedSha256: String,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        onVerifying: () -> Unit = {},
    ): File {
        workDir.mkdirs()
        var failure: Failure? = null
        for ((index, source) in sources.withIndex()) {
            val part = File(workDir, "source$index.part")
            val needed =
                (source.downloadBytes - part.length().coerceAtMost(source.downloadBytes)) +
                    (if (source is ZipSource) expectedBytes else 0L) +
                    SPACE_MARGIN_BYTES
            if (freeBytes() < needed) {
                failure = moreUseful(failure, Failure(Kind.NoSpace, "need $needed bytes"))
                continue
            }
            try {
                downloadWithRetries(source, part, onProgress)
                onVerifying()
                when (source) {
                    is FileSource -> verifyInto(part, target, expectedBytes, expectedSha256)
                    is ZipSource ->
                        extractInto(part, source.entrySuffix, target, expectedBytes, expectedSha256)
                }
                part.delete()
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
                // Reading the archive or writing the model failed after a complete download: most
                // likely the storage filled up meanwhile, otherwise the archive cannot be read.
                val kind = if (freeBytes() < expectedBytes) Kind.NoSpace else Kind.Corrupted
                if (kind == Kind.Corrupted) part.delete()
                failure = moreUseful(failure, Failure(kind, "cannot install the file", e))
            }
        }
        throw failure ?: Failure(Kind.Network, "no source")
    }

    /** Reports the failure the user can act on: space first, then bad data, then the network. */
    private fun moreUseful(current: Failure?, next: Failure): Failure =
        if (current != null && current.kind.ordinal >= next.kind.ordinal) current else next

    private suspend fun downloadWithRetries(
        source: Source,
        part: File,
        onProgress: (Long, Long) -> Unit,
    ) {
        var attempt = 0
        while (true) {
            try {
                download(source, part, onProgress)
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
                    throw Failure(Kind.Network, "download of ${source.url} failed", e)
                }
                delay(retryDelaysMs[attempt++])
            }
        }
    }

    private suspend fun download(source: Source, part: File, onProgress: (Long, Long) -> Unit) {
        val total = source.downloadBytes
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
                .url(source.url)
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
                410 -> throw Unavailable("HTTP ${response.code} for ${source.url}")
                else -> throw IOException("HTTP ${response.code} for ${source.url}")
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
                            throw Failure(Kind.Corrupted, "${source.url} is larger than expected")
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
        moveInto(part, target)
    }

    private fun extractInto(archive: File, suffix: String, target: File, bytes: Long, sha: String) {
        val unpacked = File(workDir, "${target.name}.unpacked")
        try {
            ZipFile(archive).use { zip ->
                val entry =
                    zip.entries().asSequence().firstOrNull {
                        !it.isDirectory && it.name.endsWith(suffix)
                    } ?: throw Failure(Kind.Corrupted, "no $suffix in the archive")
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                zip.getInputStream(entry).use { input ->
                    unpacked.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            written += read
                        }
                    }
                }
                if (written != bytes || digest.digest().toHex() != sha) {
                    throw Failure(Kind.Corrupted, "unpacked file does not match")
                }
            }
        } catch (e: IOException) {
            unpacked.delete()
            throw if (e is ZipException) Failure(Kind.Corrupted, "archive is damaged", e) else e
        }
        moveInto(unpacked, target)
    }

    private fun moveInto(source: File, target: File) {
        target.parentFile?.mkdirs()
        target.delete()
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
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
            return digest.digest().toHex()
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
