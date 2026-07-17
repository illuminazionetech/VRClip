package com.illuminazionetech.vrclip.ui.page.settings.format

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArtTrack
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.SpatialAudioOff
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.VideoSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.ui.common.booleanState
import com.illuminazionetech.vrclip.ui.common.intState
import com.illuminazionetech.vrclip.ui.component.BackButton
import com.illuminazionetech.vrclip.ui.component.ConfirmButton
import com.illuminazionetech.vrclip.ui.component.DismissButton
import com.illuminazionetech.vrclip.ui.component.PreferenceInfo
import com.illuminazionetech.vrclip.ui.component.PreferenceItem
import com.illuminazionetech.vrclip.ui.component.PreferenceSubtitle
import com.illuminazionetech.vrclip.ui.component.PreferenceSwitch
import com.illuminazionetech.vrclip.ui.component.PreferenceSwitchWithDivider
import com.illuminazionetech.vrclip.util.AUDIO_CONVERSION_FORMAT
import com.illuminazionetech.vrclip.util.AUDIO_CONVERT
import com.illuminazionetech.vrclip.util.CROP_ARTWORK
import com.illuminazionetech.vrclip.util.CUSTOM_COMMAND
import com.illuminazionetech.vrclip.util.AUDIO_FORMAT
import com.illuminazionetech.vrclip.util.AUDIO_QUALITY
import com.illuminazionetech.vrclip.util.DownloadUtil
import com.illuminazionetech.vrclip.util.DownloadUtil.toFormatSorter
import com.illuminazionetech.vrclip.util.EMBED_METADATA
import com.illuminazionetech.vrclip.util.EMBED_SUBTITLE
import com.illuminazionetech.vrclip.util.EMBED_THUMBNAIL
import com.illuminazionetech.vrclip.util.EXTRACT_AUDIO
import com.illuminazionetech.vrclip.util.FORMAT_SELECTION
import com.illuminazionetech.vrclip.util.FORMAT_SORTING
import com.illuminazionetech.vrclip.util.MERGE_MULTI_AUDIO_STREAM
import com.illuminazionetech.vrclip.util.MERGE_OUTPUT_MKV
import com.illuminazionetech.vrclip.util.PreferenceStrings
import com.illuminazionetech.vrclip.util.PreferenceUtil
import com.illuminazionetech.vrclip.util.PreferenceUtil.getBoolean
import com.illuminazionetech.vrclip.util.PreferenceUtil.getString
import com.illuminazionetech.vrclip.util.PreferenceUtil.updateBoolean
import com.illuminazionetech.vrclip.util.PreferenceUtil.updateInt
import com.illuminazionetech.vrclip.util.PreferenceUtil.updateString
import com.illuminazionetech.vrclip.util.SORTING_FIELDS
import com.illuminazionetech.vrclip.util.SUBTITLE
import com.illuminazionetech.vrclip.util.VIDEO_CLIP
import com.illuminazionetech.vrclip.util.VIDEO_FORMAT
import com.illuminazionetech.vrclip.util.VIDEO_QUALITY

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadFormatPreferences(onNavigateBack: () -> Unit, navigateToSubtitlePage: () -> Unit) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )

    var audioSwitch by remember { mutableStateOf(EXTRACT_AUDIO.getBoolean()) }
    var isArtworkCroppingEnabled by remember { mutableStateOf(CROP_ARTWORK.getBoolean()) }
    val downloadSubtitle by SUBTITLE.booleanState
    val embedSubtitle by EMBED_SUBTITLE.booleanState
    var remuxToMkv by MERGE_OUTPUT_MKV.booleanState
    var embedMetadata by EMBED_METADATA.booleanState

    var showAudioFormatDialog by remember { mutableStateOf(false) }
    var showAudioQualityDialog by remember { mutableStateOf(false) }
    var showAudioConvertDialog by remember { mutableStateOf(false) }
    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showVideoFormatDialog by remember { mutableStateOf(false) }
    var showFormatSorterDialog by remember { mutableStateOf(false) }
    var showVideoClipDialog by remember { mutableStateOf(false) }

    var videoFormat by VIDEO_FORMAT.intState
    var videoQuality by VIDEO_QUALITY.intState
    var convertFormat by AUDIO_CONVERSION_FORMAT.intState
    var sortingFields by
        remember(showFormatSorterDialog) { mutableStateOf(SORTING_FIELDS.getString()) }
    val audioFormat = PreferenceStrings.getAudioFormatDesc()
    var convertAudio by AUDIO_CONVERT.booleanState
    var isFormatSortingEnabled by FORMAT_SORTING.booleanState
    val audioQuality = PreferenceStrings.getAudioQualityDesc()
    var isVideoClipEnabled by VIDEO_CLIP.booleanState
    var isFormatSelectionEnabled by FORMAT_SELECTION.booleanState
    var mergeAudioStream by MERGE_MULTI_AUDIO_STREAM.booleanState
    var showMergeAudioDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(modifier = Modifier, text = stringResource(id = R.string.format)) },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
        content = {
            val isCustomCommandEnabled by remember { mutableStateOf(CUSTOM_COMMAND.getBoolean()) }
            LazyColumn(contentPadding = it) {
                if (isCustomCommandEnabled)
                    item {
                        PreferenceInfo(
                            text = stringResource(id = R.string.custom_command_enabled_hint)
                        )
                    }
                item { PreferenceSubtitle(text = stringResource(id = R.string.audio)) }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.extract_audio),
                        description = stringResource(id = R.string.extract_audio_summary),
                        icon = Icons.Rounded.MusicNote,
                        isChecked = audioSwitch,
                        enabled = !isCustomCommandEnabled,
                        onClick = {
                            audioSwitch = !audioSwitch
                            PreferenceUtil.updateValue(EXTRACT_AUDIO, audioSwitch)
                        },
                    )
                }
                item {
                    PreferenceItem(
                        title = stringResource(id = R.string.audio_format_preference),
                        description = audioFormat,
                        icon = Icons.Rounded.AudioFile,
                        enabled = !isCustomCommandEnabled && !isFormatSortingEnabled,
                        onClick = { showAudioFormatDialog = true },
                    )
                }
                item {
                    PreferenceItem(
                        title = stringResource(id = R.string.audio_quality),
                        description = audioQuality,
                        icon = Icons.Rounded.HighQuality,
                        onClick = { showAudioQualityDialog = true },
                        enabled = !isCustomCommandEnabled && !isFormatSortingEnabled,
                    )
                }
                item {
                    PreferenceSwitchWithDivider(
                        title = stringResource(R.string.convert_audio_format),
                        description = PreferenceStrings.getAudioConvertDesc(convertFormat),
                        icon = Icons.Rounded.Sync,
                        enabled = audioSwitch && !isCustomCommandEnabled,
                        onClick = { showAudioConvertDialog = true },
                        isChecked = convertAudio,
                        onChecked = {
                            convertAudio = !convertAudio
                            AUDIO_CONVERT.updateBoolean(convertAudio)
                        },
                    )
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.embed_metadata),
                        description = stringResource(id = R.string.embed_metadata_desc),
                        enabled = audioSwitch && !isCustomCommandEnabled,
                        isChecked = embedMetadata,
                        icon = Icons.Rounded.ArtTrack,
                        onClick = {
                            embedMetadata = !embedMetadata
                            EMBED_METADATA.updateBoolean(embedMetadata)
                        },
                    )
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(R.string.crop_artwork),
                        description = stringResource(R.string.crop_artwork_desc),
                        icon = Icons.Rounded.Crop,
                        enabled = embedMetadata && audioSwitch && !isCustomCommandEnabled,
                        isChecked = isArtworkCroppingEnabled,
                    ) {
                        isArtworkCroppingEnabled = !isArtworkCroppingEnabled
                        PreferenceUtil.updateValue(CROP_ARTWORK, isArtworkCroppingEnabled)
                    }
                }
                item { PreferenceSubtitle(text = stringResource(id = R.string.video)) }
                item {
                    PreferenceItem(
                        title = stringResource(R.string.video_format_preference),
                        description = PreferenceStrings.getVideoFormatLabel(videoFormat),
                        icon = Icons.Rounded.VideoFile,
                        enabled = !audioSwitch && !isCustomCommandEnabled && !isFormatSortingEnabled,
                    ) {
                        showVideoFormatDialog = true
                    }
                }
                item {
                    PreferenceItem(
                        title = stringResource(id = R.string.video_quality),
                        description = PreferenceStrings.getVideoResolutionDesc(videoQuality),
                        icon = Icons.Rounded.HighQuality,
                        enabled = !audioSwitch && !isCustomCommandEnabled && !isFormatSortingEnabled,
                    ) {
                        showVideoQualityDialog = true
                    }
                }
                item {
                    var embedThumbnail by EMBED_THUMBNAIL.booleanState

                    PreferenceSwitch(
                        title = stringResource(id = R.string.embed_thumbnail),
                        description = stringResource(id = R.string.embed_thumbnail_desc),
                        icon = Icons.Rounded.Photo,
                        isChecked = embedThumbnail,
                        enabled = !isCustomCommandEnabled && !audioSwitch,
                    ) {
                        embedThumbnail = !embedThumbnail
                        EMBED_THUMBNAIL.updateBoolean(embedThumbnail)
                    }
                }

                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.remux_container_mkv),
                        description = stringResource(id = R.string.remux_container_mkv_desc),
                        isChecked = (downloadSubtitle && embedSubtitle) || remuxToMkv,
                        icon = Icons.Rounded.Movie,
                        enabled =
                            !(downloadSubtitle && embedSubtitle) &&
                                !isCustomCommandEnabled &&
                                !audioSwitch,
                        onClick = {
                            remuxToMkv = !remuxToMkv
                            MERGE_OUTPUT_MKV.updateBoolean(remuxToMkv)
                        },
                    )
                }
                if (downloadSubtitle && embedSubtitle) {
                    item {
                        PreferenceInfo(text = stringResource(id = R.string.embed_subtitles_mkv_msg))
                    }
                }

                item { PreferenceSubtitle(text = stringResource(id = R.string.advanced_settings)) }
                item {
                    PreferenceItem(
                        title = stringResource(id = R.string.subtitle),
                        icon = Icons.Rounded.Subtitles,
                        enabled = !isCustomCommandEnabled,
                        description = stringResource(id = R.string.subtitle_desc),
                    ) {
                        navigateToSubtitlePage()
                    }
                }
                item {
                    PreferenceSwitchWithDivider(
                        title = stringResource(id = R.string.format_sorting),
                        icon = Icons.Rounded.Sort,
                        description = stringResource(id = R.string.format_sorting_desc),
                        enabled = !isCustomCommandEnabled,
                        isChecked = isFormatSortingEnabled,
                        onChecked = {
                            isFormatSortingEnabled = !isFormatSortingEnabled
                            FORMAT_SORTING.updateBoolean(isFormatSortingEnabled)
                        },
                        onClick = { showFormatSorterDialog = true },
                    )
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.format_selection),
                        icon = Icons.Rounded.VideoSettings,
                        enabled = !isCustomCommandEnabled,
                        description = stringResource(id = R.string.format_selection_desc),
                        isChecked = isFormatSelectionEnabled,
                    ) {
                        isFormatSelectionEnabled = !isFormatSelectionEnabled
                        PreferenceUtil.updateValue(FORMAT_SELECTION, isFormatSelectionEnabled)
                    }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.clip_video),
                        description = stringResource(id = R.string.clip_video_desc),
                        icon = Icons.Rounded.ContentCut,
                        isChecked = isVideoClipEnabled,
                        enabled = !isCustomCommandEnabled && isFormatSelectionEnabled,
                    ) {
                        if (!isVideoClipEnabled) showVideoClipDialog = true
                        else {
                            isVideoClipEnabled = false
                            VIDEO_CLIP.updateBoolean(false)
                        }
                    }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.merge_audiostream),
                        description = stringResource(id = R.string.merge_audiostream_desc),
                        isChecked = mergeAudioStream,
                        icon = Icons.Rounded.SpatialAudioOff,
                        onClick = {
                            if (mergeAudioStream) {
                                mergeAudioStream = false
                                MERGE_MULTI_AUDIO_STREAM.updateBoolean(false)
                            } else {
                                showMergeAudioDialog = true
                            }
                        },
                        enabled = !isCustomCommandEnabled && isFormatSelectionEnabled,
                    )
                }
            }
        },
    )
    if (showAudioFormatDialog) {
        AudioFormatDialog { showAudioFormatDialog = false }
    }
    if (showAudioQualityDialog) {
        AudioQualityDialog { showAudioQualityDialog = false }
    }
    if (showAudioConvertDialog) {
        AudioConversionDialog(
            onDismissRequest = { showAudioConvertDialog = false },
            audioFormat = convertFormat,
            onConfirm = {
                convertFormat = it
                AUDIO_CONVERSION_FORMAT.updateInt(it)
            },
        )
    }
    if (showVideoQualityDialog) {
        VideoQualityDialog(
            videoQuality = videoQuality,
            onDismissRequest = { showVideoQualityDialog = false },
        ) {
            videoQuality = it
            VIDEO_QUALITY.updateInt(it)
        }
    }
    if (showVideoFormatDialog) {
        VideoFormatDialog(
            videoFormatPreference = videoFormat,
            onDismissRequest = { showVideoFormatDialog = false },
        ) {
            PreferenceUtil.encodeInt(VIDEO_FORMAT, it)
            videoFormat = it
        }
    }
    if (showFormatSorterDialog) {
        FormatSortingDialog(
            fields = sortingFields,
            onImport = {
                sortingFields =
                    DownloadUtil.DownloadPreferences.createFromPreferences().toFormatSorter()
            },
            onDismissRequest = { showFormatSorterDialog = false },
            showSwitch = false,
            onConfirm = {
                sortingFields = it
                SORTING_FIELDS.updateString(sortingFields)
            },
        )
    }
    if (showVideoClipDialog) {
        AlertDialog(
            onDismissRequest = { showVideoClipDialog = false },
            icon = { Icon(Icons.Rounded.ContentCut, null) },
            confirmButton = {
                ConfirmButton {
                    isVideoClipEnabled = true
                    VIDEO_CLIP.updateBoolean(true)
                    showVideoClipDialog = false
                }
            },
            dismissButton = { DismissButton { showVideoClipDialog = false } },
            text = { Text(stringResource(id = R.string.clip_video_dialog_msg)) },
            title = {
                Text(
                    stringResource(id = R.string.enable_experimental_feature),
                    textAlign = TextAlign.Center,
                )
            },
        )
    }
    if (showMergeAudioDialog) {
        AlertDialog(
            onDismissRequest = { showMergeAudioDialog = false },
            icon = { Icon(Icons.Rounded.SpatialAudioOff, null) },
            confirmButton = {
                ConfirmButton {
                    mergeAudioStream = true
                    MERGE_MULTI_AUDIO_STREAM.updateBoolean(true)
                    showMergeAudioDialog = false
                }
            },
            dismissButton = { DismissButton { showMergeAudioDialog = false } },
            text = { Text(stringResource(id = R.string.merge_audiostream_desc)) },
            title = {
                Text(
                    stringResource(id = R.string.enable_experimental_feature),
                    textAlign = TextAlign.Center,
                )
            },
        )
    }
}
