package com.illuminazionetech.vrclip.player.stereo

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Marks an MP4 file as stereoscopic 3D the standard way: a Spherical Video V2 `st3d` box in the
 * video track's sample entry. Players read it without relying on the file name; Media3 surfaces it
 * as `Format.stereoMode`, which is how VRClip recognises a 2D to 3D conversion opened from anywhere
 * (sent to a Meta Quest, copied to another folder). The Media3 muxer does not write the box, so it
 * is added to the finished file.
 *
 * Only the `moov` box changes, and it grows by the 13 bytes of the new box. When `moov` is the last
 * box, or is followed by free space, the file is patched in place; otherwise it is rewritten with
 * every chunk offset after `moov` moved by those 13 bytes.
 */
internal object Mp4StereoTag {

    /** `st3d` stereo_mode values. */
    const val MONO = 0
    const val TOP_BOTTOM = 1
    const val LEFT_RIGHT = 2

    private const val ST3D_SIZE = 13
    private const val VISUAL_SAMPLE_ENTRY_HEADER = 78
    private val CONTAINERS = setOf("moov", "trak", "mdia", "minf", "stbl")
    private val VIDEO_ENTRIES = setOf("avc1", "avc3", "hvc1", "hev1", "av01", "vp09", "mp4v")

    private class Box(val type: String, val offset: Long, val headerSize: Int, val size: Long) {
        val end: Long
            get() = offset + size

        val bodyOffset: Long
            get() = offset + headerSize
    }

    /**
     * Writes [stereoMode] into [file]. Returns false, leaving the file as it was, when the file is
     * not a plain MP4 with a video track (fragmented files, unknown codecs, damaged boxes).
     */
    fun write(file: File, stereoMode: Int = LEFT_RIGHT): Boolean {
        val layout = RandomAccessFile(file, "r").use(::topLevelBoxes) ?: return false
        val moov = layout.singleOrNull { it.type == "moov" } ?: return false
        if (layout.any { it.type == "moof" } || moov.size > Int.MAX_VALUE) return false
        val original =
            RandomAccessFile(file, "r").use { raf ->
                ByteArray(moov.size.toInt()).also {
                    raf.seek(moov.offset)
                    raf.readFully(it)
                }
            }
        val tagged = tag(original, stereoMode) ?: return false
        if (tagged.size == original.size) {
            patch(file, moov.offset, tagged, original)
            return true
        }
        val growth = tagged.size - original.size
        val after = layout.filter { it.offset >= moov.end }
        val free = after.firstOrNull()?.takeIf { it.type == "free" || it.type == "skip" }
        return when {
            // Nothing but free space after moov: grow into it, the media data does not move.
            after.all { it.type == "free" || it.type == "skip" } -> {
                RandomAccessFile(file, "rw").use { raf ->
                    patchOpen(raf, moov.offset, tagged, original)
                    raf.setLength(moov.offset + tagged.size)
                }
                true
            }
            free != null && (free.size == growth.toLong() || free.size >= growth + 8L) -> {
                val rest = free.size - growth
                val patched =
                    if (rest == 0L) tagged else tagged + freeBox(rest.toInt().coerceAtLeast(8))
                patch(file, moov.offset, patched, original + freeHeaderOf(file, free))
                true
            }
            else -> rewriteWithShift(file, moov, original, stereoMode)
        }
    }

    /** The top-level boxes, or null when they do not tile the file exactly. */
    private fun topLevelBoxes(raf: RandomAccessFile): List<Box>? {
        val boxes = mutableListOf<Box>()
        val length = raf.length()
        var offset = 0L
        val header = ByteArray(16)
        while (offset < length) {
            if (length - offset < 8) return null
            raf.seek(offset)
            raf.readFully(header, 0, 8)
            var size = readUInt32(header, 0)
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            var headerSize = 8
            when (size) {
                1L -> {
                    if (length - offset < 16) return null
                    raf.readFully(header, 8, 8)
                    size = ByteBuffer.wrap(header, 8, 8).long
                    headerSize = 16
                }
                0L -> size = length - offset
            }
            if (size < headerSize || offset + size > length) return null
            boxes += Box(type, offset, headerSize, size)
            offset += size
        }
        return boxes
    }

