package com.udnahc.opentasks.viewmodel

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.udnahc.opentasks.domain.action.note.AddNoteAction
import com.udnahc.opentasks.domain.action.note.DeleteNoteAction
import com.udnahc.opentasks.domain.action.note.UpdateNoteAction
import com.udnahc.opentasks.domain.time.DateTimeTextFormatter
import com.udnahc.opentasks.domain.time.EnglishDateTimeFormatter
import com.udnahc.opentasks.domain.time.LocalDaySignal
import com.udnahc.opentasks.domain.usecase.note.ObserveAllNotesUseCase
import com.udnahc.opentasks.domain.usecase.note.ObserveNoteByIdUseCase
import com.udnahc.opentasks.testutil.FakeNoteRepository
import com.udnahc.opentasks.testutil.testNote
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class NoteListProjectionTest : MainDispatcherRule() {
    @Test
    fun metadataAndTitleChangesReuseDisplayTextButPublishCurrentEntities() {
        val formatter = CountingFormatter()
        val previews = mutableListOf<String>()
        val cache = NoteListProjectionCache(formatter) { content ->
            previews.add(content)
            "preview:$content"
        }
        val first = testNote(id = "first", content = "First body")
        val second = testNote(id = "second", content = "Second body")
        val original = cache.project(listOf(first, second), formatter.formattingContextKey)
        val acknowledged = first.copy(pbId = "remote", isSynced = true)
        val renamed = second.copy(title = "Renamed")

        val current = cache.project(listOf(renamed, acknowledged), formatter.formattingContextKey)

        assertEquals(listOf("First body", "Second body"), previews)
        assertEquals(2, formatter.calls.get())
        assertSame(renamed, current[0].note)
        assertSame(acknowledged, current[1].note)
        assertEquals(original[1].previewText, current[0].previewText)
        assertEquals(original[0].updatedAtText, current[1].updatedAtText)
    }

    @Test
    fun contentAndTimestampInvalidateOnlyTheirOwnDisplayValuesAndNote() {
        val formatter = CountingFormatter()
        val previews = mutableListOf<String>()
        val cache = NoteListProjectionCache(formatter) { content ->
            previews.add(content)
            "preview:$content"
        }
        val first = testNote(id = "first", content = "Original")
        val second = testNote(id = "second", content = "Unchanged")
        val original = cache.project(listOf(first, second), formatter.formattingContextKey)

        val edited = first.copy(content = "Edited")
        val contentChange = cache.project(listOf(edited, second), formatter.formattingContextKey)
        assertEquals(listOf("Original", "Unchanged", "Edited"), previews)
        assertEquals(2, formatter.calls.get())
        assertEquals("preview:Edited", contentChange[0].previewText)
        assertSame(original[1], contentChange[1])

        val dated = edited.copy(updatedAt = 200L)
        val dateChange = cache.project(listOf(dated, second), formatter.formattingContextKey)
        assertEquals(3, previews.size)
        assertEquals(3, formatter.calls.get())
        assertEquals("first:200", dateChange[0].updatedAtText)
        assertSame(original[1], dateChange[1])
    }

    @Test
    fun formattingContextInvalidatesDatesWithoutRebuildingPreviews() {
        val formatter = CountingFormatter()
        var previewCalls = 0
        val cache = NoteListProjectionCache(formatter) { content ->
            previewCalls++
            content
        }
        val notes = listOf(testNote(id = "first"), testNote(id = "undated", updatedAt = 0L))
        cache.project(notes, formatter.formattingContextKey)
        formatter.context.set("second")

        val current = cache.project(notes, formatter.formattingContextKey)

        assertEquals(2, previewCalls)
        assertEquals(2, formatter.calls.get())
        assertEquals("second:100", current[0].updatedAtText)
        assertEquals("", current[1].updatedAtText)
    }

    @Test
    fun removedNotesAreEvictedIncludingWhenTheListBecomesEmpty() {
        val formatter = CountingFormatter()
        var previewCalls = 0
        val cache = NoteListProjectionCache(formatter) { content ->
            previewCalls++
            content
        }
        val first = testNote(id = "first")
        val second = testNote(id = "second")
        cache.project(listOf(first, second), formatter.formattingContextKey)
        cache.project(listOf(second), formatter.formattingContextKey)
        cache.project(listOf(first, second), formatter.formattingContextKey)
        assertEquals(3, previewCalls)
        assertEquals(3, formatter.calls.get())

        assertEquals(emptyList(), cache.project(emptyList(), formatter.formattingContextKey))
        cache.project(listOf(first), formatter.formattingContextKey)
        assertEquals(4, previewCalls)
        assertEquals(4, formatter.calls.get())
    }

    @Test
    fun viewModelReusesAcknowledgedLabelsAndRefreshesContextWithoutANoteOrDayChange() = runTest(dispatcher) {
        val first = testNote(id = "first", content = "<p><strong>Saved</strong> &amp; readable</p>")
        val second = testNote(id = "second", content = "Unchanged")
        val repository = FakeNoteRepository(listOf(first, second))
        val formatter = CountingFormatter()
        val signal = LocalDaySignal(currentDate = { LocalDate(2026, 10, 30) })
        val viewModel = NoteViewModel(
            observeAllNotes = ObserveAllNotesUseCase(repository),
            observeNoteById = ObserveNoteByIdUseCase(repository),
            addNoteAction = AddNoteAction(repository),
            updateNoteAction = UpdateNoteAction(repository),
            deleteNoteAction = DeleteNoteAction(repository),
            dateTimeFormatter = formatter,
            localDaySignal = signal,
        )

        viewModel.noteListItems.test {
            val initial = awaitMatching { it.size == 2 }
            assertEquals("**Saved** & readable", initial.first().previewText)
            assertEquals(2, formatter.calls.get())

            val acknowledged = first.copy(pbId = "remote", isSynced = true)
            repository.update(acknowledged)
            val current = awaitMatching { items -> items.any { it.note.isSynced } }
            assertSame(acknowledged, current.first { it.note.id == first.id }.note)
            assertEquals(2, formatter.calls.get())

            formatter.context.set("second")
            signal.refresh()
            val reformatted = awaitMatching { items -> items.all { it.updatedAtText == "second:100" } }
            assertEquals(2, reformatted.size)
            assertEquals(4, formatter.calls.get())
            assertEquals("**Saved** & readable", reformatted.first { it.note.id == first.id }.previewText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class CountingFormatter : DateTimeTextFormatter by EnglishDateTimeFormatter {
        val context = AtomicReference("first")
        val calls = AtomicInteger()
        override val formattingContextKey: String get() = context.get()
        override fun formatDateWithYear(localMillis: Long): String {
            calls.incrementAndGet()
            return "${context.get()}:$localMillis"
        }
    }

    private suspend fun <T> ReceiveTurbine<T>.awaitMatching(predicate: (T) -> Boolean): T {
        repeat(20) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
        error("No matching note projection emitted")
    }
}
