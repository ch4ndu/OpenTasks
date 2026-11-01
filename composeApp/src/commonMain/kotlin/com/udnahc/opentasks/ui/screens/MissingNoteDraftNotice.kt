package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.discard
import opentasks.composeapp.generated.resources.keep_editing
import opentasks.composeapp.generated.resources.note_discard_message
import opentasks.composeapp.generated.resources.note_discard_title
import opentasks.composeapp.generated.resources.note_missing_draft_message
import opentasks.composeapp.generated.resources.note_save_as_new
import org.jetbrains.compose.resources.stringResource

/** A restored missing editor needs only its saved identity to remount its own saveable draft. */
internal fun noteForOpenEditor(
    noteId: String?,
    observed: Note?,
    retained: Note?,
    isMissing: Boolean,
): Note? {
    if (noteId == null) return null
    return observed?.takeIf { it.id == noteId }
        ?: retained?.takeIf { it.id == noteId }
        ?: if (isMissing) Note(id = noteId) else null
}

@Composable
internal fun NoteDraftDiscardDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.note_discard_title)) },
        text = { Text(stringResource(Res.string.note_discard_message)) },
        confirmButton = {
            TextButton(onClick = onDiscard, enabled = !isBusy) {
                Text(stringResource(Res.string.discard))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.keep_editing))
            }
        },
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun MissingNoteDraftNotice(
    isBusy: Boolean,
    onSaveAsNew: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(OpenTasksTheme.dimens.paddingLarge)) {
        Text(
            stringResource(Res.string.note_missing_draft_message),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow {
            TextButton(onClick = onSaveAsNew, enabled = !isBusy) {
                Text(stringResource(Res.string.note_save_as_new))
            }
            TextButton(onClick = onDiscard, enabled = !isBusy) {
                Text(stringResource(Res.string.discard))
            }
        }
    }
}