    /**
     * The `moov` box with the `st3d` box set in the first video track, or null when there is no
     * video track. Chunk offsets are left alone here.
     */
    private fun tag(moov: ByteArray, stereoMode: Int): ByteArray? {
        val root = parse(moov, 0) ?: return null
        val path = videoSampleEntryPath(moov, root) ?: return null
        val entry = path.last()
        val existing =
            children(moov, entry, entry.headerSize + VISUAL_SAMPLE_ENTRY_HEADER).firstOrNull {
                it.type == "st3d"
            }
        if (existing != null) {
            if (existing.size < ST3D_SIZE) return null
            return moov.copyOf().also { it[(existing.offset + 12).toInt()] = stereoMode.toByte() }
        }
        val insertAt = entry.end.toInt()
        val result = ByteArray(moov.size + ST3D_SIZE)
        System.arraycopy(moov, 0, result, 0, insertAt)
        st3d(stereoMode).copyInto(result, insertAt)
        System.arraycopy(moov, insertAt, result, insertAt + ST3D_SIZE, moov.size - insertAt)
        // Every box on the way down to the sample entry grows by the new box.
        for (box in path) {
            if (!grow(result, box, ST3D_SIZE)) return null
        }
        return result
    }

    private fun parse(data: ByteArray, at: Int): Box? {
        if (at + 8 > data.size) return null
        var size = readUInt32(data, at)
        val type = String(data, at + 4, 4, Charsets.ISO_8859_1)
        var headerSize = 8
        if (size == 1L) {
            if (at + 16 > data.size) return null
            size = ByteBuffer.wrap(data, at + 8, 8).long
            headerSize = 16
        } else if (size == 0L) {
            size = (data.size - at).toLong()
        }
        if (size < headerSize || at + size > data.size) return null
        return Box(type, at.toLong(), headerSize, size)
    }

    private fun children(data: ByteArray, parent: Box, skip: Int): List<Box> {
        val list = mutableListOf<Box>()
        var at = (parent.offset + skip).toInt()
        val end = parent.end.toInt()
        while (at + 8 <= end) {
            val child = parse(data, at) ?: break
            if (child.end > end) break
            list += child
            at = child.end.toInt()
        }
        return list
    }

    /** moov, trak, mdia, minf, stbl, stsd and the sample entry of the first video track. */
    private fun videoSampleEntryPath(data: ByteArray, moov: Box): List<Box>? {
        if (moov.type != "moov") return null
        for (trak in children(data, moov, moov.headerSize).filter { it.type == "trak" }) {
            val mdia = children(data, trak, trak.headerSize).firstOrNull { it.type == "mdia" }
            val hdlr =
                mdia?.let { children(data, it, it.headerSize) }?.firstOrNull { it.type == "hdlr" }
            // hdlr: version and flags, pre_defined, then the handler type.
            val handlerAt = hdlr?.let { (it.bodyOffset + 8).toInt() } ?: continue
            if (handlerAt + 4 > data.size) continue
            if (String(data, handlerAt, 4, Charsets.ISO_8859_1) != "vide") continue
            val minf = children(data, mdia, mdia.headerSize).firstOrNull { it.type == "minf" }
            val stbl =
                minf?.let { children(data, it, it.headerSize) }?.firstOrNull { it.type == "stbl" }
            val stsd =
                stbl?.let { children(data, it, it.headerSize) }?.firstOrNull { it.type == "stsd" }
                    ?: continue
            // stsd: version and flags, entry_count, then the sample entries.
            val entry = children(data, stsd, stsd.headerSize + 8).firstOrNull() ?: continue
            if (entry.type !in VIDEO_ENTRIES) return null
            if (entry.size < entry.headerSize + VISUAL_SAMPLE_ENTRY_HEADER) return null
            return listOf(moov, trak, mdia, minf, stbl, stsd, entry)
        }
        return null
    }

    private fun grow(data: ByteArray, box: Box, by: Int): Boolean {
        val at = box.offset.toInt()
        val size = readUInt32(data, at)
        return when {
            size == 1L -> {
                val buffer = ByteBuffer.wrap(data)
                buffer.putLong(at + 8, buffer.getLong(at + 8) + by)
                true
            }
            size == 0L -> true
            size + by > 0xFFFF_FFFFL -> false
            else -> {
                writeUInt32(data, at, size + by)
                true
            }
        }
    }

