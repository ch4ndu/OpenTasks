package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.StateFlow
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.model.NoteOrganization
import com.udnahc.opentasks.data.model.organization
import com.udnahc.opentasks.viewmodel.NoteViewModel
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.ui.util.PlatformBackHandler
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.WindowSizeCategory
import com.udnahc.opentasks.viewmodel.NoteListItem
import opentasks.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ReimaginedNotesLibrary(
    notes: List<NoteListItem>, query: String, onQuery: (String) -> Unit,
    onNote: (Note) -> Unit, onCreate: (String) -> Unit, onSettings: () -> Unit,
    isRefreshing: Boolean = false, syncEnabled: Boolean = true, onRefresh: () -> Unit = {},
    controls: @Composable () -> Unit = {},
) {
    val d = OpenTasksTheme.dimens
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var capture by rememberSaveable { mutableStateOf("") }
    PlatformBackHandler(enabled = searchOpen, onBack = { searchOpen = false; onQuery("") })
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = d.fabAreaBottom)) {
        OpenTasksTopBar(
            titleContent = {
                if (searchOpen) OutlinedTextField(query, onQuery, singleLine = true,
                    placeholder = { Text(stringResource(Res.string.notes_search_action)) },
                    modifier = Modifier.fillMaxWidth().heightIn(max = TopAppBarDefaults.TopAppBarExpandedHeight))
            },
            navigationIcon = { if (searchOpen) OpenTasksBackButton(onClick = { searchOpen = false; onQuery("") }) },
            actions = {
                if (!searchOpen) {
                    IconButton(onClick = { searchOpen = true }) { Icon(painterResource(Res.drawable.ic_search), contentDescription = stringResource(Res.string.notes_search), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    ReimaginedAddButton(onClick = { onCreate("") }, label = Res.string.note_new)
                    OpenTasksSettingsButton(onClick = onSettings)
                }
            },
        )
        controls()
        SyncPullToRefresh(isRefreshing = isRefreshing, enabled = syncEnabled, onRefresh = onRefresh, modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize()) {
                if (!searchOpen) {
                    Row(Modifier.padding(horizontal = d.paddingXLarge), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(capture, { capture = it }, placeholder = { Text(stringResource(Res.string.note_capture_hint)) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(d.cornerXLarge))
                        TextButton(onClick = { onCreate(capture); capture = "" }, enabled = capture.isNotBlank()) { Text(stringResource(Res.string.note_capture)) }
                    }
                }
                if (notes.isEmpty()) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(if (query.isBlank()) Res.string.empty_notes else Res.string.notes_no_matches))
                        TextButton(onClick = { if (query.isBlank()) onCreate("") else onQuery("") }) { Text(stringResource(if (query.isBlank()) Res.string.note_new else Res.string.clear_filter)) }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(d.noteCardMinWidth),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(d.paddingLarge),
                    ) {
                        items(notes, key = { it.note.id }) { item -> NoteCard(item) { onNote(item.note) } }
                    }
                }
            }
        }
    }
}

