package com.illuminazionetech.vrclip.ui.page.videolist

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illuminazionetech.vrclip.App
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.database.backup.BackupUtil
import com.illuminazionetech.vrclip.database.backup.BackupUtil.BackupDestination.Clipboard
import com.illuminazionetech.vrclip.database.backup.BackupUtil.BackupDestination.File
import com.illuminazionetech.vrclip.database.backup.BackupUtil.toJsonString
import com.illuminazionetech.vrclip.database.backup.BackupUtil.toURLListString
import com.illuminazionetech.vrclip.database.objects.DownloadedVideoInfo
import com.illuminazionetech.vrclip.ui.common.HapticFeedback.slightHapticFeedback
import com.illuminazionetech.vrclip.ui.common.rememberTextClipboard
import com.illuminazionetech.vrclip.ui.component.CheckBoxItem
import com.illuminazionetech.vrclip.ui.component.ConfirmButton
import com.illuminazionetech.vrclip.ui.component.DismissButton
import com.illuminazionetech.vrclip.ui.component.MediaGridItem
import com.illuminazionetech.vrclip.ui.component.MediaListItem
import com.illuminazionetech.vrclip.ui.component.VRClipDialog
import com.illuminazionetech.vrclip.ui.component.VRClipSearchBar
import com.illuminazionetech.vrclip.ui.component.rememberHiddenSheetState
import com.illuminazionetech.vrclip.util.AUDIO_REGEX
import com.illuminazionetech.vrclip.util.FileUtil
import com.illuminazionetech.vrclip.util.makeToast
import com.illuminazionetech.vrclip.util.toFileSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

private val AudioRegex = Regex(AUDIO_REGEX)

fun DownloadedVideoInfo.filterByType(
    videoFilter: Boolean = false,
    audioFilter: Boolean = true,
): Boolean {
    return if (!(videoFilter || audioFilter)) true
    else if (audioFilter) this.videoPath.contains(AudioRegex)
    else !this.videoPath.contains(AudioRegex)
}

fun DownloadedVideoInfo.filterSort(
    viewState: VideoListViewModel.VideoListViewState,
    filterSet: Set<String>,
): Boolean {
    return filterByType(videoFilter = viewState.videoFilter, audioFilter = viewState.audioFilter) &&
        filterByExtractor(filterSet.elementAtOrNull(viewState.activeFilterIndex))
}

fun DownloadedVideoInfo.filterByExtractor(extractor: String?): Boolean {
    return extractor.isNullOrEmpty() || (this.extractor == extractor)
}

@Composable
private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        top = calculateTopPadding() + other.calculateTopPadding(),
        bottom = calculateBottomPadding() + other.calculateBottomPadding(),
        start =
            calculateStartPadding(layoutDirection) + other.calculateStartPadding(layoutDirection),
        end = calculateEndPadding(layoutDirection) + other.calculateEndPadding(layoutDirection),
    )
}

