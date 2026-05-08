package com.udnahc.opentasks.ui.screens

import org.lighthousegames.logging.logging
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
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

private val log = logging("CreateNoteBottomSheet")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNoteBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    editNote: Note? = null,
    onSave: (title: String, content: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        CreateNoteBottomSheetContent(
            editNote = editNote,
            onDismiss = onDismiss,
            onSave = onSave,
            onDelete = onDelete,
        )
    }
}

@Composable
internal fun CreateNoteBottomSheetContent(
    editNote: Note? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val stateKey = editNote?.id ?: 0L
    var title by remember(stateKey) { mutableStateOf(editNote?.title ?: "") }
    val richTextState = rememberRichTextState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(stateKey) {
        if (editNote != null) {
            log.d { "Loading note content: length=${editNote.content.length}, has newlines=${'\n' in editNote.content}" }
            log.v { "Raw content: ${editNote.content.take(200)}" }
            richTextState.setHtml(editNote.content)
        }
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.delete_note_title)) },
            text = { Text(stringResource(Res.string.delete_note_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                    onDismiss()
                }) {
                    Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
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
            onBack = onDismiss,
            onSave = {
                val content = richTextState.toHtml()
                log.d { "Saving note content: length=${content.length}, has newlines=${'\n' in content}" }
                if (title.isNotBlank() || content.isNotBlank()) {
                    onSave(title, content)
                }
                onDismiss()
            },
            onDelete = if (editNote != null) {
                { showDeleteConfirm = true }
            } else {
                null
            },
        )

        val dimens = OpenTasksTheme.dimens

        // Title field
        BasicTextField(
            value = title,
            onValueChange = { title = it },
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
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun CreateNoteTopBar(
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val dimens = OpenTasksTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.paddingSmall, vertical = dimens.paddingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
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

        Text(
            text = stringResource(Res.string.save),
            style = MaterialTheme.typography.labelLarge,
            color = PrimaryBlue,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(onClick = onSave)
                .padding(horizontal = dimens.paddingMedium, vertical = dimens.paddingSmall),
        )

        if (onDelete != null) {
            IconButton(onClick = onDelete) {
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


