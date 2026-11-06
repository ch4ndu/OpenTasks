package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.model.NoteOrganization
import com.udnahc.opentasks.data.model.organization
import com.udnahc.opentasks.viewmodel.NoteViewModel
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.minimumInteractiveTargetSize
import com.udnahc.opentasks.ui.util.PlatformBackHandler
import com.udnahc.opentasks.viewmodel.NoteMutationState
import com.udnahc.opentasks.viewmodel.matchesEditor
import opentasks.composeapp.generated.resources.note_save_open_tasks
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.back
import opentasks.composeapp.generated.resources.cancel
import opentasks.composeapp.generated.resources.delete
import opentasks.composeapp.generated.resources.delete_note
import opentasks.composeapp.generated.resources.delete_note_message
import opentasks.composeapp.generated.resources.delete_note_title
import opentasks.composeapp.generated.resources.ic_arrow_back
import opentasks.composeapp.generated.resources.ic_delete
import opentasks.composeapp.generated.resources.note
import opentasks.composeapp.generated.resources.note_title_hint
import opentasks.composeapp.generated.resources.loading
import opentasks.composeapp.generated.resources.save
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNoteBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    editNote: Note? = null,
    requestToken: Long = 0L,
    mutationState: NoteMutationState = NoteMutationState.Idle,
    errorMessage: String? = null,
    isMissing: Boolean = false,
    onSaveAsNew: ((String, String, NoteOrganization) -> Unit)? = null,
    onSave: (title: String, content: String, organization: NoteOrganization) -> Unit,
    onDelete: (() -> Unit)? = null,
    onSaveAndOpenTasks: ((String, String, NoteOrganization) -> Unit)? = null,
    organizationModel: NoteViewModel? = null,
    onOpenNote: (Note) -> Unit = {},
) {
    val isBusy = mutationState.isOwnedBy(requestToken, editNote?.id)
    var confirmMissingDiscard by rememberSaveable(requestToken) { mutableStateOf(false) }
    fun requestDismiss() {
        if (isBusy) return
        if (isMissing) confirmMissingDiscard = true else onDismiss()
    }
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = ::requestDismiss,
        dragHandle = null,
        sheetGesturesEnabled = !isBusy && !isMissing,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = !isBusy && !isMissing,
            shouldDismissOnClickOutside = !isBusy && !isMissing,
        ),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        PlatformBackHandler(enabled = isMissing || isBusy, onBack = ::requestDismiss)
        CreateNoteBottomSheetContent(
            editNote = editNote,
            organizationModel = organizationModel,
            onOpenNote = onOpenNote,
            onDismiss = ::requestDismiss,
            onSave = onSave,
            onDelete = onDelete,
            onSaveAndOpenTasks = onSaveAndOpenTasks,
            isBusy = isBusy,
            errorMessage = errorMessage,
            isMissing = isMissing,
            onSaveAsNew = onSaveAsNew,
            onDiscardMissingDraft = onDismiss,
        )
    }
    if (confirmMissingDiscard && isMissing) {
        NoteDraftDiscardDialog(
            isBusy = isBusy,
            onDismiss = { confirmMissingDiscard = false },
            onDiscard = onDismiss,
        )
    }
}

