package com.udnahc.opentasks.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** Keep this inside the draft's NoteEditorRequestScope so restored revisions retain their owner. */
@Composable
internal fun NoteSaveRevisionEffect(
    requestToken: Long,
    savedRevision: Long,
    onNewSuccessfulRevision: () -> Unit,
) {
    var lastAppliedRevision by rememberSaveable(requestToken) { mutableStateOf(0L) }
    val applySuccessfulRevision by rememberUpdatedState(onNewSuccessfulRevision)
    LaunchedEffect(requestToken, savedRevision) {
        // A remount must not apply an old success to the pending fields of a later failed save.
        if (savedRevision > lastAppliedRevision) {
            applySuccessfulRevision()
            lastAppliedRevision = savedRevision
        }
    }
}
