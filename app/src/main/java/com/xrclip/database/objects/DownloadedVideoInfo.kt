package com.xrclip.database.objects

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
     * User-forced [com.xrclip.player.ProjectionMode] name, overriding filename/aspect-ratio
     * detection for this specific file. `null` means "auto-detect".
     */
    @ColumnInfo(defaultValue = "NULL") val projectionOverride: String? = null,
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
