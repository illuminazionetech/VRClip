package com.illuminazionetech.vrclip.player.stereo

import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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

    private fun file(path: String = "/model") =
        ModelDownloader.FileSource(server.url(path).toString(), model.size.toLong())

    private fun fetch(vararg sources: ModelDownloader.Source) = runBlocking {
        downloader().fetch(sources.toList(), target, model.size.toLong(), modelSha, { _, _ -> })
    }

    @Test
    fun downloadsAndVerifiesTheFile() {
        server.enqueue(ok(model))
        fetch(file())
        assertArrayEquals(model, target.readBytes())
        assertNull(server.takeRequest().headers["Range"])
        assertFalse(File(workDir, "source0.part").exists())
    }

    @Test
    fun resumesAPartialDownloadWithARangeRequest() {
        workDir.mkdirs()
        File(workDir, "source0.part").writeBytes(model.copyOfRange(0, 100_000))
        server.enqueue(
            MockResponse.Builder()
                .code(206)
                .body(Buffer().write(model.copyOfRange(100_000, model.size)))
                .build()
        )
        fetch(file())
        assertEquals("bytes=100000-", server.takeRequest().headers["Range"])
        assertArrayEquals(model, target.readBytes())
    }

    @Test
    fun startsOverWhenTheServerIgnoresTheRange() {
        workDir.mkdirs()
        File(workDir, "source0.part").writeBytes(ByteArray(100_000) { 1 })
        server.enqueue(ok(model))
        fetch(file())
        assertArrayEquals(model, target.readBytes())
    }

    @Test
    fun unpacksTheModelFromAZipArchive() {
        val zip = zipOf("depth/metadata.json" to "{}".toByteArray(), "depth/model.tflite" to model)
        server.enqueue(ok(zip))
        fetch(
            ModelDownloader.ZipSource(server.url("/a.zip").toString(), zip.size.toLong(), ".tflite")
        )
        assertArrayEquals(model, target.readBytes())
        assertEquals(listOf<String>(), workDir.list()!!.toList())
    }

    @Test
    fun corruptedDataFallsBackToTheNextSource() {
        val wrong = model.copyOf().also { it[10] = (it[10] + 1).toByte() }
        server.enqueue(ok(wrong))
        server.enqueue(ok(model))
        fetch(file("/first"), file("/second"))
        assertArrayEquals(model, target.readBytes())
        assertFalse("corrupted data is discarded", File(workDir, "source0.part").exists())
    }

    @Test
    fun missingFileFallsBackWithoutRetrying() {
        server.enqueue(status(404))
        server.enqueue(ok(model))
        fetch(file("/first"), file("/second"))
        assertArrayEquals(model, target.readBytes())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun serverErrorsAreRetriedThenReportedAsNetwork() {
        repeat(3) { server.enqueue(status(503)) }
        val failure = expectFailure { fetch(file()) }
        assertEquals(ModelDownloader.Kind.Network, failure.kind)
        assertEquals(3, server.requestCount)
        assertFalse(target.exists())
    }

    @Test
    fun notEnoughSpaceIsReportedBeforeDownloading() {
        free = 1_000
        val failure = expectFailure { fetch(file()) }
        assertEquals(ModelDownloader.Kind.NoSpace, failure.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aDamagedArchiveIsReportedAsCorrupted() {
        val junk = Random(3).nextBytes(5_000)
        server.enqueue(ok(junk))
        val failure = expectFailure {
            fetch(ModelDownloader.ZipSource(server.url("/a.zip").toString(), 5_000, ".tflite"))
        }
        assertEquals(ModelDownloader.Kind.Corrupted, failure.kind)
        assertTrue(workDir.list().orEmpty().isEmpty())
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

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
