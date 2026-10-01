@file:OptIn(UnstableApi::class)

package com.illuminazionetech.vrclip.player.stereo

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** What a source video looks like, read before converting it. */
data class SourceInfo(
    val width: Int,
    val height: Int,
    val durationUs: Long,
    val frameRate: Float,
    val bitrate: Int,
    val isHdr: Boolean,
    val hasVideo: Boolean,
)

/** How the converted file will be encoded. */
data class ConversionPlan(
    val packing: StereoPacking,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val bitrate: Int,
) {
    /** Rough output size, used to check free space before starting. */
    fun estimatedBytes(durationUs: Long, audioBitrate: Int = 256_000): Long =
        ((bitrate.toLong() + audioBitrate) * (durationUs / 1_000_000.0) / 8).toLong()
}

class ConversionException(val reason: Reason, cause: Throwable? = null) :
    Exception(reason.name, cause) {
    enum class Reason {
        NoVideo,
        Unreadable,
        EncoderUnavailable,
        Failed,
    }
}

/**
 * Converts a flat video into a full-quality side-by-side 3D MP4 with Media3 Transformer and
 * [StereoConversionEffect] (depth estimated for every frame, with temporal smoothing). The output
 * keeps each eye at the source resolution when the device encoder allows it, falls back to half
 * side-by-side where that keeps more detail (4K sources), prefers HEVC for its better quality per
 * bit, and tone-maps HDR sources to SDR since the depth model and the view synthesis work on SDR
 * frames.
 */
object StereoConverter {

