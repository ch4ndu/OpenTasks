package com.udnahc.opentasks.viewmodel

import app.cash.turbine.test
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.repository.CommittedMutation
import com.udnahc.opentasks.data.repository.NoteRepository
import com.udnahc.opentasks.domain.action.note.AddNoteAction
import com.udnahc.opentasks.domain.action.note.DeleteNoteAction
import com.udnahc.opentasks.domain.action.note.UpdateNoteAction
import com.udnahc.opentasks.domain.usecase.note.ObserveAllNotesUseCase
import com.udnahc.opentasks.domain.usecase.note.ObserveNoteByIdUseCase
import com.udnahc.opentasks.testutil.FakeNoteRepository
import com.udnahc.opentasks.testutil.testNote
import com.udnahc.opentasks.ui.screens.noteForOpenEditor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NoteSelectionStateTest : MainDispatcherRule() {
    @Test
    fun restoredLiveEditorReceivesCompletedMissingWithoutSeeingALiveRowAgain() = runTest(dispatcher) {
        // The host saved a live editor ID/draft, with its missing flag still false.
        val read = MutableSharedFlow<Note?>(replay = 1)
        val repository = object : NoteRepository by FakeNoteRepository() {
            override fun observeNoteById(id: String) = read
        }
        val viewModel = viewModel(repository)

        viewModel.selectedNoteState.test {
            assertEquals(NoteSelectionState.Unselected, awaitItem())
            viewModel.selectNote("restored")
            assertEquals(NoteSelectionState.Loading("restored"), awaitItem())
            assertNull(noteForOpenEditor("restored", null, null, isMissing = false))

            // The first completed Room observation is absent; it must not equal initial loading.
            read.emit(null)
            val missing = assertIs<NoteSelectionState.Missing>(awaitItem())
            assertEquals("restored", missing.noteId)
            val editorSeed = assertNotNull(noteForOpenEditor(missing.noteId, null, null, isMissing = true))
            assertEquals("restored", editorSeed.id) // Original saveable draft key stays intact.
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun delayedLiveReadDoesNotSeedEmptyEditorAndLaterDeletionIsExplicit() = runTest(dispatcher) {
        val read = MutableSharedFlow<Note?>(replay = 1)
        val repository = object : NoteRepository by FakeNoteRepository() {
            override fun observeNoteById(id: String) = read
        }
        val viewModel = viewModel(repository)
        val note = testNote(id = "live", title = "Saved title", content = "<p>Saved body</p>")

        viewModel.selectedNoteState.test {
            assertEquals(NoteSelectionState.Unselected, awaitItem())
            viewModel.selectNote(note.id)
            assertEquals(NoteSelectionState.Loading(note.id), awaitItem())
            expectNoEvents()
            read.emit(note)
            assertEquals(NoteSelectionState.Ready(note), awaitItem())
            read.emit(null)
            assertEquals(NoteSelectionState.Missing(note.id), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectionSwitchCannotUseThePreviousNotesCompletedRead() = runTest(dispatcher) {
        val firstRead = MutableSharedFlow<Note?>(replay = 1)
        val secondRead = MutableSharedFlow<Note?>(replay = 1)
        val repository = object : NoteRepository by FakeNoteRepository() {
            override fun observeNoteById(id: String) = if (id == "first") firstRead else secondRead
        }
        val viewModel = viewModel(repository)
        val first = testNote(id = "first")
        val second = testNote(id = "second", title = "Second")

        viewModel.selectedNoteState.test {
            assertEquals(NoteSelectionState.Unselected, awaitItem())
            viewModel.selectNote(first.id)
            assertEquals(NoteSelectionState.Loading(first.id), awaitItem())
            firstRead.emit(first)
            assertEquals(NoteSelectionState.Ready(first), awaitItem())
            viewModel.selectNote(second.id)
            assertEquals(NoteSelectionState.Loading(second.id), awaitItem())
            assertNull(noteForOpenEditor(second.id, first, first, isMissing = false))
            firstRead.emit(null)
            secondRead.emit(second)
            assertEquals(NoteSelectionState.Ready(second), awaitItem())
            viewModel.selectNote(null)
            assertEquals(NoteSelectionState.Unselected, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun failedRecoveryCanBeConsumedAndRetriedWithTheSameDraftAndOwnership() = runTest(dispatcher) {
        val storage = FakeNoteRepository()
        val insertOutcomes = Channel<Boolean>()
        val repository = object : NoteRepository by storage {
            override suspend fun insertCommitted(note: Note): CommittedMutation<Note> {
                if (!insertOutcomes.receive()) error("Injected recovery write failure")
                return storage.insertCommitted(note)
            }
        }
        val viewModel = viewModel(repository)
        val requestToken = 7L

        viewModel.mutationState.test {
            assertEquals(NoteMutationState.Idle, awaitItem())
            viewModel.addNote(requestToken, "Draft title", "<p>Draft body</p>", recoveryNoteId = "deleted")
            assertIs<NoteMutationState.Busy>(awaitItem())
            insertOutcomes.send(false)
            val error = assertIs<NoteMutationState.Error>(awaitItem())
            assertTrue(error.operation.matchesEditor(requestToken, "deleted"))
            assertTrue(viewModel.consumeMutationState(error))
            assertEquals(NoteMutationState.Idle, awaitItem())
            assertFalse(viewModel.consumeMutationState(error))

            viewModel.addNote(requestToken, "Draft title", "<p>Draft body</p>", recoveryNoteId = "deleted")
            assertIs<NoteMutationState.Busy>(awaitItem())
            insertOutcomes.send(true)
            val success = assertIs<NoteMutationState.Success>(awaitItem())
            assertTrue(success.operation.matchesEditor(requestToken, "deleted"))
            val recovered = assertNotNull(success.committedNote)
            assertNotEquals("deleted", recovered.id)
            assertEquals("Draft title", recovered.title)
            assertEquals("<p>Draft body</p>", recovered.content)
            assertEquals(listOf(recovered), storage.inserted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun viewModel(repository: NoteRepository) = NoteViewModel(
        observeAllNotes = ObserveAllNotesUseCase(repository),
        observeNoteById = ObserveNoteByIdUseCase(repository),
        addNoteAction = AddNoteAction(repository),
        updateNoteAction = UpdateNoteAction(repository),
        deleteNoteAction = DeleteNoteAction(repository),
    )
}
