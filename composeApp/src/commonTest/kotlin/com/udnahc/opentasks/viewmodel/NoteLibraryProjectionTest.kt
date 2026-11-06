package com.udnahc.opentasks.viewmodel

import com.udnahc.opentasks.data.extensions.computeLocalMillis
import com.udnahc.opentasks.data.model.*
import com.udnahc.opentasks.domain.time.EnglishDateTimeFormatter
import com.udnahc.opentasks.testutil.*
import kotlinx.datetime.LocalDate
import kotlin.test.*

class NoteLibraryProjectionTest {
    private val today = LocalDate(2026, 11, 6)
    private fun items(vararg notes: Note) = NoteListProjectionCache(EnglishDateTimeFormatter).project(notes.toList(), "test")

    @Test
    fun searchFindsTextBeyondPreviewAndAcrossFormattingWithoutMatchingHtmlAttributes() {
        val body = "<p>${"intro ".repeat(60)}Meet <strong>Alex</strong> &amp; Jo &#x1F600;</p><a href='secret'>Reference</a>"
        val rows = items(testNote(id = "long", content = body))
        assertFalse(rows.single().previewText.contains("Alex"))
        val source = noteLibrarySource(rows, emptyList(), emptyList())
        val matches = filterNoteLibrary(source, "meet alex & jo 😀", NoteLibraryFilter(), today)
        assertEquals("long", matches.single().note.id)
        assertContains(matches.single().previewText, "Meet Alex & Jo 😀")
        assertTrue(filterNoteLibrary(source, "secret", NoteLibraryFilter(), today).isEmpty())
        assertEquals("one two three", noteSearchText("<p>one</p><div>two<br>three</div>"))
    }

    @Test
    fun archiveIsExcludedFromActiveViewsAndPinsSortBeforeRecentNotes() {
        val source = noteLibrarySource(items(
            testNote(id = "recent", updatedAt = 300),
            testNote(id = "pinned", updatedAt = 100).copy(isPinned = true),
            testNote(id = "archived", updatedAt = 400).copy(isPinned = true, isArchived = true),
        ), emptyList(), emptyList())
        assertEquals(listOf("pinned", "recent"), filterNoteLibrary(source, "", NoteLibraryFilter(), today).map { it.note.id })
        assertEquals("archived", filterNoteLibrary(source, "", NoteLibraryFilter(NoteLibraryView.ARCHIVE), today).single().note.id)
        assertEquals("pinned", filterNoteLibrary(source, "", NoteLibraryFilter(NoteLibraryView.PINNED), today).single().note.id)
    }

    @Test
    fun filtersCombineTagMembershipWithUnfinishedTasksAndIgnoreDeletedTags() {
        val source = noteLibrarySource(items(
            testNote(id = "a").copy(tagIds = listOf("work")),
            testNote(id = "b").copy(tagIds = listOf("deleted")),
            testNote(id = "c"),
        ), listOf(testTask(id = "open").copy(sourceNoteId = "a"), testTask(id = "done", status = TaskStatus.DONE).copy(sourceNoteId = "b")),
            listOf(testTag(id = "work", name = "Work")))
        assertEquals("Work", source.items.first().tagNames)
        assertEquals("a", filterNoteLibrary(source, "", NoteLibraryFilter(NoteLibraryView.UNFINISHED_TASKS, "work"), today).single().note.id)
        assertEquals(setOf("b", "c"), filterNoteLibrary(source, "", NoteLibraryFilter(NoteLibraryView.UNTAGGED), today).map { it.note.id }.toSet())
        assertTrue(filterNoteLibrary(source, "", NoteLibraryFilter(tagId = "deleted"), today).isEmpty())
    }

    @Test
    fun recentUsesSevenCivilDaysAndExcludesFutureDates() {
        val source = noteLibrarySource(items(
            testNote(id = "boundary", updatedAt = computeLocalMillis(2026, 10, 31)),
            testNote(id = "old", updatedAt = computeLocalMillis(2026, 10, 30)),
            testNote(id = "future", updatedAt = computeLocalMillis(2026, 11, 7)),
        ), emptyList(), emptyList())
        assertEquals("boundary", filterNoteLibrary(source, "", NoteLibraryFilter(NoteLibraryView.RECENT), today).single().note.id)
        assertTrue(filterNoteLibrary(source, "boundary", NoteLibraryFilter(NoteLibraryView.RECENT), LocalDate(2026, 11, 7)).isEmpty())
    }

    @Test
    fun stableLinksFollowRenamesShowBacklinksAndRetainUnavailableTargets() {
        val origin = testNote(id = "origin").copy(linkedNoteIds = listOf("target", "gone"))
        val target = testNote(id = "target", title = "Renamed").copy(isArchived = true)
        val rows = items(origin, target, testNote(id = "gone").copy(isDeleted = true))
        val links = projectNoteRelations(rows, NoteEditorContext(origin.id, origin.organization()))
        assertEquals("Renamed", links.linked.first().title)
        assertNull(links.linked.last().note)
        assertTrue(links.available.isEmpty())
        val backlinks = projectNoteRelations(rows, NoteEditorContext(target.id, target.organization()))
        assertEquals(origin.id, backlinks.backlinks.single().id)
        assertEquals(emptyList(), origin.withOrganization(NoteOrganization(linkedNoteIds = listOf("origin"))).linkedNoteIds)
    }
}
