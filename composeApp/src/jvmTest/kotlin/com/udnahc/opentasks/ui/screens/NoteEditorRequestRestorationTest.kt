package com.udnahc.opentasks.ui.screens

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import com.mohamedrejeb.richeditor.model.RichTextState
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NoteEditorRequestRestorationTest {
    @Test
    fun discardDuringRestoredLoadingCannotTransferDraftToAnotherNoteOrNewNote() {
        EditorInputs.entries.forEach { inputs ->
            listOf("note-b", null).forEach { nextNoteId ->
                val saved = saveEditedDraft(inputs)
                DraftComposition(saved.values).use { restored ->
                    assertNull(restored.render(inputs, requestToken = 7L, noteId = "note-a", loading = true))
                    // The loading UI retires the request before its draft savers ever mount.
                    assertNull(restored.render(inputs, requestToken = 8L, noteId = null, active = false))
                    val next = assertNotNull(restored.render(
                        inputs,
                        requestToken = 9L,
                        noteId = nextNoteId,
                        title = if (nextNoteId == null) "" else "B title",
                        html = if (nextNoteId == null) "" else "<p>B body</p>",
                    ))

                    assertEquals(if (nextNoteId == null) "" else "B title", next.title.value)
                    assertEquals(normalizedHtml(if (nextNoteId == null) "" else "<p>B body</p>"), next.rich.toHtml())
                    assertEquals(next.title.value, next.baselineTitle.value)
                    assertEquals(next.rich.toHtml(), next.baselineHtml.value)
                }
            }
        }
    }

    @Test
    fun sameRequestRestoresUnsavedFieldsAfterLoadingEvenWhenTheNoteIsMissing() {
        EditorInputs.entries.forEach { inputs ->
            val saved = saveEditedDraft(inputs)
            DraftComposition(saved.values).use { restored ->
                assertNull(restored.render(inputs, requestToken = 7L, noteId = "note-a", loading = true))
                // A missing note remounts the same identity with blank seed fields.
                val draft = assertNotNull(restored.render(inputs, requestToken = 7L, noteId = "note-a"))
                assertEquals(saved.draft, draft.snapshot())
            }
        }
    }

    @Test
    fun reimaginedCreateAndRecoveryKeepDraftStateWhenTheCommittedNoteIdChanges() {
        listOf(null, "deleted-note").forEach { originalNoteId ->
            DraftComposition().use { composition ->
                val draft = assertNotNull(composition.render(
                    EditorInputs.REQUEST_TOKEN,
                    requestToken = 7L,
                    noteId = originalNoteId,
                    title = "Draft title",
                    html = "<p>Draft body</p>",
                ))
                draft.title.value = "Saved title"
                draft.rich.setHtml("<p><strong>Saved body</strong></p>")
                draft.baselineTitle.value = draft.title.value
                draft.baselineHtml.value = draft.rich.toHtml()
                val saved = draft.snapshot()

                val committed = assertNotNull(composition.render(
                    EditorInputs.REQUEST_TOKEN,
                    requestToken = 7L,
                    noteId = "newly-committed-note",
                    title = "Different seed must not replace the draft",
                    html = "<p>Different seed</p>",
                ))
                assertEquals(saved, committed.snapshot())
            }
        }
    }

    private fun saveEditedDraft(inputs: EditorInputs): SavedDraft = DraftComposition().use { composition ->
        val draft = assertNotNull(composition.render(
            inputs,
            requestToken = 7L,
            noteId = "note-a",
            title = "A persisted title",
            html = "<p>A persisted body</p>",
        ))
        draft.title.value = "A unsaved title"
        draft.rich.setHtml("<p><em>A unsaved body</em></p>")
        SavedDraft(composition.save(), draft.snapshot())
    }

    private enum class EditorInputs { NOTE_ID, REQUEST_TOKEN }

    private data class Draft(
        val title: MutableState<String>,
        val rich: RichTextState,
        val baselineTitle: MutableState<String>,
        val baselineHtml: MutableState<String>,
    ) {
        fun snapshot() = DraftSnapshot(title.value, rich.toHtml(), baselineTitle.value, baselineHtml.value)
    }

    private data class DraftSnapshot(val title: String, val html: String, val baselineTitle: String, val baselineHtml: String)
    private data class SavedDraft(val values: Map<String, List<Any?>>, val draft: DraftSnapshot)

    /** Exercises real composition/registry restoration without creating a native window or UI nodes. */
    private class DraftComposition(restoredValues: Map<String, List<Any?>>? = null) : AutoCloseable {
        private val registry = SaveableStateRegistry(restoredValues) { true }
        private val recomposer = Recomposer(EmptyCoroutineContext)
        private val composition = Composition(NoOpApplier(), recomposer)
        private var draft: Draft? = null

        fun render(
            inputs: EditorInputs,
            requestToken: Long,
            noteId: String?,
            title: String = "",
            html: String = "",
            loading: Boolean = false,
            active: Boolean = true,
        ): Draft? {
            if (loading || !active) draft = null
            composition.setContent {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    if (active) NoteEditorRequestScope(requestToken) {
                        if (!loading) {
                            val stateInput = when (inputs) {
                                EditorInputs.NOTE_ID -> noteId ?: "create-note"
                                EditorInputs.REQUEST_TOKEN -> requestToken
                            }
                            val currentTitle = rememberSaveable(stateInput) { mutableStateOf(title) }
                            val rich = rememberSaveable(stateInput, saver = RichTextState.Saver) {
                                RichTextState().apply { if (html.isNotBlank()) setHtml(html) }
                            }
                            val baselineTitle = rememberSaveable(stateInput) { mutableStateOf(title) }
                            val baselineHtml = rememberSaveable(stateInput) { mutableStateOf(normalizedHtml(html)) }
                            SideEffect { draft = Draft(currentTitle, rich, baselineTitle, baselineHtml) }
                        }
                    }
                }
            }
            return draft
        }

        fun save(): Map<String, List<Any?>> = registry.performSave()

        override fun close() {
            composition.dispose()
            recomposer.cancel()
        }
    }

    private class NoOpApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun onClear() = Unit
    }

    private companion object {
        fun normalizedHtml(html: String): String = RichTextState().apply {
            if (html.isNotBlank()) setHtml(html)
        }.toHtml()
    }
}