/**
 * The library of downloaded media: searchable, filterable by type and site, shown as a poster
 * grid or a compact list. Long-press starts multi-selection, whose actions replace the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoListPage(
    viewModel: VideoListViewModel = koinViewModel(),
    onNavigateToPlayer: (DownloadedVideoInfo) -> Unit = {},
) {
    val viewState by viewModel.stateFlow.collectAsStateWithLifecycle()
    val fullVideoList by viewModel.videoListFlow.collectAsStateWithLifecycle(emptyList())
    val searchedVideoList by
        viewModel.searchedVideoListFlow.collectAsStateWithLifecycle(emptyList())
    val videoList = if (viewState.isSearching) searchedVideoList else fullVideoList
    val filterSet by viewModel.filterSetFlow.collectAsState(mutableSetOf())
    val fileSizeMap by
        viewModel.fileSizeMapFlow.collectAsStateWithLifecycle(initialValue = emptyMap())

    val visibleList by
        remember(videoList, viewState, filterSet) {
            derivedStateOf { videoList.filter { it.filterSort(viewState, filterSet) } }
        }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val softKeyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val clipboard = rememberTextClipboard()
    val hostState = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()

    var isGridView by rememberSaveable { mutableStateOf(true) }
    var currentVideoInfo by remember { mutableStateOf(DownloadedVideoInfo()) }
    var isSelectEnabled by remember { mutableStateOf(false) }
    val selectedItemIds = remember { mutableStateListOf<Int>() }
    var showRemoveMultipleItemsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberHiddenSheetState()

    LaunchedEffect(isSelectEnabled) { if (!isSelectEnabled) selectedItemIds.clear() }
    // Drop selections that no longer exist (deleted, or filtered out by a new filter).
    LaunchedEffect(visibleList) {
        val visibleIds = visibleList.map { it.id }.toSet()
        selectedItemIds.retainAll(visibleIds)
    }

    val totalSize by remember(fullVideoList, fileSizeMap) {
        derivedStateOf { fullVideoList.sumOf { fileSizeMap.getOrElse(it.id) { 0L } } }
    }
    val selectedFileSizeSum by remember {
        derivedStateOf { selectedItemIds.sumOf { fileSizeMap.getOrElse(it) { 0L } } }
    }

    BackHandler(isSelectEnabled || viewState.isSearching) {
        if (isSelectEnabled) isSelectEnabled = false else viewModel.toggleSearch(false)
    }

    LaunchedEffect(showBottomSheet, isSelectEnabled) {
        if (showBottomSheet || isSelectEnabled) softKeyboardController?.hide()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    AnimatedContent(
                        targetState = isSelectEnabled,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "libraryTitle",
                    ) { selecting ->
                        Text(
                            text =
                                if (selecting)
                                    pluralStringResource(
                                        R.plurals.library_selected,
                                        selectedItemIds.size,
                                        selectedItemIds.size,
                                    )
                                else stringResource(R.string.nav_library),
                            maxLines = 1,
                        )
                    }
                },
                subtitle = {
                    val subtitle =
                        if (isSelectEnabled) selectedFileSizeSum.toFileSizeText()
                        else if (fullVideoList.isEmpty()) stringResource(R.string.library_empty_subtitle)
                        else
                            pluralStringResource(
                                R.plurals.library_items,
                                fullVideoList.size,
                                fullVideoList.size,
                            ) + " · " + totalSize.toFileSizeText()
                    Text(subtitle, maxLines = 1)
                },
                navigationIcon = {
                    AnimatedVisibility(visible = isSelectEnabled, enter = fadeIn(), exit = fadeOut()) {
                        IconButton(onClick = { isSelectEnabled = false }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.close))
                        }
                    }
                },
                actions = {
                    if (isSelectEnabled) {
                        val allSelected =
                            selectedItemIds.size == visibleList.size && visibleList.isNotEmpty()
                        IconButton(
                            onClick = {
                                view.slightHapticFeedback()
                                if (allSelected) selectedItemIds.clear()
                                else {
                                    selectedItemIds.clear()
                                    selectedItemIds.addAll(visibleList.map { it.id })
                                }
                            }
                        ) {
                            Icon(
                                if (allSelected) Icons.Rounded.RemoveDone else Icons.Rounded.DoneAll,
                                contentDescription = stringResource(R.string.select_all),
                            )
                        }
                        IconButton(
                            onClick = {
                                view.slightHapticFeedback()
                                showRemoveMultipleItemsDialog = true
                            },
                            enabled = selectedItemIds.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.remove),
                            )
                        }
                    } else {
                        if (fullVideoList.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    view.slightHapticFeedback()
                                    viewModel.toggleSearch(!viewState.isSearching)
                                    if (!viewState.isSearching) {
                                        scope.launch { gridState.animateScrollToItem(0) }
                                    }
                                }
                            ) {
                                Icon(
                                    if (viewState.isSearching) Icons.Rounded.SearchOff
                                    else Icons.Rounded.Search,
                                    contentDescription = stringResource(R.string.search),
                                )
                            }
                            IconButton(
                                onClick = {
                                    view.slightHapticFeedback()
                                    isGridView = !isGridView
                                }
                            ) {
                                Icon(
                                    if (isGridView) Icons.AutoMirrored.Rounded.ViewList
                                    else Icons.Rounded.GridView,
                                    contentDescription =
                                        stringResource(
                                            if (isGridView) R.string.switch_to_list_view
                                            else R.string.switch_to_grid_view
                                        ),
                                )
                            }
                        }
                        LibraryOverflowMenu(
                            canExport = visibleList.isNotEmpty(),
                            onExport = { showExportDialog = true },
                            onImport = { showImportDialog = true },
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(hostState = hostState) },
    ) { innerPadding ->
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            columns = if (isGridView) GridCells.Adaptive(220.dp) else GridCells.Adaptive(480.dp),
            contentPadding =
                innerPadding + PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isGridView) 12.dp else 8.dp),
        ) {
            if (fullVideoList.isNotEmpty()) {
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        AnimatedVisibility(visible = viewState.isSearching) {
                            VRClipSearchBar(
                                modifier = Modifier.padding(bottom = 8.dp),
                                text = viewState.searchText,
                                placeholderText = stringResource(R.string.search_in_downloads),
                                onValueChange = viewModel::updateSearchText,
                            )
                        }
                        LibraryFilters(
                            viewState = viewState,
                            filterSet = filterSet,
                            onAudio = viewModel::clickAudioFilter,
                            onVideo = viewModel::clickVideoFilter,
                            onExtractor = viewModel::clickExtractorFilter,
                        )
                    }
                }
            }

            if (visibleList.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    LibraryEmptyState(
                        modifier = Modifier.animateItem(),
                        noResults = fullVideoList.isNotEmpty(),
                    )
                }
            }

            items(items = visibleList, key = { it.id }) { info ->
                val onClick: () -> Unit = {
                    if (info.videoPath.contains(AudioRegex)) {
                        FileUtil.openFile(path = info.videoPath) {
                            makeToast(App.context.getString(R.string.file_unavailable))
                        }
                    } else {
                        onNavigateToPlayer(info)
                    }
                }
                val onLongClick: () -> Unit = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isSelectEnabled = true
                    if (!selectedItemIds.contains(info.id)) selectedItemIds.add(info.id)
                }
                val onSelect: () -> Unit = {
                    view.slightHapticFeedback()
                    if (selectedItemIds.contains(info.id)) selectedItemIds.remove(info.id)
                    else selectedItemIds.add(info.id)
                }
                val onShowContextMenu: () -> Unit = {
                    view.slightHapticFeedback()
                    currentVideoInfo = info
                    showBottomSheet = true
                }
                if (isGridView) {
                    MediaGridItem(
                        modifier = Modifier.animateItem(),
                        title = info.videoTitle,
                        author = info.videoAuthor,
                        thumbnailUrl = info.thumbnailUrl,
                        videoPath = info.videoPath,
                        projectionOverride = info.projectionOverride,
                        videoFileSize = fileSizeMap.getOrElse(info.id) { 0L },
                        isSelectEnabled = { isSelectEnabled },
                        isSelected = { selectedItemIds.contains(info.id) },
                        onSelect = onSelect,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onShowContextMenu = onShowContextMenu,
                    )
                } else {
                    MediaListItem(
                        modifier = Modifier.animateItem(),
                        title = info.videoTitle,
                        author = info.videoAuthor,
                        thumbnailUrl = info.thumbnailUrl,
                        videoPath = info.videoPath,
                        projectionOverride = info.projectionOverride,
                        videoFileSize = fileSizeMap.getOrElse(info.id) { 0L },
                        isSelectEnabled = { isSelectEnabled },
                        isSelected = { selectedItemIds.contains(info.id) },
                        onSelect = onSelect,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onShowContextMenu = onShowContextMenu,
                    )
                }
            }
        }
    }

    if (showBottomSheet) {
        LaunchedEffect(Unit) { sheetState.show() }
        VideoDetailDrawer(
            sheetState = sheetState,
            info = currentVideoInfo,
            isFileAvailable = fileSizeMap[currentVideoInfo.id] != 0L,
            onPlay = { onNavigateToPlayer(currentVideoInfo) },
            onDismissRequest = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { showBottomSheet = false }
            },
            onDelete = { showRemoveDialog = true },
        )
    }

    var deleteFile by remember { mutableStateOf(false) }

    if (showRemoveDialog) {
        RemoveItemDialog(
            info = currentVideoInfo,
            deleteFile = deleteFile,
            onDeleteFileToggled = { deleteFile = it },
            onRemoveConfirm = {
                viewModel.deleteDownloadHistory(listOf(currentVideoInfo), deleteFile = deleteFile)
            },
            onDismissRequest = { showRemoveDialog = false },
        )
    }

    if (showRemoveMultipleItemsDialog) {
        VRClipDialog(
            onDismissRequest = { showRemoveMultipleItemsDialog = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null) },
            title = { Text(stringResource(R.string.delete_info)) },
            text = {
                Column {
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        text =
                            stringResource(R.string.delete_multiple_items_msg)
                                .format(selectedItemIds.size),
                    )
                    CheckBoxItem(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        text =
                            stringResource(R.string.delete_file) +
                                " (${selectedFileSizeSum.toFileSizeText()})",
                        checked = deleteFile,
                    ) {
                        deleteFile = !deleteFile
                    }
                }
            },
            confirmButton = {
                ConfirmButton {
                    viewModel.deleteDownloadHistory(
                        infoList = videoList.filter { selectedItemIds.contains(it.id) },
                        deleteFile = deleteFile,
                    )
                    showRemoveMultipleItemsDialog = false
                    isSelectEnabled = false
                }
            },
            dismissButton = { DismissButton { showRemoveMultipleItemsDialog = false } },
        )
    }

    var backupString by remember { mutableStateOf("") }

    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/plain")
        ) { uri ->
            uri?.let {
                scope.launch(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(backupString.toByteArray())
                    }
                    withContext(Dispatchers.Main) { showExportDialog = false }
                }
            }
        }

    if (showExportDialog) {
        val list =
            if (selectedItemIds.isNotEmpty()) {
                videoList.filter { selectedItemIds.contains(it.id) }
            } else {
                visibleList
            }

        ExportDialog(onDismissRequest = { showExportDialog = false }, itemCount = list.size) {
            type,
            destination ->
            list.backupToString(type).let {
                when (destination) {
                    Clipboard -> clipboard.setText(it)
                    File -> {
                        backupString = it
                        exportLauncher.launch(
                            BackupUtil.getDownloadHistoryExportFilename(context = context)
                        )
                    }
                }
                view.slightHapticFeedback()
                showExportDialog = false
            }
        }
    }

    val importLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                viewModel.importBackupFromUri(context, uri) {
                    viewModel.showImportedSnackbar(hostState, context, it)
                }
            }
        }

    if (showImportDialog) {
        ImportDialog(onDismissRequest = { showImportDialog = false }) { destination ->
            when (destination) {
                Clipboard ->
                    clipboard.readText { str ->
                        str?.let {
                            viewModel.importBackupFromText(it) { count ->
                                viewModel.showImportedSnackbar(hostState, context, count)
                            }
                        }
                    }
                File -> importLauncher.launch("text/plain")
            }
            view.slightHapticFeedback()
            showImportDialog = false
        }
    }
}

@Composable
private fun LibraryOverflowMenu(canExport: Boolean, onExport: () -> Unit, onImport: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(id = R.string.show_more_actions),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (canExport) {
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, null) },
                    text = { Text(stringResource(id = R.string.export_backup)) },
                    onClick = {
                        expanded = false
                        onExport()
                    },
                )
            }
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Rounded.Restore, null) },
                text = { Text(stringResource(id = R.string.import_backup)) },
                onClick = {
                    expanded = false
                    onImport()
                },
            )
        }
    }
}

@Composable
private fun LibraryFilters(
    viewState: VideoListViewModel.VideoListViewState,
    filterSet: Set<String>,
    onAudio: () -> Unit,
    onVideo: () -> Unit,
    onExtractor: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = viewState.videoFilter,
            onClick = onVideo,
            label = { Text(stringResource(R.string.video)) },
        )
        FilterChip(
            selected = viewState.audioFilter,
            onClick = onAudio,
            label = { Text(stringResource(R.string.audio)) },
        )
        if (filterSet.size > 1) {
            filterSet.forEachIndexed { index, extractor ->
                FilterChip(
                    selected = viewState.activeFilterIndex == index,
                    onClick = { onExtractor(index) },
                    label = { Text(extractor) },
                )
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(noResults: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 56.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(144.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = MaterialShapes.Clover4Leaf.toShape(),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {}
            Icon(
                imageVector = if (noResults) Icons.Rounded.SearchOff else Icons.Rounded.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text =
                stringResource(
                    if (noResults) R.string.library_no_results else R.string.no_downloaded_media
                ),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            text =
                stringResource(
                    if (noResults) R.string.library_no_results_desc else R.string.library_empty_desc
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).padding(horizontal = 32.dp).widthIn(max = 420.dp),
        )
    }
}

private fun List<DownloadedVideoInfo>.backupToString(type: BackupUtil.BackupType): String {
    return when (type) {
        BackupUtil.BackupType.DownloadHistory -> reversed().toJsonString()
        BackupUtil.BackupType.URLList -> toURLListString()
        else -> throw IllegalArgumentException()
    }
}
