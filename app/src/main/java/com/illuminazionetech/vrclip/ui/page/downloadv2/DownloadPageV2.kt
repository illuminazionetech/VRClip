package com.illuminazionetech.vrclip.ui.page.downloadv2

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.toShape
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.illuminazionetech.vrclip.App
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.download.DownloaderV2
import com.illuminazionetech.vrclip.download.Task
import com.illuminazionetech.vrclip.download.Task.DownloadState.Completed
import com.illuminazionetech.vrclip.download.Task.DownloadState.Error
import com.illuminazionetech.vrclip.download.Task.DownloadState.FetchingInfo
import com.illuminazionetech.vrclip.download.Task.DownloadState.Idle
import com.illuminazionetech.vrclip.download.Task.DownloadState.ReadyWithInfo
import com.illuminazionetech.vrclip.download.Task.DownloadState.Running
import com.illuminazionetech.vrclip.ui.common.HapticFeedback.slightHapticFeedback
import com.illuminazionetech.vrclip.ui.common.LocalIsVRMode
import com.illuminazionetech.vrclip.ui.common.LocalWindowWidthState
import com.illuminazionetech.vrclip.ui.common.rememberTextClipboard
import com.illuminazionetech.vrclip.ui.component.VRClipModalBottomSheet
import com.illuminazionetech.vrclip.ui.component.rememberHiddenSheetState
import com.illuminazionetech.vrclip.ui.page.downloadv2.configure.Config
import com.illuminazionetech.vrclip.ui.page.downloadv2.configure.DownloadDialog
import com.illuminazionetech.vrclip.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.illuminazionetech.vrclip.ui.page.downloadv2.configure.DownloadDialogViewModel.Action
import com.illuminazionetech.vrclip.ui.page.downloadv2.configure.FormatPage
import com.illuminazionetech.vrclip.ui.page.downloadv2.configure.PlaylistSelectionPage
import com.illuminazionetech.vrclip.util.DownloadUtil
import com.illuminazionetech.vrclip.util.FileUtil
import com.illuminazionetech.vrclip.util.StorageUtil
import com.illuminazionetech.vrclip.util.YtDlpEngine
import com.illuminazionetech.vrclip.util.getErrorReport
import com.illuminazionetech.vrclip.util.makeToast
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

enum class Filter {
    All,
    Downloading,
    Canceled,
    Finished;

    @Composable
    @ReadOnlyComposable
    fun label(): String =
        when (this) {
            All -> stringResource(R.string.all)
            Downloading -> stringResource(R.string.status_downloading)
            Canceled -> stringResource(R.string.filter_stopped)
            Finished -> stringResource(R.string.status_completed)
        }

    fun predict(entry: Pair<Task, Task.State>): Boolean =
        when (this) {
            All -> true
            Downloading -> entry.second.downloadState.isActive()
            Canceled ->
                entry.second.downloadState.let {
                    it is Error || it is Task.DownloadState.Canceled
                }
            Finished -> entry.second.downloadState is Completed
        }
}

private fun Task.DownloadState.isActive(): Boolean =
    this is FetchingInfo || this == Idle || this == ReadyWithInfo || this is Running

sealed interface UiAction {
    data class OpenFile(val filePath: String?) : UiAction

    data class ShareFile(val filePath: String?) : UiAction

    data class OpenThumbnailURL(val url: String) : UiAction

    data object CopyVideoURL : UiAction

    data class OpenVideoURL(val url: String) : UiAction

    data object Cancel : UiAction

    data object Delete : UiAction

    data object Resume : UiAction

    data class CopyErrorReport(val throwable: Throwable) : UiAction
}

