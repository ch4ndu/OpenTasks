package com.udnahc.opentasks.ui.screens

import com.udnahc.opentasks.testutil.testNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class NoteDraftRestoreTest {
    @Test
    fun restoredMissingNoteMountsTheExistingDraftByItsSavedIdentityWithoutDuplicatingHtml() {
        // App restores the missing flag and editor ID; process-local snapshots are absent.
        val restored = assertNotNull(noteForOpenEditor(
            noteId = "deleted-note",
            observed = null,
            retained = null,
            isMissing = true,
        ))

        assertEquals("deleted-note", restored.id)
        assertEquals("", restored.title)
        assertEquals("", restored.content)
        assertNull(restored.pbId)
    }

    @Test
    fun liveObservedAndLocallyOwnedDeleteSnapshotsKeepTheOriginalEditorData() {
        val opened = testNote(id = "note", title = "Original", content = "<p>Body</p>")
        assertSame(opened, noteForOpenEditor("note", null, opened, isMissing = false))
        assertSame(opened, noteForOpenEditor("note", null, opened, isMissing = true))
        val current = opened.copy(title = "Updated")
        assertSame(current, noteForOpenEditor("note", current, opened, isMissing = false))
    }

    @Test
    fun initialLoadingAndRetiredRequestsCannotReuseAnotherEditorsSnapshot() {
        val previous = testNote(id = "previous-note")
        assertNull(noteForOpenEditor("next-note", previous, previous, isMissing = false))
        assertNull(noteForOpenEditor(null, previous, previous, isMissing = true))
        assertEquals("next-note", noteForOpenEditor("next-note", previous, previous, isMissing = true)?.id)
    }
}
