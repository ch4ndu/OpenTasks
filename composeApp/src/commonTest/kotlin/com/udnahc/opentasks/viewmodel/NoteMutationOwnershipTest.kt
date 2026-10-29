package com.udnahc.opentasks.viewmodel

import com.udnahc.opentasks.testutil.testNote
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteMutationOwnershipTest {
    @Test
    fun recoveredCreationBelongsOnlyToTheOriginalEditorRequest() {
        val recovery = NoteMutationOperation.Create(requestToken = 8L, recoveryNoteId = "deleted-note")

        assertTrue(recovery.matchesEditor(requestToken = 8L, noteId = "deleted-note"))
        assertFalse(recovery.matchesEditor(requestToken = 9L, noteId = "deleted-note"))
        assertFalse(recovery.matchesEditor(requestToken = 8L, noteId = "another-note"))
        assertFalse(recovery.matchesEditor(requestToken = 8L, noteId = null))
    }

    @Test
    fun ordinaryCreateUpdateAndLocallyOwnedDeleteRetainTheirEditorOwnership() {
        val create = NoteMutationOperation.Create(requestToken = 8L)
        assertTrue(create.matchesEditor(requestToken = 8L, noteId = null))
        assertFalse(create.matchesEditor(requestToken = 8L, noteId = "existing-note"))

        val update = NoteMutationOperation.Update(requestToken = 8L, noteId = "existing-note")
        val delete = NoteMutationOperation.Delete(requestToken = 8L, note = testNote(id = "existing-note"))
        listOf(update, delete).forEach { operation ->
            assertTrue(operation.matchesEditor(requestToken = 8L, noteId = "existing-note"))
            assertFalse(operation.matchesEditor(requestToken = 9L, noteId = "existing-note"))
            assertFalse(operation.matchesEditor(requestToken = 8L, noteId = "another-note"))
            assertFalse(operation.matchesEditor(requestToken = 8L, noteId = null))
        }
    }
}