    fun probe(file: File): SourceInfo {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    format = candidate
                    break
                }
            }
            format ?: return SourceInfo(0, 0, 0, 0f, 0, isHdr = false, hasVideo = false)
            val rotation = format.intOrNull(MediaFormat.KEY_ROTATION) ?: 0
            val rawWidth = format.intOrNull(MediaFormat.KEY_WIDTH) ?: 0
            val rawHeight = format.intOrNull(MediaFormat.KEY_HEIGHT) ?: 0
            val upright = rotation % 180 == 0
            val transfer = format.intOrNull(MediaFormat.KEY_COLOR_TRANSFER)
            return SourceInfo(
                width = if (upright) rawWidth else rawHeight,
                height = if (upright) rawHeight else rawWidth,
                durationUs = format.longOrNull(MediaFormat.KEY_DURATION) ?: 0L,
                frameRate =
                    format.intOrNull(MediaFormat.KEY_FRAME_RATE)?.toFloat()
                        ?: runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrNull()
                        ?: 30f,
                bitrate = format.intOrNull(MediaFormat.KEY_BIT_RATE) ?: 0,
                isHdr =
                    transfer == MediaFormat.COLOR_TRANSFER_ST2084 ||
                        transfer == MediaFormat.COLOR_TRANSFER_HLG,
                hasVideo = rawWidth > 0 && rawHeight > 0,
            )
        } catch (e: Exception) {
            throw ConversionException(ConversionException.Reason.Unreadable, e)
        } finally {
            extractor.release()
        }
    }

    /**
     * Picks packing, size and codec. Full side-by-side keeps every source pixel for each eye but
     * doubles the width; when the encoder cannot take that, both options are scaled to fit and the
     * one with more pixels per eye wins. Frames narrower than 1.25:1 always use half packing so
     * players can tell the packing apart from the frame shape (see
     * [com.illuminazionetech.vrclip.player.ProjectionMode.eyeAspectRatio]).
     */
    fun plan(source: SourceInfo): ConversionPlan {
        if (!source.hasVideo) throw ConversionException(ConversionException.Reason.NoVideo)
        val candidates =
            listOf(MimeTypes.VIDEO_H265, MimeTypes.VIDEO_H264).mapNotNull { mime ->
                encoderCapabilities(mime)?.let { mime to it }
            }
        if (candidates.isEmpty()) {
            throw ConversionException(ConversionException.Reason.EncoderUnavailable)
        }
        val allowFull = source.width.toFloat() / source.height >= 1.25f
        var best: ConversionPlan? = null
        var bestEyePixels = 0L
        for ((mime, caps) in candidates) {
            for (packing in StereoPacking.entries) {
                if (packing == StereoPacking.Full && !allowFull) continue
                val wanted =
                    if (packing == StereoPacking.Full) source.width * 2 to source.height
                    else source.width to source.height
                val (w, h) = fit(caps, wanted.first, wanted.second) ?: continue
                // Either way each eye gets half of the encoded width.
                val eyePixels = w / 2L * h
                // Prefer HEVC and full packing unless another option is clearly sharper: they
                // come first in the loops.
                if (best == null || eyePixels > bestEyePixels * 11 / 10) {
                    best =
                        ConversionPlan(
                            packing = packing,
                            width = w,
                            height = h,
                            mimeType = mime,
                            bitrate = bitrateFor(mime, caps, w, h, source),
                        )
                    bestEyePixels = eyePixels
                }
            }
        }
        return best ?: throw ConversionException(ConversionException.Reason.EncoderUnavailable)
    }

    /**
     * Runs the conversion. [onProgress] receives 0..1 on the main thread. Cancelling the calling
     * coroutine cancels the export; the partial [output] is deleted either way on failure.
     */
    suspend fun convert(
        context: Context,
        input: File,
        output: File,
        plan: ConversionPlan,
        settings: StereoSettings,
        onProgress: (Float) -> Unit,
    ) {
        withContext(Dispatchers.Main) {
            val model = DepthModelManager.get(context)
            val effect =
                StereoConversionEffect(
                    model = model,
                    settings = settings,
                    realtime = false,
                    packing = plan.packing,
                    targetSize = plan.width to plan.height,
                )
            val edited =
                EditedMediaItem.Builder(MediaItem.fromUri(android.net.Uri.fromFile(input)))
                    .setEffects(
                        Effects(
                            /* audioProcessors= */ emptyList(),
                            /* videoEffects= */ listOf(effect),
                        )
                    )
                    .build()
            val composition =
                Composition.Builder(EditedMediaItemSequence.withAudioAndVideoFrom(listOf(edited)))
                    .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                    .build()
            val encoderFactory =
                DefaultEncoderFactory.Builder(context.applicationContext)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder().setBitrate(plan.bitrate).build()
                    )
                    .setEnableFallback(true)
                    .build()

            output.delete()
            val handler = Handler(Looper.getMainLooper())
            suspendCancellableCoroutine { continuation ->
                lateinit var transformer: Transformer
                val progressHolder = ProgressHolder()
                val poll =
                    object : Runnable {
                        override fun run() {
                            if (
                                transformer.getProgress(progressHolder) ==
                                    Transformer.PROGRESS_STATE_AVAILABLE
                            ) {
                                onProgress(progressHolder.progress / 100f)
                            }
                            handler.postDelayed(this, 500)
                        }
                    }
                transformer =
                    Transformer.Builder(context.applicationContext)
                        .setVideoMimeType(plan.mimeType)
                        .setEncoderFactory(encoderFactory)
                        .addListener(
                            object : Transformer.Listener {
                                override fun onCompleted(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                ) {
                                    handler.removeCallbacks(poll)
                                    onProgress(1f)
                                    if (continuation.isActive) continuation.resume(Unit)
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException,
                                ) {
                                    handler.removeCallbacks(poll)
                                    output.delete()
                                    if (continuation.isActive) {
                                        continuation.resumeWithException(
                                            ConversionException(
                                                ConversionException.Reason.Failed,
                                                exportException,
                                            )
                                        )
                                    }
                                }
                            }
                        )
                        .build()
                continuation.invokeOnCancellation {
                    handler.post {
                        handler.removeCallbacks(poll)
                        transformer.cancel()
                        output.delete()
                    }
                }
                transformer.start(composition, output.absolutePath)
                handler.post(poll)
            }
        }
    }

    private fun encoderCapabilities(mime: String): MediaCodecInfo.VideoCapabilities? {
        val encoders =
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }
        // Hardware encoders are an order of magnitude faster; software ones are a last resort.
        val preferred =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                encoders.sortedByDescending { it.isHardwareAccelerated }
            else encoders
        return preferred.firstNotNullOfOrNull { info ->
            runCatching { info.getCapabilitiesForType(mime).videoCapabilities }.getOrNull()
        }
    }

    /** Largest size with the same aspect ratio as [width] x [height] that the encoder accepts. */
    internal fun fit(
        caps: MediaCodecInfo.VideoCapabilities,
        width: Int,
        height: Int,
    ): Pair<Int, Int>? {
        val widthAlign = caps.widthAlignment.coerceAtLeast(2)
        val heightAlign = caps.heightAlignment.coerceAtLeast(2)
        var scale = 1f
        while (scale > 0.2f) {
            val w = ((width * scale).roundToInt() / widthAlign) * widthAlign
            val h = ((height * scale).roundToInt() / heightAlign) * heightAlign
            if (w > 0 && h > 0 && runCatching { caps.isSizeSupported(w, h) }.getOrDefault(false)) {
                return w to h
            }
            scale -= 0.05f
        }
        return null
    }

    private fun bitrateFor(
        mime: String,
        caps: MediaCodecInfo.VideoCapabilities,
        width: Int,
        height: Int,
        source: SourceInfo,
    ): Int {
        val bitsPerPixel = if (mime == MimeTypes.VIDEO_H265) 0.09 else 0.14
        val fps = source.frameRate.coerceIn(15f, 60f)
        val fromPixels = width.toDouble() * height * fps * bitsPerPixel
        // Never below what the source itself spent per pixel.
        val sourcePixels = source.width.toDouble() * source.height
        val fromSource =
            if (source.bitrate > 0 && sourcePixels > 0)
                source.bitrate * (width.toDouble() * height / sourcePixels)
            else 0.0
        val wanted = maxOf(fromPixels, fromSource, 8_000_000.0)
        return wanted.toInt().coerceAtMost(caps.bitrateRange.upper)
    }

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    private fun MediaFormat.longOrNull(key: String): Long? =
        if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null
}
