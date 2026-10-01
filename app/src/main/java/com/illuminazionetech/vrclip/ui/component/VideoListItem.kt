package com.illuminazionetech.vrclip.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.player.ProjectionDetector
import com.illuminazionetech.vrclip.player.ProjectionMode
import com.illuminazionetech.vrclip.ui.common.AsyncImageImpl
import com.illuminazionetech.vrclip.util.AUDIO_REGEX
import com.illuminazionetech.vrclip.util.toFileSizeText

private val OverlayScrim = Color.Black.copy(alpha = 0.62f)
private val AudioRegex = Regex(AUDIO_REGEX)

/** Short badge text for immersive projections ("360°", "180° 3D", "3D"), null for flat video. */
fun ProjectionMode.badgeText(): String? =
    when (this) {
        ProjectionMode.FLAT -> null
        ProjectionMode.MONO_360 -> "360°"
        ProjectionMode.STEREO_360_TB,
        ProjectionMode.STEREO_360_LR -> "360° 3D"
        ProjectionMode.MONO_180 -> "180°"
        ProjectionMode.STEREO_180_TB,
        ProjectionMode.STEREO_180_LR -> "180° 3D"
        ProjectionMode.SBS_3D,
        ProjectionMode.OU_3D -> "3D"
    }

@Composable
private fun rememberProjectionBadge(videoPath: String, projectionOverride: String?): String? =
    remember(videoPath, projectionOverride) {
        (ProjectionMode.fromStorageKey(projectionOverride)
                ?: ProjectionDetector.detectProjection(videoPath))
            .badgeText()
    }

/** A single library entry laid out as a row: thumbnail, title, author, size. */
@Composable
fun MediaListItem(
    modifier: Modifier = Modifier,
    title: String = "",
    author: String = "",
    thumbnailUrl: String = "",
    videoPath: String = "",
    projectionOverride: String? = null,
    videoFileSize: Long = 0L,
    /** Fraction of the video already watched, 0 to hide the bar. */
    watchedFraction: Float = 0f,
    isSelectEnabled: () -> Boolean = { false },
    isSelected: () -> Boolean = { false },
    onSelect: () -> Unit = {},
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onShowContextMenu: () -> Unit = {},
) {
    val isAudio = videoPath.contains(AudioRegex)
    val selected = isSelectEnabled() && isSelected()
    val containerColor =
        if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .mediaClickable(isSelectEnabled, isSelected, onSelect, onClick, onLongClick),
        color = containerColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Clipped like the thumbnail so the watched bar follows its rounded corners.
            Box(Modifier.clip(MaterialTheme.shapes.medium)) {
                MediaImage(
                    modifier = Modifier,
                    imageModel = thumbnailUrl,
                    isAudio = isAudio,
                )
                WatchedBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    fraction = watchedFraction,
                )
                ProjectionBadge(
                    modifier = Modifier.align(Alignment.TopStart),
                    text = rememberProjectionBadge(videoPath, projectionOverride),
                )
                SelectionMark(
                    modifier = Modifier.align(Alignment.Center),
                    visible = isSelectEnabled(),
                    selected = isSelected(),
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 12.dp).weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                MediaTexts(title = title, author = author, fileSize = videoFileSize)
            }
            AnimatedVisibility(
                visible = !isSelectEnabled(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                IconButton(onClick = onShowContextMenu) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(id = R.string.show_more_actions),
                    )
                }
            }
        }
    }
}

