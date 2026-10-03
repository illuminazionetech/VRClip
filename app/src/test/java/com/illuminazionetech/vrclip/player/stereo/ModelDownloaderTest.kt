package com.illuminazionetech.vrclip.player.stereo

import java.io.File
import java.security.MessageDigest
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Download, resume, fallback and verification of the depth model, against a local server. */
class ModelDownloaderTest {

    @get:Rule val folder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val model = Random(7).nextBytes(300_000)
    private val modelSha = sha256(model)
    private var free = Long.MAX_VALUE

    private val target
        get() = File(folder.root, "model.tflite")

    private val workDir
        get() = File(folder.root, "work")

    private fun downloader() =
        ModelDownloader(
            client = OkHttpClient(),
            workDir = workDir,
            userAgent = "test",
            freeBytes = { free },
            retryDelaysMs = listOf(0, 0),
        )

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.close()
    }

    private fun ok(bytes: ByteArray) =
        MockResponse.Builder().code(200).body(Buffer().write(bytes)).build()

    private fun status(code: Int) = MockResponse.Builder().code(code).build()

    private fun url(path: String = "/model") = server.url(path).toString()

    private fun part(index: Int = 0) = downloader().partFile(target, index)

    private fun fetch(vararg urls: String) = runBlocking {
        downloader().fetch(urls.toList(), target, model.size.toLong(), modelSha, { _, _ -> })
    }

    @Test
    fun downloadsAndVerifiesTheFile() {
        server.enqueue(ok(model))
        fetch(url())
        assertArrayEquals(model, target.readBytes())
        assertNull(server.takeRequest().headers["Range"])
        assertFalse(part().exists())
    }

    @Test
    fun resumesAPartialDownloadWithARangeRequest() {
        workDir.mkdirs()
        part().writeBytes(model.copyOfRange(0, 100_000))
        server.enqueue(
            MockResponse.Builder()
                .code(206)
                .body(Buffer().write(model.copyOfRange(100_000, model.size)))
                .build()
        )
        fetch(url())
        assertEquals("bytes=100000-", server.takeRequest().headers["Range"])
        assertArrayEquals(model, target.readBytes())
    }

    @Test
    fun startsOverWhenTheServerIgnoresTheRange() {
        workDir.mkdirs()
        part().writeBytes(ByteArray(100_000) { 1 })
        server.enqueue(ok(model))
        fetch(url())
        assertArrayEquals(model, target.readBytes())
    }

    @Test
    fun corruptedDataFallsBackToTheNextSource() {
        val wrong = model.copyOf().also { it[10] = (it[10] + 1).toByte() }
        server.enqueue(ok(wrong))
        server.enqueue(ok(model))
        fetch(url("/first"), url("/second"))
        assertArrayEquals(model, target.readBytes())
        assertFalse("corrupted data is discarded", part().exists())
    }

    @Test
    fun missingFileFallsBackWithoutRetrying() {
        server.enqueue(status(404))
        server.enqueue(ok(model))
        fetch(url("/first"), url("/second"))
        assertArrayEquals(model, target.readBytes())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun serverErrorsAreRetriedThenReportedAsNetwork() {
        repeat(3) { server.enqueue(status(503)) }
        val failure = expectFailure { fetch(url()) }
        assertEquals(ModelDownloader.Kind.Network, failure.kind)
        assertEquals(3, server.requestCount)
        assertFalse(target.exists())
    }

    @Test
    fun notEnoughSpaceIsReportedBeforeDownloading() {
        free = 1_000
        val failure = expectFailure { fetch(url()) }
        assertEquals(ModelDownloader.Kind.NoSpace, failure.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aLargerFileIsReportedAsCorruptedAndDiscarded() {
        server.enqueue(ok(model + ByteArray(10)))
        val failure = expectFailure { fetch(url()) }
        assertEquals(ModelDownloader.Kind.Corrupted, failure.kind)
        assertFalse(part().exists())
        assertFalse(target.exists())
    }

    @Test
    fun eachTargetKeepsItsOwnPartialFile() {
        val other = File(folder.root, "other.tflite")
        assertTrue(downloader().partFile(target, 0) != downloader().partFile(other, 0))
        assertTrue(downloader().partFile(target, 0) != downloader().partFile(target, 1))
    }

    private fun expectFailure(block: () -> Unit): ModelDownloader.Failure {
        try {
            block()
        } catch (e: ModelDownloader.Failure) {
            return e
        }
        fail("expected a failure")
        throw AssertionError()
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
