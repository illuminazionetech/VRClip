package com.illuminazionetech.vrclip.database.objects

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity
@Serializable
data class DownloadedVideoInfo(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val videoTitle: String,
    val videoAuthor: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val videoPath: String,
    @ColumnInfo(defaultValue = "Unknown") val extractor: String = "Unknown",
    /**
     * User-forced [com.illuminazionetech.vrclip.player.ProjectionMode] name, overriding
     * filename/aspect-ratio detection for this specific file. `null` means "auto-detect".
     */
    @ColumnInfo(defaultValue = "NULL") val projectionOverride: String? = null,
    /** Where playback stopped last time, so the player can pick up from there. */
    @ColumnInfo(defaultValue = "0") val playbackPositionMs: Long = 0L,
    /** Duration seen by the player, used to draw the "watched" bar in lists. */
    @ColumnInfo(defaultValue = "0") val playbackDurationMs: Long = 0L,
) {
    @Ignore
    constructor() :
        this(
            id = 0,
            videoTitle = "Video",
            videoAuthor = "Author",
            videoUrl = "Url",
            thumbnailUrl = "Thumbnail",
            videoPath = "Path",
            extractor = "Unknown",
            projectionOverride = null,
        )
}

/** Fraction watched, or 0 when the video was never played or was finished. */
val DownloadedVideoInfo.watchedFraction: Float
    get() =
        if (playbackDurationMs > 0 && playbackPositionMs > 0)
            (playbackPositionMs.toFloat() / playbackDurationMs).coerceIn(0f, 1f)
        else 0f