    /** Rewrites the file with the bigger moov and every chunk offset past it moved. */
    private fun rewriteWithShift(
        file: File,
        moov: Box,
        original: ByteArray,
        stereoMode: Int,
    ): Boolean {
        val shifted = shiftChunkOffsets(original, moov.end, ST3D_SIZE) ?: return false
        val tagged = tag(shifted, stereoMode) ?: return false
        val temp = File(file.parentFile, ".${file.name}.st3d.tmp")
        try {
            RandomAccessFile(file, "r").use { input ->
                temp.outputStream().buffered(1 shl 20).use { output ->
                    copyRange(input, output, 0, moov.offset)
                    output.write(tagged)
                    copyRange(input, output, moov.end, input.length() - moov.end)
                }
            }
            if (StereoConversionWorker.moveIntoPlace(temp, file, file)) return true
            temp.delete()
            return false
        } catch (e: IOException) {
            temp.delete()
            return false
        }
    }

    /**
     * moov with every `stco`/`co64` entry at or after [from] moved by [by]; null when a 32-bit
     * offset would overflow.
     */
    private fun shiftChunkOffsets(moov: ByteArray, from: Long, by: Int): ByteArray? {
        val result = moov.copyOf()
        val root = parse(result, 0) ?: return null
        fun visit(box: Box): Boolean {
            when (box.type) {
                in CONTAINERS -> {
                    for (child in children(result, box, box.headerSize)) {
                        if (!visit(child)) return false
                    }
                }
                "stco" -> {
                    val countAt = (box.bodyOffset + 4).toInt()
                    val count = readUInt32(result, countAt)
                    for (i in 0 until count) {
                        val at = countAt + 4 + (i * 4).toInt()
                        if (at + 4 > box.end) return false
                        val offset = readUInt32(result, at)
                        if (offset >= from) {
                            if (offset + by > 0xFFFF_FFFFL) return false
                            writeUInt32(result, at, offset + by)
                        }
                    }
                }
                "co64" -> {
                    val countAt = (box.bodyOffset + 4).toInt()
                    val count = readUInt32(result, countAt)
                    val buffer = ByteBuffer.wrap(result)
                    for (i in 0 until count) {
                        val at = countAt + 4 + (i * 8).toInt()
                        if (at + 8 > box.end) return false
                        val offset = buffer.getLong(at)
                        if (offset >= from) buffer.putLong(at, offset + by)
                    }
                }
            }
            return true
        }
        return if (visit(root)) result else null
    }

    private fun patch(file: File, at: Long, bytes: ByteArray, original: ByteArray) {
        RandomAccessFile(file, "rw").use { patchOpen(it, at, bytes, original) }
    }

    /** Writes [bytes] at [at]; if that fails halfway, puts the [original] bytes back. */
    private fun patchOpen(raf: RandomAccessFile, at: Long, bytes: ByteArray, original: ByteArray) {
        try {
            raf.seek(at)
            raf.write(bytes)
        } catch (e: IOException) {
            runCatching {
                raf.seek(at)
                raf.write(original)
            }
            throw e
        }
    }

    private fun freeHeaderOf(file: File, free: Box): ByteArray =
        RandomAccessFile(file, "r").use { raf ->
            ByteArray(minOf(free.size, 16L).toInt()).also {
                raf.seek(free.offset)
                raf.readFully(it)
            }
        }

    private fun freeBox(size: Int): ByteArray =
        ByteArray(size).also {
            writeUInt32(it, 0, size.toLong())
            "free".toByteArray(Charsets.ISO_8859_1).copyInto(it, 4)
        }

    private fun st3d(stereoMode: Int): ByteArray =
        ByteArray(ST3D_SIZE).also {
            writeUInt32(it, 0, ST3D_SIZE.toLong())
            "st3d".toByteArray(Charsets.ISO_8859_1).copyInto(it, 4)
            // Version 0, no flags, then the stereo mode.
            it[12] = stereoMode.toByte()
        }

    private fun copyRange(
        input: RandomAccessFile,
        output: java.io.OutputStream,
        from: Long,
        count: Long,
    ) {
        val buffer = ByteArray(1 shl 20)
        input.seek(from)
        var left = count
        while (left > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (read < 0) throw IOException("Unexpected end of file")
            output.write(buffer, 0, read)
            left -= read
        }
    }

    private fun readUInt32(data: ByteArray, at: Int): Long =
        ((data[at].toLong() and 0xFF) shl 24) or
            ((data[at + 1].toLong() and 0xFF) shl 16) or
            ((data[at + 2].toLong() and 0xFF) shl 8) or
            (data[at + 3].toLong() and 0xFF)

    private fun writeUInt32(data: ByteArray, at: Int, value: Long) {
        data[at] = (value ushr 24).toByte()
        data[at + 1] = (value ushr 16).toByte()
        data[at + 2] = (value ushr 8).toByte()
        data[at + 3] = value.toByte()
    }
}
