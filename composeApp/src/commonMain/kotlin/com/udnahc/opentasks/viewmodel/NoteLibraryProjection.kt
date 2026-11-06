package com.udnahc.opentasks.viewmodel

import com.udnahc.opentasks.data.extensions.localMillisToLocalDate
import com.udnahc.opentasks.data.model.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

enum class NoteLibraryView { ACTIVE, PINNED, ARCHIVE, RECENT, UNTAGGED, UNFINISHED_TASKS }
data class NoteLibraryFilter(val view: NoteLibraryView = NoteLibraryView.ACTIVE, val tagId: String? = null)
internal data class NoteLibrarySource(
    val items: List<NoteListItem> = emptyList(),
    val unfinishedNoteIds: Set<String> = emptySet(),
    val activeTagIds: Set<String> = emptySet(),
)

internal fun noteLibrarySource(items: List<NoteListItem>, tasks: List<Task>, tags: List<Tag>): NoteLibrarySource {
    val counts = tasks.filterNot { it.isDeleted }.mapNotNull { it.sourceNoteId }.groupingBy { it }.eachCount()
    val tagsById = tags.filterNot { it.isDeleted }.associateBy { it.id }
    return NoteLibrarySource(
        items.filterNot { it.note.isDeleted }.map { item -> item.copy(
            linkedTaskCount = counts[item.note.id] ?: 0,
            tagNames = item.note.tagIds.mapNotNull { tagsById[it]?.name }.joinToString(", "),
        ) },
        tasks.filter { !it.isDeleted && it.status != TaskStatus.DONE }.mapNotNull { it.sourceNoteId }.toSet(),
        tagsById.keys,
    )
}

internal fun filterNoteLibrary(source: NoteLibrarySource, query: String, filter: NoteLibraryFilter, today: LocalDate): List<NoteListItem> {
    val needle = query.trim()
    val recentStart = today.minus(6, DateTimeUnit.DAY)
    return source.items.asSequence().filter { item ->
        val note = item.note
        val inView = when (filter.view) {
            NoteLibraryView.ARCHIVE -> note.isArchived
            NoteLibraryView.ACTIVE -> !note.isArchived
            NoteLibraryView.PINNED -> !note.isArchived && note.isPinned
            NoteLibraryView.RECENT -> !note.isArchived && note.updatedAt != 0L &&
                localMillisToLocalDate(note.updatedAt) in recentStart..today
            NoteLibraryView.UNTAGGED -> !note.isArchived && note.tagIds.none { it in source.activeTagIds }
            NoteLibraryView.UNFINISHED_TASKS -> !note.isArchived && note.id in source.unfinishedNoteIds
        }
        inView && (filter.tagId == null || filter.tagId in note.tagIds && filter.tagId in source.activeTagIds) &&
            (needle.isEmpty() || note.title.contains(needle, true) || item.searchText.contains(needle, true))
    }.map { item ->
        val match = if (needle.isEmpty()) -1 else item.searchText.indexOf(needle, ignoreCase = true)
        if (match < 0) item else {
            val start = (match - 40).coerceAtLeast(0)
            item.copy(previewText = (if (start > 0) "…" else "") + item.searchText.substring(start).take(maxOf(160, needle.length + 40)))
        }
    }.sortedWith(compareByDescending<NoteListItem> { it.note.isPinned }
        .thenByDescending { it.note.updatedAt }.thenBy { it.note.id }).toList()
}

/** Readable search text is independent of the abbreviated, Markdown-style card preview. */
internal fun noteSearchText(html: String): String = html
    .replace(Regex("<(script|style)\\b[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
    .replace(Regex("<br\\s*/?>|</(?:p|div|li|h[1-6])\\s*>", RegexOption.IGNORE_CASE), " ")
    .replace(Regex("<[^>]*>"), "")
    .replace(Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos|nbsp);")) { match ->
        when (val entity = match.groupValues[1]) {
            "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
            else -> {
                val point = if (entity.startsWith("#x")) entity.drop(2).toIntOrNull(16) else entity.drop(1).toIntOrNull()
                when {
                    point == null || point !in 0..0x10ffff || point in 0xd800..0xdfff -> match.value
                    point <= 0xffff -> point.toChar().toString()
                    else -> { val n = point - 0x10000; "${(0xd800 + (n shr 10)).toChar()}${(0xdc00 + (n and 0x3ff)).toChar()}" }
                }
            }
        }
    }.replace(Regex("\\s+"), " ").trim()

data class NoteEditorContext(val noteId: String? = null, val organization: NoteOrganization = NoteOrganization())
data class NoteReference(val id: String, val title: String, val note: Note?)
data class NoteRelations(
    val context: NoteEditorContext? = null,
    val linked: List<NoteReference> = emptyList(),
    val available: List<NoteReference> = emptyList(),
    val backlinks: List<NoteReference> = emptyList(),
)
internal fun projectNoteRelations(items: List<NoteListItem>, context: NoteEditorContext): NoteRelations {
    val notes = items.map { it.note }.filterNot { it.isDeleted }.associateBy { it.id }
    fun Note.reference() = NoteReference(id, title, this)
    return NoteRelations(
        context = context,
        linked = context.organization.linkedNoteIds.map { id -> notes[id]?.reference() ?: NoteReference(id, "", null) },
        available = notes.values.filter { it.id != context.noteId && it.id !in context.organization.linkedNoteIds }.sortedBy { it.title.lowercase() }.map { it.reference() },
        backlinks = notes.values.filter { context.noteId != null && context.noteId in it.linkedNoteIds }.sortedBy { it.title.lowercase() }.map { it.reference() },
    )
}
