package com.illuminazionetech.vrclip.util

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log

/**
 * What this device can decode, so the high-resolution download preset asks yt-dlp for the largest
 * video it can actually play instead of a fixed 8K AV1 that many phones and the Quest 2 cannot
 * decode.
 */
object DecoderCapabilities {

    /** A codec in yt-dlp's preference order (best first) and the largest size it decodes. */
    data class Decoder(val codec: Codec, val maxResolution: Int)

    /** yt-dlp `vcodec` sort names, best first, as yt-dlp ranks them. */
    enum class Codec(val ytDlpName: String, val mime: String) {
        // MediaFormat.MIMETYPE_VIDEO_AV1 only exists from API 29; the string is the same.
        Av1("av01", "video/av01"),
        Vp9Hdr("vp9.2", MediaFormat.MIMETYPE_VIDEO_VP9),
        Vp9("vp9", MediaFormat.MIMETYPE_VIDEO_VP9),
        Hevc("h265", MediaFormat.MIMETYPE_VIDEO_HEVC),
        Avc("h264", MediaFormat.MIMETYPE_VIDEO_AVC),
    }

    /**
     * Frame sizes tried, as (width, height). yt-dlp's `res` is the smaller side, which is what
     * these are grouped by: 8K 16:9 and 2:1 equirectangular, 6K and 5.7K 360, 4K, 1440p, 1080p.
     */
    private val sizes =
        listOf(
            7680 to 4320,
            7680 to 3840,
            5760 to 2880,
            3840 to 2160,
            2560 to 1440,
            1920 to 1080,
        )

    private val decoders: List<Decoder> by lazy {
        runCatching { query() }
            .onFailure { Log.w("DecoderCapabilities", "Cannot list decoders", it) }
            .getOrDefault(emptyList())
    }

    /** The format sort for the high-resolution preset on this device. */
    fun highResolutionSort(): String = highResolutionSort(decoders)

    /**
     * Picks the codec that reaches the highest resolution (the better codec on a tie) and limits
     * the resolution to what it decodes. `vcodec:X` in yt-dlp prefers X and anything it ranks below
     * X, so codecs this device lacks are pushed back rather than excluded.
     */
    fun highResolutionSort(available: List<Decoder>): String {
        val best =
            available.maxWithOrNull(
                compareBy<Decoder> { it.maxResolution }.thenByDescending { it.codec.ordinal }
            ) ?: return "res:1080,vcodec:h264"
        return "res:${best.maxResolution},vcodec:${best.codec.ytDlpName}"
    }

    private fun query(): List<Decoder> {
        val infos =
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        return Codec.entries.mapNotNull { codec ->
            val candidates = infos.filter { info ->
                info.supportedTypes.any { it.equals(codec.mime, ignoreCase = true) } &&
                    isHardware(info) &&
                    (codec != Codec.Vp9Hdr || supportsVp9Profile2(info))
            }
            val max =
                candidates.maxOfOrNull { info ->
                    val video =
                        info.getCapabilitiesForType(codec.mime).videoCapabilities
                            ?: return@maxOfOrNull 0
                    sizes
                        .filter { (w, h) ->
                            video.isSizeSupported(w, h) || video.isSizeSupported(h, w)
                        }
                        .maxOfOrNull { (w, h) -> minOf(w, h) } ?: 0
                } ?: 0
            if (max > 0) Decoder(codec, max) else null
        }
    }

    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.isHardwareAccelerated
        else !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")

    private fun supportsVp9Profile2(info: MediaCodecInfo): Boolean =
        info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_VP9).profileLevels.any {
            it.profile == MediaCodecInfo.CodecProfileLevel.VP9Profile2 ||
                it.profile == MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR
        }
}
