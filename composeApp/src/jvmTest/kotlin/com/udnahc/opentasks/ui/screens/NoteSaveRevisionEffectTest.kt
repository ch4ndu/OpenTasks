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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NoteSaveRevisionEffectTest {
    @Test
    fun failedOrPendingLaterSaveStaysDirtyAfterRestoreUntilANewSuccess() = runTest {
        val saved = DraftComposition(this).use { composition ->
            val draft = assertNotNull(composition.render(savedRevision = 0L))
            draft.attemptSave("A title", "<p>A body</p>")
            composition.render(savedRevision = 1L)
            draft.assertSaved("A title", "<p>A body</p>")

            // A failed or still-pending write has no new successful revision.
            draft.attemptSave("B title", "<p><strong>B body</strong></p>")
            composition.render(savedRevision = 1L)
            draft.assertDirty("A title", "<p>A body</p>")
            assertEquals(listOf(1L), composition.appliedRevisions)
            composition.save()
        }

        DraftComposition(this, saved).use { restored ->
            assertNull(restored.render(savedRevision = 1L, loading = true))
            assertTrue(restored.appliedRevisions.isEmpty())
            val draft = assertNotNull(restored.render(savedRevision = 1L))
            assertEquals("B title", draft.title.value)
            assertEquals("<p><strong>B body</strong></p>", draft.html.value)
            draft.assertDirty("A title", "<p>A body</p>")
            assertTrue(restored.appliedRevisions.isEmpty())

            restored.render(savedRevision = 2L)
            draft.assertSaved("B title", "<p><strong>B body</strong></p>")
            assertEquals(listOf(2L), restored.appliedRevisions)
        }
    }

    @Test
    fun zeroSuccessRevisionNeverPromotesAnAttemptedDraftOnRestore() = runTest {
        val saved = DraftComposition(this).use { composition ->
            val draft = assertNotNull(composition.render(savedRevision = 0L))
            draft.attemptSave("Unsaved title", "<p>Unsaved body</p>")
            composition.save()
        }

        DraftComposition(this, saved).use { restored ->
            val draft = assertNotNull(restored.render(savedRevision = 0L))
            draft.assertDirty("Original title", "<p>Original body</p>")
            assertTrue(restored.appliedRevisions.isEmpty())

            restored.render(savedRevision = 1L)
            draft.assertSaved("Unsaved title", "<p>Unsaved body</p>")
            assertEquals(listOf(1L), restored.appliedRevisions)
        }
    }

    @Test
    fun replacementRequestCanApplyItsOwnFirstSuccess() = runTest {
        DraftComposition(this).use { composition ->
            val first = assertNotNull(composition.render(requestToken = 7L, savedRevision = 0L))
            first.attemptSave("A title", "<p>A body</p>")
            composition.render(requestToken = 7L, savedRevision = 1L)
            first.assertSaved("A title", "<p>A body</p>")

            val replacement = assertNotNull(composition.render(requestToken = 8L, savedRevision = 0L))
            replacement.assertSaved("Original title", "<p>Original body</p>")
            replacement.attemptSave("B title", "<p>B body</p>")
            composition.render(requestToken = 8L, savedRevision = 1L)
            replacement.assertSaved("B title", "<p>B body</p>")
            assertEquals(listOf(1L, 1L), composition.appliedRevisions)
        }
    }

    @Test
    fun replacementDuringRestoredLoadingCannotInheritTheOldAppliedRevision() = runTest {
        val saved = DraftComposition(this).use { composition ->
            val first = assertNotNull(composition.render(requestToken = 7L, savedRevision = 0L))
            first.attemptSave("A title", "<p>A body</p>")
            composition.render(requestToken = 7L, savedRevision = 1L)
            composition.save()
        }

        DraftComposition(this, saved).use { restored ->
            assertNull(restored.render(requestToken = 7L, savedRevision = 1L, loading = true))
            val replacement = assertNotNull(restored.render(requestToken = 8L, savedRevision = 0L))
            replacement.assertSaved("Original title", "<p>Original body</p>")
            replacement.attemptSave("B title", "<p>B body</p>")
            restored.render(requestToken = 8L, savedRevision = 1L)
            replacement.assertSaved("B title", "<p>B body</p>")
            assertEquals(listOf(1L), restored.appliedRevisions)
        }
    }

    private data class Draft(
        val title: MutableState<String>,
        val html: MutableState<String>,
        val baselineTitle: MutableState<String>,
        val baselineHtml: MutableState<String>,
        val pendingTitle: MutableState<String>,
        val pendingHtml: MutableState<String>,
        val changedSinceSave: MutableState<Boolean>,
    ) {
        fun attemptSave(title: String, html: String) {
            this.title.value = title
            this.html.value = html
            pendingTitle.value = title
            pendingHtml.value = html
            changedSinceSave.value = true
        }

        fun assertSaved(title: String, html: String) {
            assertEquals(title, this.title.value)
            assertEquals(html, this.html.value)
            assertEquals(title, baselineTitle.value)
            assertEquals(html, baselineHtml.value)
            assertFalse(changedSinceSave.value)
        }

        fun assertDirty(savedTitle: String, savedHtml: String) {
            assertEquals(savedTitle, baselineTitle.value)
            assertEquals(savedHtml, baselineHtml.value)
            assertTrue(changedSinceSave.value)
            assertTrue(title.value != baselineTitle.value || html.value != baselineHtml.value)
        }
    }

    /** Real registry/effect execution; explicit content updates need no native window or frame loop. */
    private class DraftComposition(
        private val scope: TestScope,
        restoredValues: Map<String, List<Any?>>? = null,
    ) : AutoCloseable {
        private val registry = SaveableStateRegistry(restoredValues) { true }
        private val recomposer = Recomposer(scope.coroutineContext)
        private val composition = Composition(NoOpApplier(), recomposer)
        private var draft: Draft? = null
        val appliedRevisions = mutableListOf<Long>()

        fun render(requestToken: Long = 7L, savedRevision: Long, loading: Boolean = false): Draft? {
            draft = null
            composition.setContent {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    NoteEditorRequestScope(requestToken) {
                        if (!loading) {
                            val title = rememberSaveable(requestToken) { mutableStateOf("Original title") }
                            val html = rememberSaveable(requestToken) { mutableStateOf("<p>Original body</p>") }
                            val baselineTitle = rememberSaveable(requestToken) { mutableStateOf(title.value) }
                            val baselineHtml = rememberSaveable(requestToken) { mutableStateOf(html.value) }
                            val pendingTitle = rememberSaveable(requestToken) { mutableStateOf(title.value) }
                            val pendingHtml = rememberSaveable(requestToken) { mutableStateOf(html.value) }
                            val changedSinceSave = rememberSaveable(requestToken) { mutableStateOf(false) }
                            NoteSaveRevisionEffect(requestToken, savedRevision) {
                                baselineTitle.value = pendingTitle.value
                                baselineHtml.value = pendingHtml.value
                                changedSinceSave.value = false
                                appliedRevisions += savedRevision
                            }
                            SideEffect {
                                draft = Draft(title, html, baselineTitle, baselineHtml, pendingTitle, pendingHtml, changedSinceSave)
                            }
                        }
                    }
                }
            }
            scope.runCurrent()
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
}