/** Draft ownership is above every width/focus branch and keyed only by the App request token. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ReimaginedNotesWorkspace(
    active: Boolean,
    loading: Boolean,
    requestToken: Long,
    note: Note?,
    initialCapture: String,
    savedRevision: Long,
    isBusy: Boolean,
    isMissing: Boolean,
    errorMessage: String?,
    library: StateFlow<List<NoteListItem>>,
    linkedTasks: StateFlow<List<Task>>,
    linkedTaskPreview: StateFlow<List<Task>>,
    onSelectNote: (Note) -> Unit,
    onClose: () -> Unit,
    onSaveAsNew: (String, String, NoteOrganization) -> Unit,
    onSave: (String, String, NoteOrganization) -> Unit,
    onSaveAndOpenTasks: ((String, String, NoteOrganization) -> Unit)?,
    onDelete: (() -> Unit)?,
    onLinkedTaskComplete: (Task) -> Unit,
    onBackHandler: (Any, (() -> Unit)?) -> Unit,
    navigation: @Composable (onLeave: (() -> Unit) -> Unit) -> Unit,
    organizationModel: NoteViewModel? = null,
) {
    if (!active) return
    val backOwner = remember { Any() }
    if (loading) {
        // The saved draft cannot be inspected until its lookup completes; exiting still needs consent.
        var confirmLoadingDiscard by rememberSaveable(requestToken) { mutableStateOf(false) }
        val loadingBack by rememberUpdatedState<() -> Unit> { if (!isBusy) confirmLoadingDiscard = true }
        PlatformBackHandler(enabled = true, onBack = { loadingBack() })
        DisposableEffect(backOwner) {
            onBackHandler(backOwner) { loadingBack() }
            onDispose { onBackHandler(backOwner, null) }
        }
        // Surface blocks the screen behind it without consuming its children's gestures.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                OpenTasksTopBar(titleContent = {}, navigationIcon = { OpenTasksBackButton(onClick = { loadingBack() }) })
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(stringResource(Res.string.loading), modifier = Modifier.padding(OpenTasksTheme.dimens.paddingLarge))
                    }
                }
            }
        }
        if (confirmLoadingDiscard) {
            NoteDraftDiscardDialog(
                isBusy = isBusy,
                onDismiss = { confirmLoadingDiscard = false },
                onDiscard = onClose,
            )
        }
        return
    }
    var title by rememberSaveable(requestToken) { mutableStateOf(note?.title.orEmpty()) }
    val rich = rememberSaveable(requestToken, saver = RichTextState.Saver) {
        RichTextState().apply {
            val html = note?.content.orEmpty()
            if (html.isNotBlank()) setHtml(html) else if (initialCapture.isNotBlank()) setText(initialCapture)
        }
    }
    var organization by rememberSaveable(requestToken, stateSaver = NoteOrganizationSaver) { mutableStateOf(note?.organization() ?: NoteOrganization()) }
    var baselineOrganization by rememberSaveable(requestToken, stateSaver = NoteOrganizationSaver) { mutableStateOf(organization) }
    var pendingOrganization by rememberSaveable(requestToken, stateSaver = NoteOrganizationSaver) { mutableStateOf(organization) }
    var baselineTitle by rememberSaveable(requestToken) { mutableStateOf(note?.title.orEmpty()) }
    var baselineHtml by rememberSaveable(requestToken) {
        mutableStateOf(RichTextState().apply { note?.content?.takeIf { it.isNotBlank() }?.let { setHtml(it) } }.toHtml())
    }
    var pendingTitle by rememberSaveable(requestToken) { mutableStateOf(title) }
    var pendingHtml by rememberSaveable(requestToken) { mutableStateOf(baselineHtml) }
    var changedSinceSave by rememberSaveable(requestToken) { mutableStateOf(false) }
    LaunchedEffect(requestToken, rich) {
        snapshotFlow { rich.annotatedString }.drop(1).collect { changedSinceSave = true }
    }
    var focus by rememberSaveable(requestToken) { mutableStateOf(false) }
    var pendingSwitch by remember(requestToken) { mutableStateOf<(() -> Unit)?>(null) }
    var confirmDelete by remember(requestToken) { mutableStateOf(false) }
    NoteSaveRevisionEffect(requestToken, savedRevision) {
        baselineTitle = pendingTitle; baselineHtml = pendingHtml; baselineOrganization = pendingOrganization; changedSinceSave = false
    }
    fun requestLeave(action: () -> Unit) {
        if (isBusy) return
        if (isMissing || title != baselineTitle || rich.toHtml() != baselineHtml || organization != baselineOrganization) pendingSwitch = action else action()
    }
    PlatformBackHandler(enabled = true, onBack = { requestLeave(onClose) })
    val currentBack by rememberUpdatedState<() -> Unit> { requestLeave(onClose) }
    DisposableEffect(active, backOwner) {
        onBackHandler(backOwner, if (active) ({ currentBack() }) else null)
        onDispose { onBackHandler(backOwner, null) }
    }
    val d = OpenTasksTheme.dimens
    val wide = OpenTasksTheme.windowSizeCategory != WindowSizeCategory.COMPACT
    // Surface blocks the screen behind it without consuming its children's gestures.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()
            .padding(start = if (wide && !focus) d.reimaginedRailWidth else 0.dp, bottom = if (!wide && !focus) d.fabAreaBottom else 0.dp)
            .statusBarsPadding().navigationBarsPadding().imePadding()) {
            if (OpenTasksTheme.windowSizeCategory == WindowSizeCategory.EXPANDED && !focus) {
                val libraryItems by library.collectAsState()
                LazyColumn(Modifier.width(d.libraryColumnWidth).fillMaxHeight().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(d.paddingMedium)) {
                    item { Text(stringResource(Res.string.notes), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(d.paddingLarge)) }
                    items(libraryItems, key = { it.note.id }) { item ->
                        NoteCard(item) { if (item.note.id != note?.id) requestLeave { onSelectNote(item.note) } }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = if (focus && wide) d.paddingXXLarge else d.paddingMedium)) {
                OpenTasksTopBar(
                    titleContent = {},
                    navigationIcon = { OpenTasksBackButton(onClick = { requestLeave(onClose) }) },
                    windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal),
                    actions = {
                        IconButton(onClick = { focus = !focus }) { Icon(painterResource(Res.drawable.ic_unfold), stringResource(if (focus) Res.string.note_focus_exit else Res.string.note_focus), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (!isMissing) TextButton(onClick = {
                            pendingTitle = title; pendingHtml = rich.toHtml(); pendingOrganization = organization; onSave(title, pendingHtml, organization)
                        }, enabled = !isBusy) { Text(stringResource(Res.string.save)) }
                        if (onDelete != null && !isMissing) IconButton(onClick = { confirmDelete = true }, enabled = !isBusy) { Icon(painterResource(Res.drawable.ic_delete), stringResource(Res.string.delete_note), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    },
                )
                if (errorMessage != null) Text(errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(d.paddingMedium))
                if (isMissing) MissingNoteDraftNotice(
                    isBusy = isBusy,
                    onSaveAsNew = { pendingTitle = title; pendingHtml = rich.toHtml(); pendingOrganization = organization; onSaveAsNew(title, pendingHtml, organization) },
                    onDiscard = onClose,
                )
                Text(stringResource(if (isMissing || changedSinceSave || note == null && savedRevision == 0L) Res.string.note_draft else Res.string.note_saved), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = d.paddingLarge))
                if (isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                BasicTextField(
                    title, { title = it; changedSinceSave = true }, readOnly = isBusy,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(d.paddingLarge),
                    decorationBox = { inner -> if (title.isEmpty()) Text(stringResource(Res.string.note_title_hint), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); inner() },
                )
                if (!focus && organizationModel != null) NoteOrganizationControls(
                    model = organizationModel, noteId = note?.id, value = organization,
                    onChange = { organization = it; changedSinceSave = true }, enabled = !isBusy,
                    onOpenNote = { target -> requestLeave { onSelectNote(target) } },
                )
                HorizontalDivider()
                BasicRichTextEditor(
                    state = rich, readOnly = isBusy,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(d.paddingXLarge),
                )
                FormattingToolbar(richTextState = rich, enabled = !isBusy, modifier = Modifier.fillMaxWidth())
                if (!focus && onSaveAndOpenTasks != null && !isMissing) {
                    val linkedTaskCount = linkedTasks.collectAsState().value.size
                    val linkedPreview by linkedTaskPreview.collectAsState()
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.note_linked_count, linkedTaskCount), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { pendingTitle = title; pendingHtml = rich.toHtml(); pendingOrganization = organization; onSaveAndOpenTasks(title, pendingHtml, organization) }, enabled = !isBusy) { Text(stringResource(Res.string.note_save_open_tasks)) }
                    }
                    linkedPreview.forEach { task ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = task.status == TaskStatus.DONE, onCheckedChange = { onLinkedTaskComplete(task) }, enabled = !isBusy)
                            Text(task.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        }
                    }
                }
            }
        }
        if (!focus) Box(Modifier.align(if (wide) Alignment.CenterStart else Alignment.BottomCenter)) {
            navigation { action -> requestLeave { onClose(); action() } }
        }
        }
    }
    if (pendingSwitch != null) NoteDraftDiscardDialog(
        isBusy = isBusy,
        onDismiss = { pendingSwitch = null },
        onDiscard = { val next = pendingSwitch; pendingSwitch = null; next?.invoke() },
    )
    if (confirmDelete && onDelete != null && !isMissing) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(Res.string.delete_note_title)) }, text = { Text(stringResource(Res.string.delete_note_message)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }, enabled = !isBusy) { Text(stringResource(Res.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.cancel)) } },
    )
}