@Composable
fun DownloadPageV2(
    modifier: Modifier = Modifier,
    dialogViewModel: DownloadDialogViewModel,
    downloader: DownloaderV2 = koinInject(),
) {
    val view = LocalView.current
    val context = LocalContext.current
    val clipboard = rememberTextClipboard()
    val uriHandler = LocalUriHandler.current

    DownloadPageImplV2(
        modifier = modifier,
        taskDownloadStateMap = downloader.getTaskStateMap(),
        onNewDownload = {
            view.slightHapticFeedback()
            dialogViewModel.postAction(Action.ShowSheet())
        },
    ) { task, action ->
        view.slightHapticFeedback()
        when (action) {
            UiAction.Cancel -> downloader.cancel(task)
            UiAction.Delete -> downloader.remove(task)
            UiAction.Resume -> downloader.restart(task)
            is UiAction.CopyErrorReport -> {
                clipboard.setText(getErrorReport(action.throwable, task.url))
                context.makeToast(R.string.error_copied)
            }
            UiAction.CopyVideoURL -> {
                clipboard.setText(task.url)
                context.makeToast(R.string.link_copied)
            }
            is UiAction.OpenFile -> {
                action.filePath?.let {
                    FileUtil.openFile(path = it) { context.makeToast(R.string.file_unavailable) }
                }
            }
            is UiAction.OpenThumbnailURL -> uriHandler.openUri(action.url)
            is UiAction.OpenVideoURL -> uriHandler.openUri(action.url)
            is UiAction.ShareFile -> {
                val shareTitle = App.context.getString(R.string.share)
                FileUtil.createIntentForSharingFile(action.filePath)?.let {
                    context.startActivity(Intent.createChooser(it, shareTitle))
                }
            }
        }
    }

    var preferences by remember {
        mutableStateOf(DownloadUtil.DownloadPreferences.createFromPreferences())
    }
    val sheetValue by dialogViewModel.sheetValueFlow.collectAsStateWithLifecycle()
    val state by dialogViewModel.sheetStateFlow.collectAsStateWithLifecycle()
    val selectionState = dialogViewModel.selectionStateFlow.collectAsStateWithLifecycle().value

    var showDialog by remember { mutableStateOf(false) }
    val sheetState = rememberHiddenSheetState()

    LaunchedEffect(sheetValue) {
        if (sheetValue == DownloadDialogViewModel.SheetValue.Expanded) {
            showDialog = true
        } else {
            launch { sheetState.hide() }.invokeOnCompletion { showDialog = false }
        }
    }

    if (showDialog) {
        DownloadDialog(
            state = state,
            sheetState = sheetState,
            config = Config(),
            preferences = preferences,
            onPreferencesUpdate = { preferences = it },
            onActionPost = { dialogViewModel.postAction(it) },
        )
    }
    when (selectionState) {
        is DownloadDialogViewModel.SelectionState.FormatSelection ->
            FormatPage(
                state = selectionState,
                onDismissRequest = { dialogViewModel.postAction(Action.Reset) },
            )

        is DownloadDialogViewModel.SelectionState.PlaylistSelection ->
            PlaylistSelectionPage(
                state = selectionState,
                onDismissRequest = { dialogViewModel.postAction(Action.Reset) },
            )

        DownloadDialogViewModel.SelectionState.Idle -> {}
    }
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
 * The download queue: every task with its live state, filterable, as a grid of cards or a
 * compact list. The "new download" FAB is shown here on phones; on rails the navigation rail
 * carries that action instead.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadPageImplV2(
    modifier: Modifier = Modifier,
    taskDownloadStateMap: SnapshotStateMap<Task, Task.State>,
    onNewDownload: () -> Unit = {},
    showFab: Boolean =
        LocalWindowWidthState.current == WindowWidthSizeClass.Compact && !LocalIsVRMode.current,
    onActionPost: (Task, UiAction) -> Unit,
) {
    var activeFilter by rememberSaveable { mutableStateOf(Filter.All) }
    var isGridView by rememberSaveable { mutableStateOf(true) }
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val allEntries by remember {
        derivedStateOf {
            taskDownloadStateMap.entries
                .map { it.key to it.value }
                .sortedWith(
                    compareBy<Pair<Task, Task.State>> { it.second.downloadState }
                        .thenByDescending { it.first.timeCreated }
                )
        }
    }
    val visibleEntries by remember {
        derivedStateOf { allEntries.filter { activeFilter.predict(it) } }
    }
    val counts by remember {
        derivedStateOf { Filter.entries.associateWith { f -> allEntries.count { f.predict(it) } } }
    }

    val sheetState = rememberHiddenSheetState(skipPartiallyExpanded = false)
    var selectedTask by remember { mutableStateOf<Task?>(null) }

    fun showActionSheet(task: Task) {
        view.slightHapticFeedback()
        selectedTask = task
    }

    // A task removed while its sheet is open (deleted from the sheet itself, or from a
    // notification) must close the sheet instead of leaving it pointing at nothing.
    LaunchedEffect(selectedTask, taskDownloadStateMap.size) {
        if (selectedTask != null && !taskDownloadStateMap.contains(selectedTask)) {
            selectedTask = null
        }
    }

    val gridState = rememberLazyGridState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val fabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            QueueTopBar(
                active = counts[Filter.Downloading] ?: 0,
                completed = counts[Filter.Finished] ?: 0,
                stopped = counts[Filter.Canceled] ?: 0,
                isGridView = isGridView,
                onToggleView = {
                    view.slightHapticFeedback()
                    isGridView = !isGridView
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (showFab && allEntries.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.new_download)) },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    expanded = fabExpanded,
                    onClick = onNewDownload,
                )
            }
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            columns = if (isGridView) GridCells.Adaptive(280.dp) else GridCells.Adaptive(520.dp),
            contentPadding =
                innerPadding + PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isGridView) 16.dp else 8.dp),
        ) {
            item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                FilterRow(
                    activeFilter = activeFilter,
                    counts = counts,
                    onSelect = { filter ->
                        view.slightHapticFeedback()
                        if (filter == activeFilter) {
                            scope.launch { gridState.animateScrollToItem(0) }
                        } else {
                            activeFilter = filter
                        }
                    },
                )
            }
            item(key = "storage", span = { GridItemSpan(maxLineSpan) }) { StorageAccessBanner() }
            item(key = "engine", span = { GridItemSpan(maxLineSpan) }) { EngineStatusBanner() }

            if (visibleEntries.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    QueueEmptyState(
                        modifier = Modifier.animateItem(),
                        filtered = activeFilter != Filter.All && allEntries.isNotEmpty(),
                        onNewDownload = onNewDownload,
                        onShowAll = { activeFilter = Filter.All },
                    )
                }
            }

            items(items = visibleEntries, key = { (task, _) -> task.id }) { (task, state) ->
                val itemModifier = Modifier.animateItem()
                if (isGridView) {
                    VideoCardV2(
                        modifier = itemModifier,
                        viewState = state.viewState,
                        downloadState = state.downloadState,
                        actionButton = {
                            ActionButton(downloadState = state.downloadState) {
                                onActionPost(task, it)
                            }
                        },
                        stateIndicator = { CardStateIndicator(downloadState = state.downloadState) },
                        onClick = { showActionSheet(task) },
                    )
                } else {
                    VideoListItem(
                        modifier = itemModifier,
                        viewState = state.viewState,
                        stateIndicator = { ListItemStateText(downloadState = state.downloadState) },
                        onButtonClick = { showActionSheet(task) },
                    )
                }
            }
        }
    }

    selectedTask?.let { task ->
        val taskState = taskDownloadStateMap[task] ?: return@let
        LaunchedEffect(task) { sheetState.show() }
        VRClipModalBottomSheet(
            sheetState = sheetState,
            contentPadding = PaddingValues(),
            onDismissRequest = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { selectedTask = null }
            },
        ) {
            SheetContent(
                task = task,
                downloadState = taskState.downloadState,
                viewState = taskState.viewState,
                onDismissRequest = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { selectedTask = null }
                },
                onActionPost = onActionPost,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QueueTopBar(
    active: Int,
    completed: Int,
    stopped: Int,
    isGridView: Boolean,
    onToggleView: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val parts = buildList {
        if (active > 0) add(pluralStringResource(R.plurals.queue_active, active, active))
        if (completed > 0) add(pluralStringResource(R.plurals.queue_completed, completed, completed))
        if (stopped > 0) add(pluralStringResource(R.plurals.queue_stopped, stopped, stopped))
    }
    val subtitle = parts.joinToString(" · ").ifEmpty { stringResource(R.string.queue_idle) }

    MediumFlexibleTopAppBar(
        title = { Text(stringResource(R.string.download_queue), maxLines = 1) },
        subtitle = {
            AnimatedContent(
                targetState = subtitle,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "queueSubtitle",
            ) {
                Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        actions = {
            IconButton(onClick = onToggleView) {
                AnimatedContent(
                    targetState = isGridView,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "viewToggle",
                ) { grid ->
                    Icon(
                        imageVector =
                            if (grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                        contentDescription =
                            stringResource(
                                if (grid) R.string.switch_to_list_view
                                else R.string.switch_to_grid_view
                            ),
                    )
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        scrollBehavior = scrollBehavior,
    )
}

/** Connected toggle buttons (Material 3 Expressive button group) for the queue filters. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilterRow(activeFilter: Filter, counts: Map<Filter, Int>, onSelect: (Filter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        val filters = Filter.entries
        filters.forEachIndexed { index, filter ->
            val count = counts[filter] ?: 0
            ToggleButton(
                checked = activeFilter == filter,
                onCheckedChange = { onSelect(filter) },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        filters.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                colors =
                    ToggleButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
            ) {
                Text(
                    text = if (count > 0 && filter != Filter.All) "${filter.label()} $count"
                    else filter.label(),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Shown while the app lacks the storage access it needs to save downloads, with a direct link to
 * the system grant screen: without it downloads fail with an opaque yt-dlp error.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun StorageAccessBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(StorageUtil.isStorageAccessGranted(context)) }

    val legacyStoragePermission =
        if (Build.VERSION.SDK_INT < 30) {
            rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE) {
                granted = StorageUtil.isStorageAccessGranted(context)
            }
        } else {
            null
        }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = StorageUtil.isStorageAccessGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    StatusBanner(
        modifier = modifier,
        visible = !granted,
        icon = Icons.Rounded.FolderOff,
        text = stringResource(R.string.storage_access_needed),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        action = {
            TextButton(
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                onClick = {
                    if (Build.VERSION.SDK_INT >= 30) {
                        StorageUtil.launchAllFilesAccessSettings(context)
                    } else {
                        legacyStoragePermission?.launchPermissionRequest()
                    }
                }
            ) {
                Text(stringResource(R.string.grant_access))
            }
        },
    )
}

/**
 * Shown while the yt-dlp engine is initializing or updating, so the first download after install
 * does not look stuck. Hidden as soon as the engine is ready.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EngineStatusBanner(modifier: Modifier = Modifier) {
    val engineState by YtDlpEngine.state.collectAsStateWithLifecycle()
    val failed = engineState is YtDlpEngine.State.InitFailed
    StatusBanner(
        modifier = modifier,
        visible = engineState !is YtDlpEngine.State.Ready,
        icon = if (failed) Icons.Rounded.ErrorOutline else null,
        text =
            stringResource(
                when {
                    failed -> R.string.engine_init_failed
                    engineState is YtDlpEngine.State.Updating -> R.string.engine_updating
                    else -> R.string.engine_preparing
                }
            ),
        containerColor =
            if (failed) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        contentColor =
            if (failed) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
        leading =
            if (failed) null
            else {
                {
                    LoadingIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            },
    )
}

@Composable
private fun StatusBanner(
    visible: Boolean,
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Surface(
            modifier = modifier.fillMaxWidth().padding(top = 8.dp),
            color = containerColor,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp).height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    leading != null -> leading()
                    icon != null -> Icon(icon, contentDescription = null, Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                action?.invoke()
            }
        }
    }
}

@Composable
private fun QueueEmptyState(
    filtered: Boolean,
    onNewDownload: () -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(144.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = MaterialShapes.Cookie9Sided.toShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {}
            Icon(
                imageVector = if (filtered) Icons.Rounded.Inbox else Icons.Rounded.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text =
                stringResource(
                    if (filtered) R.string.queue_filter_empty
                    else R.string.you_ll_find_your_downloads_here
                ),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            text =
                stringResource(if (filtered) R.string.queue_filter_empty_desc else R.string.download_hint),
            modifier = Modifier.padding(top = 8.dp).padding(horizontal = 32.dp).widthIn(max = 420.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        if (filtered) {
            TextButton(onClick = onShowAll) { Text(stringResource(R.string.show_all)) }
        } else {
            Button(onClick = onNewDownload) {
                Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.new_download))
            }
        }
    }
}
