package com.illuminazionetech.vrclip.ui.page.command

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.ui.res.pluralStringResource
import com.illuminazionetech.vrclip.ui.common.rememberTextClipboard
import com.illuminazionetech.vrclip.ui.component.VRClipModalBottomSheet
import com.illuminazionetech.vrclip.ui.component.rememberHiddenSheetState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NewLabel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illuminazionetech.vrclip.download.CommandTaskManager
import com.illuminazionetech.vrclip.R
import com.illuminazionetech.vrclip.database.objects.CommandTemplate
import com.illuminazionetech.vrclip.ui.common.HapticFeedback.slightHapticFeedback
import com.illuminazionetech.vrclip.ui.common.intState
import com.illuminazionetech.vrclip.ui.component.BackButton
import com.illuminazionetech.vrclip.ui.component.ClearButton
import com.illuminazionetech.vrclip.ui.component.CustomCommandTaskItem
import com.illuminazionetech.vrclip.ui.component.DismissButton
import com.illuminazionetech.vrclip.ui.component.FilledButtonWithIcon
import com.illuminazionetech.vrclip.ui.component.OutlinedButtonChip
import com.illuminazionetech.vrclip.ui.component.OutlinedButtonWithIcon
import com.illuminazionetech.vrclip.ui.component.PasteFromClipBoardButton
import com.illuminazionetech.vrclip.ui.component.VRClipDialog
import com.illuminazionetech.vrclip.ui.component.TaskStatus
import com.illuminazionetech.vrclip.ui.page.settings.command.CommandTemplateDialog
import com.illuminazionetech.vrclip.util.PreferenceUtil
import com.illuminazionetech.vrclip.util.PreferenceUtil.updateInt
import com.illuminazionetech.vrclip.util.TEMPLATE_ID
import com.illuminazionetech.vrclip.util.findURLsFromString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Custom yt-dlp commands run from user templates, with their live output. Same page anatomy as
 * the queue and the library: flexible top bar with a summary, empty state, primary action FAB.
 */
@Composable
fun TaskListPage(onNavigateToDetail: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val clipboard = rememberTextClipboard()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberHiddenSheetState()
    val tasks = CommandTaskManager.mutableTaskList.values.toList().sortedBy { it.state.toStatus() }
    val running = tasks.count { it.state is CommandTaskManager.CustomCommandTask.State.Running }

    val openSheet: () -> Unit = {
        view.slightHapticFeedback()
        showBottomSheet = true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.nav_commands), maxLines = 1) },
                subtitle = {
                    Text(
                        text =
                            if (tasks.isEmpty()) stringResource(R.string.commands_subtitle)
                            else pluralStringResource(R.plurals.queue_active, running, running),
                        maxLines = 1,
                    )
                },
                scrollBehavior = scrollBehavior,
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
        floatingActionButton = {
            if (tasks.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = openSheet,
                    icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_task)) },
                )
            }
        },
    ) { paddings ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddings.calculateTopPadding() + 4.dp,
                    bottom = paddings.calculateBottomPadding() + 104.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (tasks.isEmpty()) {
                item(key = "empty") { CommandsEmptyState(onRunCommand = openSheet) }
            }
            items(tasks, key = { it.toKey() }) {
                it.run {
                    CustomCommandTaskItem(
                        status = state.toStatus(),
                        progress =
                            if (state is CommandTaskManager.CustomCommandTask.State.Running)
                                state.progress / 100f
                            else 0f,
                        progressText = currentLine,
                        url = url,
                        templateName = template.name,
                        onCancel = { onCancel() },
                        onCopyError = { onCopyError(clipboard::setText) },
                        onRestart = { onRestart() },
                        onCopyLog = { onCopyLog(clipboard::setText) },
                        onShowLog = { onNavigateToDetail(hashCode()) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    val onDismissRequest: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { showBottomSheet = false }
    }

    if (showBottomSheet) {
        LaunchedEffect(Unit) { sheetState.show() }
        VRClipModalBottomSheet(sheetState = sheetState, onDismissRequest = onDismissRequest) {
            var showTemplateSelectionDialog by remember { mutableStateOf(false) }
            var showTemplateCreatorDialog by remember { mutableStateOf(false) }
            var showTemplateEditorDialog by remember { mutableStateOf(false) }

            val template by
                remember(
                    showTemplateCreatorDialog,
                    showTemplateSelectionDialog,
                    showTemplateEditorDialog,
                ) {
                    mutableStateOf(PreferenceUtil.getTemplate())
                }

            var url by remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                url =
                    findURLsFromString(clipboard.getText().orEmpty(), false)
                        .joinToString(separator = "\n")
            }

            Column(Modifier.fillMaxWidth()) {
                TaskCreatorDialogContent(
                    url = url,
                    onValueChange = { url = it },
                    template = template,
                    onTemplateSelectionClicked = { showTemplateSelectionDialog = true },
                    onNewTemplateClicked = { showTemplateCreatorDialog = true },
                    onEditClicked = { showTemplateEditorDialog = true },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    OutlinedButtonWithIcon(
                        onClick = onDismissRequest,
                        icon = Icons.Rounded.Cancel,
                        text = stringResource(R.string.cancel),
                    )
                    FilledButtonWithIcon(
                        onClick = {
                            view.slightHapticFeedback()
                            CommandTaskManager.executeCommandWithUrl(url)
                            onDismissRequest()
                        },
                        icon = Icons.Rounded.DownloadDone,
                        text = stringResource(R.string.start),
                        enabled = url.isNotBlank(),
                    )
                }
            }
            if (showTemplateSelectionDialog) {
                TemplatePickerDialog() { showTemplateSelectionDialog = false }
            }
            if (showTemplateCreatorDialog) {
                CommandTemplateDialog(
                    onDismissRequest = { showTemplateCreatorDialog = false },
                    confirmationCallback = { scope.launch { TEMPLATE_ID.updateInt(it) } },
                )
            }
            if (showTemplateEditorDialog) {
                CommandTemplateDialog(
                    commandTemplate = template,
                    onDismissRequest = { showTemplateEditorDialog = false },
                )
            }
        }
    }
}

@Composable
private fun CommandsEmptyState(onRunCommand: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 56.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(144.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = MaterialShapes.SoftBurst.toShape(),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {}
            Icon(
                imageVector = Icons.Rounded.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.commands_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            text = stringResource(R.string.custom_command_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).padding(horizontal = 32.dp).widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRunCommand) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.new_task))
        }
    }
}

