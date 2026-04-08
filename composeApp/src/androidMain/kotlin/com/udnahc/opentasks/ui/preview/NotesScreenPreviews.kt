package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.ui.screens.NoteCard
import com.udnahc.opentasks.ui.screens.NotesContent
import com.udnahc.opentasks.ui.screens.previewNotes
import com.udnahc.opentasks.ui.theme.OpenTasksTheme

@Composable
@LightDarkPreview
private fun NotesContentPreview() {
    OpenTasksTheme {
        NotesContent(
            notes = previewNotes,
            onNoteClick = {},
            onSettingsClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun NotesContentEmptyPreview() {
    OpenTasksTheme {
        NotesContent(
            notes = emptyList(),
            onNoteClick = {},
            onSettingsClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun NoteCardPreview() {
    OpenTasksTheme {
        NoteCard(
            note = previewNotes.first(),
            onClick = {},
        )
    }
}