/** A single library entry laid out as a poster card, for the grid view. */
@Composable
fun MediaGridItem(
    modifier: Modifier = Modifier,
    title: String = "",
    author: String = "",
    thumbnailUrl: String = "",
    videoPath: String = "",
    projectionOverride: String? = null,
    videoFileSize: Long = 0L,
    /** Fraction of the video already watched, 0 to hide the bar. */
    watchedFraction: Float = 0f,
    isSelectEnabled: () -> Boolean = { false },
    isSelected: () -> Boolean = { false },
    onSelect: () -> Unit = {},
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onShowContextMenu: () -> Unit = {},
) {
    val isAudio = videoPath.contains(AudioRegex)
    val selected = isSelectEnabled() && isSelected()
    val inset by animateDpAsState(if (selected) 8.dp else 0.dp, label = "selectionInset")

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .mediaClickable(isSelectEnabled, isSelected, onSelect, onClick, onLongClick),
        color =
            if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth()
                    .padding(inset)
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.large)
            ) {
                if (isAudio || thumbnailUrl.isBlank()) {
                    AudioArtwork(Modifier.fillMaxSize(), isAudio = isAudio)
                } else {
                    AsyncImageImpl(
                        modifier =
                            Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                    )
                }
                WatchedBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    fraction = watchedFraction,
                )
                ProjectionBadge(
                    modifier = Modifier.align(Alignment.TopStart),
                    text = rememberProjectionBadge(videoPath, projectionOverride),
                )
                SelectionMark(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    visible = isSelectEnabled(),
                    selected = isSelected(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier =
                        Modifier.weight(1f).padding(start = 14.dp, top = 10.dp, bottom = 12.dp)
                ) {
                    MediaTexts(
                        title = title,
                        author = author,
                        fileSize = videoFileSize,
                        minTitleLines = 2,
                    )
                }
                AnimatedVisibility(
                    visible = !isSelectEnabled(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    IconButton(onClick = onShowContextMenu) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(id = R.string.show_more_actions),
                        )
                    }
                }
            }
        }
    }
}

/** Thin bar along the bottom of a thumbnail showing how much of the video was watched. */
@Composable
private fun WatchedBar(modifier: Modifier, fraction: Float) {
    if (fraction <= 0f) return
    Box(
        modifier = modifier.fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.45f))
    ) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

private fun Modifier.mediaClickable(
    isSelectEnabled: () -> Boolean,
    isSelected: () -> Boolean,
    onSelect: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = semantics {
    if (isSelectEnabled()) selected = isSelected()
}
    .combinedClickable(
        role = Role.Button,
        onClick = { if (isSelectEnabled()) onSelect() else onClick() },
        onLongClick = { if (isSelectEnabled()) onSelect() else onLongClick() },
    )

@Composable
private fun MediaTexts(title: String, author: String, fileSize: Long, minTitleLines: Int = 1) {
    val isFileAvailable = fileSize != 0L
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        minLines = minTitleLines,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    val meta =
        listOfNotNull(
                author.takeIf { it.isNotBlank() && it != "null" && it != "playlist" },
                if (isFileAvailable) fileSize.toFileSizeText() else null,
            )
            .joinToString(" · ")
    if (meta.isNotEmpty()) {
        Text(
            modifier = Modifier.padding(top = 2.dp),
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (!isFileAvailable) {
        Text(
            modifier = Modifier.padding(top = 2.dp),
            text = stringResource(R.string.unavailable),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProjectionBadge(text: String?, modifier: Modifier = Modifier) {
    if (text == null) return
    Surface(
        modifier = modifier.padding(6.dp),
        color = OverlayScrim,
        contentColor = Color.White,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun SelectionMark(visible: Boolean, selected: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.6f),
        exit = fadeOut() + scaleOut(targetScale = 0.6f),
    ) {
        Icon(
            imageVector =
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
            modifier =
                Modifier.size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.onPrimary else OverlayScrim
                    ),
        )
    }
}

@Composable
private fun AudioArtwork(modifier: Modifier = Modifier, isAudio: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier.background(
                Brush.linearGradient(listOf(colors.tertiaryContainer, colors.primaryContainer))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isAudio) Icons.Rounded.MusicNote else Icons.Rounded.Movie,
            contentDescription = null,
            tint = colors.onTertiaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
fun MediaImage(
    modifier: Modifier = Modifier,
    imageModel: String,
    isAudio: Boolean = false,
    contentDescription: String? = null,
) {
    val imageModifier =
        modifier
            .height(72.dp)
            .aspectRatio(if (!isAudio) 16f / 9f else 1f, matchHeightConstraintsFirst = true)
            .clip(MaterialTheme.shapes.medium)
    if (imageModel.isBlank()) {
        AudioArtwork(imageModifier, isAudio = isAudio)
    } else {
        AsyncImageImpl(
            modifier = imageModifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
            model = imageModel,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
        )
    }
}
