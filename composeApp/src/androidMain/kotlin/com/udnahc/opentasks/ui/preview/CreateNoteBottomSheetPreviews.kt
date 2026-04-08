package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.udnahc.opentasks.ui.screens.CreateNoteTopBar
import com.udnahc.opentasks.ui.screens.MarkdownToolbar
import com.udnahc.opentasks.ui.theme.OpenTasksTheme

@Composable
@LightDarkPreview
private fun CreateNoteTopBarPreview() {
    OpenTasksTheme {
        CreateNoteTopBar(
            onBack = {},
            onSave = {},
            onDelete = null,
        )
    }
}

@Composable
@LightDarkPreview
private fun CreateNoteTopBarWithDeletePreview() {
    OpenTasksTheme {
        CreateNoteTopBar(
            onBack = {},
            onSave = {},
            onDelete = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun MarkdownToolbarPreview() {
    OpenTasksTheme {
        MarkdownToolbar(
            richTextState = rememberRichTextState(),
        )
    }
}
