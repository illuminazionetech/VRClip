package com.illuminazionetech.vrclip.player.stereo

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Mp4StereoTagTest {

    @get:Rule val folder = TemporaryFolder()

    private val videoChunk = ByteArray(40) { (it * 7).toByte() }
    private val audioChunk = ByteArray(24) { (100 + it).toByte() }

    // region Building test files

    private fun box(type: String, vararg parts: ByteArray): ByteArray {
        val body = parts.fold(ByteArray(0)) { acc, part -> acc + part }
        return ByteBuffer.allocate(8 + body.size)
            .putInt(8 + body.size)
            .put(type.toByteArray(Charsets.ISO_8859_1))
            .put(body)
            .array()
    }

    private fun ints(vararg values: Int): ByteArray =
        ByteBuffer.allocate(values.size * 4).apply { values.forEach(::putInt) }.array()

    private fun fullBox(type: String, vararg parts: ByteArray) = box(type, ints(0), *parts)

    private fun hdlr(handler: String) =
        fullBox("hdlr", ints(0), handler.toByteArray(Charsets.ISO_8859_1), ByteArray(13))

    private fun stco(vararg offsets: Int) = fullBox("stco", ints(offsets.size, *offsets))

    private fun avc1(extra: ByteArray = ByteArray(0)): ByteArray {
        // Visual sample entry fields: 78 bytes, with width 1920 and height 1080.
        val fields = ByteBuffer.allocate(78)
        fields.position(24)
        fields.putShort(1920.toShort()).putShort(1080.toShort())
        return box("avc1", fields.array(), box("avcC", ByteArray(12) { 1 }), extra)
    }

    private fun trak(handler: String, entry: ByteArray, chunkOffset: Int) =
        box(
            "trak",
            box("tkhd", ByteArray(84)),
            box(
                "mdia",
                box("mdhd", ByteArray(24)),
                hdlr(handler),
                box(
                    "minf",
                    box("vmhd", ByteArray(12)),
                    box(
                        "stbl",
                        fullBox("stsd", ints(1), entry),
                        box("stts", ByteArray(8)),
                        stco(chunkOffset),
                    ),
                ),
            ),
        )

    private fun moov(videoOffset: Int, audioOffset: Int, video: Boolean = true, st3d: Int? = null) =
        box(
            "moov",
            box("mvhd", ByteArray(100)),
            *listOfNotNull(
                    if (video)
                        trak(
                            "vide",
                            avc1(
                                st3d?.let { fullBox("st3d", byteArrayOf(it.toByte())) }
                                    ?: ByteArray(0)
                            ),
                            videoOffset,
                        )
                    else null,
                    trak("soun", box("mp4a", ByteArray(28)), audioOffset),
                )
                .toTypedArray(),
        )

    private val ftyp = box("ftyp", "isom".toByteArray(), ints(0), "isom".toByteArray())

    private fun mdat() = box("mdat", videoChunk, audioChunk)

    private fun write(vararg boxes: ByteArray): File =
        folder.newFile().apply {
            writeBytes(ByteArrayOutputStream().apply { boxes.forEach(::write) }.toByteArray())
        }

    /** moov first and mdat second: the chunk offsets for that layout. */
    private fun moovFirst(freeSize: Int = 0, video: Boolean = true, st3d: Int? = null): File {
        // Offsets depend on the moov size, which does not depend on the offset values.
        val size = moov(0, 0, video, st3d).size
        val mdatAt = ftyp.size + size + freeSize
        val moov = moov(mdatAt + 8, mdatAt + 8 + videoChunk.size, video, st3d)
        val free = if (freeSize > 0) box("free", ByteArray(freeSize - 8)) else ByteArray(0)
        return write(ftyp, moov, free, mdat())
    }

    private fun moovLast(): File {
        val mdatAt = ftyp.size
        return write(ftyp, mdat(), moov(mdatAt + 8, mdatAt + 8 + videoChunk.size))
    }

    // endregion

    // region Reading test files

    private class Parsed(val type: String, val offset: Int, val size: Int)

    private fun boxesIn(data: ByteArray, from: Int, to: Int): List<Parsed> {
        val list = mutableListOf<Parsed>()
        var at = from
        while (at < to) {
            val size = ByteBuffer.wrap(data, at, 4).int
            assertTrue("box at $at has size $size", size >= 8 && at + size <= to)
            list += Parsed(String(data, at + 4, 4, Charsets.ISO_8859_1), at, size)
            at += size
        }
        assertEquals("boxes fill their parent exactly", to, at)
        return list
    }

    private fun child(data: ByteArray, parent: Parsed, type: String, skip: Int = 8): Parsed =
        boxesIn(data, parent.offset + skip, parent.offset + parent.size).single { it.type == type }

    private fun tracks(data: ByteArray): List<Parsed> {
        val moov = boxesIn(data, 0, data.size).single { it.type == "moov" }
        return boxesIn(data, moov.offset + 8, moov.offset + moov.size).filter { it.type == "trak" }
    }

    private fun stbl(data: ByteArray, trak: Parsed): Parsed {
        val mdia = child(data, trak, "mdia")
        return child(data, child(data, mdia, "minf"), "stbl")
    }

    private fun chunkOffset(data: ByteArray, trak: Parsed): Int {
        val stco = child(data, stbl(data, trak), "stco")
        return ByteBuffer.wrap(data, stco.offset + 16, 4).int
    }

    /** The stereo mode written in the video track, or null without an st3d box. */
    private fun stereoMode(data: ByteArray): Int? {
        val video = tracks(data).first()
        val stsd = child(data, stbl(data, video), "stsd")
        val entry = child(data, stsd, "avc1", skip = 16)
        val st3d =
            boxesIn(data, entry.offset + 8 + 78, entry.offset + entry.size).firstOrNull {
                it.type == "st3d"
            } ?: return null
        assertEquals(13, st3d.size)
        return data[st3d.offset + 12].toInt()
    }

    /** The media data each track's chunk offset points at is still the right data. */
    private fun assertChunksIntact(data: ByteArray) {
        val (video, audio) = tracks(data)
        val v = chunkOffset(data, video)
        val a = chunkOffset(data, audio)
        assertArrayEquals(videoChunk, data.copyOfRange(v, v + videoChunk.size))
        assertArrayEquals(audioChunk, data.copyOfRange(a, a + audioChunk.size))
    }

    // endregion

    @Test
    fun moovAtTheEndGrowsInPlace() {
        val file = moovLast()
        val before = file.length()
        assertTrue(Mp4StereoTag.write(file))
        val data = file.readBytes()
        assertEquals(before + 13, data.size.toLong())
        assertEquals(Mp4StereoTag.LEFT_RIGHT, stereoMode(data))
        assertChunksIntact(data)
    }

    @Test
    fun moovFirstUsesTheFreeSpaceAfterIt() {
        val file = moovFirst(freeSize = 64)
        val before = file.length()
        assertTrue(Mp4StereoTag.write(file))
        val data = file.readBytes()
        assertEquals(before, data.size.toLong())
        assertEquals(Mp4StereoTag.LEFT_RIGHT, stereoMode(data))
        val free = boxesIn(data, 0, data.size).single { it.type == "free" }
        assertEquals(64 - 13, free.size)
        assertChunksIntact(data)
    }

    @Test
    fun freeSpaceOfExactlyTheBoxSizeDisappears() {
        val file = moovFirst(freeSize = 13)
        assertTrue(Mp4StereoTag.write(file))
        val data = file.readBytes()
        assertTrue(boxesIn(data, 0, data.size).none { it.type == "free" })
        assertEquals(Mp4StereoTag.LEFT_RIGHT, stereoMode(data))
        assertChunksIntact(data)
    }

    @Test
    fun moovFirstWithoutRoomMovesTheMediaData() {
        val file = moovFirst()
        val before = file.length()
        assertTrue(Mp4StereoTag.write(file))
        val data = file.readBytes()
        assertEquals(before + 13, data.size.toLong())
        assertEquals(Mp4StereoTag.LEFT_RIGHT, stereoMode(data))
        assertChunksIntact(data)
    }

    @Test
    fun anExistingBoxIsUpdated() {
        val file = moovFirst(st3d = Mp4StereoTag.MONO)
        val before = file.length()
        assertTrue(Mp4StereoTag.write(file))
        val data = file.readBytes()
        assertEquals(before, data.size.toLong())
        assertEquals(Mp4StereoTag.LEFT_RIGHT, stereoMode(data))
        assertChunksIntact(data)
    }

    @Test
    fun filesWithoutVideoAreLeftAlone() {
        val file = moovFirst(video = false)
        val original = file.readBytes()
        assertFalse(Mp4StereoTag.write(file))
        assertArrayEquals(original, file.readBytes())
    }

    @Test
    fun notAnMp4IsLeftAlone() {
        val file = folder.newFile().apply { writeBytes(ByteArray(100) { 7 }) }
        val original = file.readBytes()
        assertFalse(Mp4StereoTag.write(file))
        assertArrayEquals(original, file.readBytes())
        assertNull(file.parentFile?.listFiles { f -> f.name.endsWith(".st3d.tmp") }?.firstOrNull())
    }
}
