package com.udnahc.opentasks.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key

/** Saveable inputs do not validate restored values; a retired request needs a distinct position. */
@Composable
internal fun NoteEditorRequestScope(requestToken: Long, content: @Composable () -> Unit) {
    key(requestToken) { content() }
}