private fun CommandTaskManager.CustomCommandTask.State.toStatus(): TaskStatus =
    when (this) {
        CommandTaskManager.CustomCommandTask.State.Canceled -> TaskStatus.CANCELED
        CommandTaskManager.CustomCommandTask.State.Completed -> TaskStatus.FINISHED
        is CommandTaskManager.CustomCommandTask.State.Error -> TaskStatus.ERROR
        is CommandTaskManager.CustomCommandTask.State.Running -> TaskStatus.RUNNING
    }

@Composable
fun ColumnScope.TaskCreatorDialogContent(
    url: String,
    onValueChange: (String) -> Unit = {},
    template: CommandTemplate,
    onTemplateSelectionClicked: () -> Unit = {},
    onNewTemplateClicked: () -> Unit = {},
    onEditClicked: () -> Unit = {},
) {
    Icon(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        imageVector = Icons.Rounded.Add,
        contentDescription = null,
    )
    Text(
        text = stringResource(id = R.string.new_task),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.custom_command_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp),
    )

    OutlinedTextField(
        value = url,
        onValueChange = onValueChange,
        label = { Text(text = stringResource(id = R.string.video_url)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 3,
        trailingIcon = {
            if (url.isNotEmpty()) {
                ClearButton { onValueChange("") }
            } else {
                PasteFromClipBoardButton(onPaste = onValueChange)
            }
        },
        textStyle = LocalTextStyle.current.merge(fontFamily = FontFamily.Monospace),
    )

    LazyRow(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedButtonChip(
                icon = Icons.Rounded.Code,
                label = template.name,
                onClick = onTemplateSelectionClicked,
            )
        }
        item {
            OutlinedButtonChip(
                icon = Icons.Rounded.Edit,
                label = stringResource(id = R.string.edit_template, template.name),
                onClick = onEditClicked,
            )
        }
        item {
            OutlinedButtonChip(
                icon = Icons.Rounded.NewLabel,
                label = stringResource(id = R.string.new_template),
                onClick = onNewTemplateClicked,
            )
        }
    }
}

@Composable
fun TemplatePickerDialog(onDismissRequest: () -> Unit = {}) {
    val templateList by PreferenceUtil.templateListStateFlow.collectAsStateWithLifecycle()
    var selectedId by TEMPLATE_ID.intState
    val scrollState =
        rememberLazyListState(
            initialFirstVisibleItemIndex =
                templateList
                    .indexOfFirst { it.id == selectedId }
                    .run { if (this == -1) 0 else this }
        )

    VRClipDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { DismissButton(onClick = onDismissRequest) },
        title = { Text(text = stringResource(id = R.string.template_selection)) },
        icon = { Icon(imageVector = Icons.Rounded.Code, contentDescription = null) },
        text = {
            Box(modifier = Modifier.heightIn(max = 450.dp)) {
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.align(Alignment.TopCenter)
                )
                LazyColumn(state = scrollState) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    items(templateList) {
                        TemplateSingleChoiceItem(
                            text = it.name,
                            supportingText = it.template,
                            selected = it.id == selectedId,
                        ) {
                            selectedId = it.id
                            TEMPLATE_ID.updateInt(it.id)
                            onDismissRequest()
                        }
                    }
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                }
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        },
    )
}

@Composable
fun TemplateSingleChoiceItem(
    modifier: Modifier = Modifier,
    text: String,
    supportingText: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .selectable(selected = selected, enabled = true, onClick = onClick)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        RadioButton(
            modifier = Modifier.padding(end = 8.dp).clearAndSetSemantics {},
            selected = selected,
            onClick = onClick,
        )
        Column {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supportingText.replace("\n", " "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