/** Owns input until a lookup can remount the saved draft without seeding an empty editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LoadingNoteBottomSheet(
    sheetState: SheetState,
    requestToken: Long,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onBackHandler: (Any, (() -> Unit)?) -> Unit,
) {
    var confirmDiscard by rememberSaveable(requestToken) { mutableStateOf(false) }
    fun requestDismiss() {
        if (!isBusy) confirmDiscard = true
    }
    val backOwner = remember { Any() }
    val currentBack by rememberUpdatedState<() -> Unit> { requestDismiss() }
    DisposableEffect(backOwner) {
        onBackHandler(backOwner) { currentBack() }
        onDispose { onBackHandler(backOwner, null) }
    }
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = ::requestDismiss,
        dragHandle = null,
        sheetGesturesEnabled = false,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false,
            shouldDismissOnClickOutside = false,
        ),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        PlatformBackHandler(enabled = true, onBack = ::requestDismiss)
        Column(Modifier.fillMaxSize()) {
            CreateNoteTopBar(
                onBack = ::requestDismiss,
                onSave = {},
                onDelete = null,
                isBusy = isBusy,
                canSave = false,
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(stringResource(Res.string.loading), modifier = Modifier.padding(OpenTasksTheme.dimens.paddingLarge))
                }
            }
        }
    }
    if (confirmDiscard) {
        NoteDraftDiscardDialog(
            isBusy = isBusy,
            onDismiss = { confirmDiscard = false },
            onDiscard = onDismiss,
        )
    }
}

@Composable
internal fun CreateNoteBottomSheetContent(
    editNote: Note? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, organization: NoteOrganization) -> Unit,
    onDelete: (() -> Unit)? = null,
    onSaveAndOpenTasks: ((String, String, NoteOrganization) -> Unit)? = null,
    isBusy: Boolean = false,
    errorMessage: String? = null,
    isMissing: Boolean = false,
    onSaveAsNew: ((String, String, NoteOrganization) -> Unit)? = null,
    onDiscardMissingDraft: () -> Unit = onDismiss,
    organizationModel: NoteViewModel? = null,
    onOpenNote: (Note) -> Unit = {},
) {
    val stateKey = editNote?.id ?: CREATE_NOTE_STATE_KEY
    var title by rememberSaveable(stateKey) { mutableStateOf(editNote?.title ?: "") }
    val richTextState = rememberSaveable(stateKey, saver = RichTextState.Saver) {
        RichTextState().apply {
            val initialContent = editNote?.content.orEmpty()
            if (initialContent.isNotBlank()) setHtml(initialContent)
        }
    }
    var organization by rememberSaveable(stateKey, stateSaver = NoteOrganizationSaver) { mutableStateOf(editNote?.organization() ?: NoteOrganization()) }
    val initialOrganization = rememberSaveable(stateKey, saver = NoteOrganizationSaver) { editNote?.organization() ?: NoteOrganization() }
    val initialTitle = rememberSaveable(stateKey) { editNote?.title.orEmpty() }
    val initialHtml = rememberSaveable(stateKey) { richTextState.toHtml() }
    var pendingOpen by remember { mutableStateOf<Note?>(null) }
    if (pendingOpen != null) NoteDraftDiscardDialog(
        isBusy = isBusy, onDismiss = { pendingOpen = null },
        onDiscard = { pendingOpen?.let(onOpenNote); pendingOpen = null },
    )
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm && onDelete != null && !isMissing) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.delete_note_title)) },
            text = { Text(stringResource(Res.string.delete_note_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }, enabled = !isBusy) {
                    Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    enabled = !isBusy,
                ) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        CreateNoteTopBar(
            onBack = { if (!isBusy) onDismiss() },
            onSave = {
                val content = richTextState.toHtml()
                if (title.isNotBlank() || content.isNotBlank()) {
                    onSave(title, content, organization)
                } else {
                    onDismiss()
                }
            },
            onDelete = if (onDelete != null && !isMissing) {
                { showDeleteConfirm = true }
            } else {
                null
            },
            isBusy = isBusy,
            canSave = !isMissing,
        )

        val dimens = OpenTasksTheme.dimens

        if (errorMessage != null) {
            Text(
                errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(dimens.paddingLarge),
            )
        }
        if (isMissing && onSaveAsNew != null) {
            MissingNoteDraftNotice(
                isBusy = isBusy,
                onSaveAsNew = { onSaveAsNew(title, richTextState.toHtml(), organization) },
                onDiscard = onDiscardMissingDraft,
            )
        }
        if (onSaveAndOpenTasks != null && !isMissing) {
            TextButton(onClick = { onSaveAndOpenTasks(title, richTextState.toHtml(), organization) }, enabled = !isBusy) {
                Text(stringResource(Res.string.note_save_open_tasks))
            }
        }
        if (organizationModel != null) NoteOrganizationControls(
            model = organizationModel, noteId = editNote?.id, value = organization,
            onChange = { organization = it }, enabled = !isBusy,
            onOpenNote = { target ->
                if (isMissing || title != initialTitle || richTextState.toHtml() != initialHtml || organization != initialOrganization) pendingOpen = target
                else onOpenNote(target)
            },
        )
        // Title field
        BasicTextField(
            value = title,
            onValueChange = { title = it },
            readOnly = isBusy,
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            ),
            cursorBrush = SolidColor(PrimaryBlue),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.padding(
                        horizontal = dimens.paddingLarge,
                        vertical = dimens.paddingMedium,
                    ),
                ) {
                    if (title.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.note_title_hint),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        // Rich text editor
        BasicRichTextEditor(
            state = richTextState,
            readOnly = isBusy,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(PrimaryBlue),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(
                    horizontal = dimens.paddingLarge,
                    vertical = dimens.paddingMedium,
                ),
        )

        // Markdown formatting toolbar
        FormattingToolbar(
            richTextState = richTextState,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun CreateNoteTopBar(
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    isBusy: Boolean = false,
    canSave: Boolean = true,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.paddingSmall, vertical = dimens.paddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = !isBusy) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_back),
                contentDescription = stringResource(Res.string.back),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(dimens.iconXLarge),
            )
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = stringResource(Res.string.note),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.weight(1f))

        TextButton(
            onClick = onSave,
            enabled = !isBusy && canSave,
            modifier = Modifier.minimumInteractiveTargetSize(),
        ) {
            Text(
                text = stringResource(Res.string.save),
                style = MaterialTheme.typography.labelLarge,
                color = PrimaryBlue,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (onDelete != null) {
            IconButton(onClick = onDelete, enabled = !isBusy) {
                Icon(
                    painter = painterResource(Res.drawable.ic_delete),
                    contentDescription = stringResource(Res.string.delete_note),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(dimens.iconXLarge),
                )
            }
        }
    }
}

private fun NoteMutationState.isOwnedBy(
    requestToken: Long,
    editNoteId: String?,
): Boolean {
    val operation = when (this) {
        is NoteMutationState.Busy -> operation
        is NoteMutationState.Success -> operation
        is NoteMutationState.Error -> operation
        NoteMutationState.Idle -> return false
    }
    return operation.matchesEditor(requestToken, editNoteId)
}

private const val CREATE_NOTE_STATE_KEY = "create-note"
