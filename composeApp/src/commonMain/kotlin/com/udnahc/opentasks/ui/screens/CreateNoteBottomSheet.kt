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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.theme.minimumInteractiveTargetSize
import com.udnahc.opentasks.viewmodel.NoteMutationOperation
import com.udnahc.opentasks.viewmodel.NoteMutationState
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
    onSave: (title: String, content: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val isBusy = mutationState.isOwnedBy(requestToken, editNote?.id)
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = { if (!isBusy) onDismiss() },
        dragHandle = null,
        sheetGesturesEnabled = !isBusy,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        CreateNoteBottomSheetContent(
            editNote = editNote,
            onDismiss = onDismiss,
            onSave = onSave,
            onDelete = onDelete,
            isBusy = isBusy,
        )
    }
}

@Composable
internal fun CreateNoteBottomSheetContent(
    editNote: Note? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    isBusy: Boolean = false,
) {
    val stateKey = editNote?.id ?: CREATE_NOTE_STATE_KEY
    var title by rememberSaveable(stateKey) { mutableStateOf(editNote?.title ?: "") }
    val richTextState = rememberSaveable(stateKey, saver = RichTextState.Saver) {
        RichTextState().apply {
            val initialContent = editNote?.content.orEmpty()
            if (initialContent.isNotBlank()) setHtml(initialContent)
        }
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm && onDelete != null) {
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
                    onSave(title, content)
                } else {
                    onDismiss()
                }
            },
            onDelete = if (editNote != null) {
                { showDeleteConfirm = true }
            } else {
                null
            },
            isBusy = isBusy,
        )

        val dimens = OpenTasksTheme.dimens

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
            enabled = !isBusy,
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
    if (operation.requestToken != requestToken) return false
    return when (operation) {
        is NoteMutationOperation.Create -> editNoteId == null
        is NoteMutationOperation.Update -> operation.noteId == editNoteId
        is NoteMutationOperation.Delete -> operation.note.id == editNoteId
    }
}

private const val CREATE_NOTE_STATE_KEY = "create-note"
