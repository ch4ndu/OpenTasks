package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.AccountBoundaryRejectedException
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.data.extensions.extractYear
import com.udnahc.opentasks.data.extensions.formatDateShort
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.domain.action.note.AddNoteAction
import com.udnahc.opentasks.domain.action.note.DeleteNoteAction
import com.udnahc.opentasks.domain.action.note.UpdateNoteAction
import com.udnahc.opentasks.domain.usecase.note.ObserveAllNotesUseCase
import com.udnahc.opentasks.domain.usecase.note.ObserveNoteByIdUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.lighthousegames.logging.logging

private val log = logging("NoteViewModel")

data class NoteListItem(
    val note: Note,
    val previewText: String,
    val updatedAtText: String,
)

class NoteViewModel(
    observeAllNotes: ObserveAllNotesUseCase,
    private val observeNoteById: ObserveNoteByIdUseCase,
    private val addNoteAction: AddNoteAction,
    private val updateNoteAction: UpdateNoteAction,
    private val deleteNoteAction: DeleteNoteAction,
    private val accountBoundaryExecutor: AccountBoundaryExecutor? = null,
) : ViewModel() {

    private val _selectedNoteId = MutableStateFlow<String?>(null)

    val noteListItems: StateFlow<List<NoteListItem>> = observeAllNotes()
        .map { notes -> notes.map { it.toListItem() } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedNote: StateFlow<Note?> = _selectedNoteId
        .flatMapLatest { id -> if (id != null) observeNoteById(id) else flowOf(null) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectNote(noteId: String?) {
        _selectedNoteId.value = noteId
    }

    fun addNote(
        title: String,
        content: String
    ) {
        launchMutation { addNoteAction(title, content) }
    }

    fun updateNote(note: Note) {
        launchMutation { updateNoteAction(note) }
    }

    fun deleteNote(note: Note) {
        launchMutation { deleteNoteAction(note) }
    }

    private fun launchMutation(block: suspend () -> Unit) {
        val expectedBoundary = accountBoundaryExecutor?.captureForegroundBoundary()
        if (accountBoundaryExecutor != null && expectedBoundary == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                accountBoundaryExecutor.withForegroundActionBoundary(expectedBoundary, block)
            } catch (error: CancellationException) {
                throw error
            } catch (_: AccountBoundaryRejectedException) {
                log.w { "Note mutation skipped because the foreground account boundary changed" }
            }
        }
    }
}

private fun Note.toListItem(): NoteListItem =
    NoteListItem(
        note = this,
        previewText = noteContentPreview(content),
        updatedAtText = formatNoteDate(updatedAt),
    )

/** Converts saved rich-text HTML into a compact Markdown-style preview. */
private fun noteContentPreview(content: String): String =
    content.toMarkdownPreview().take(160)

private fun String.toMarkdownPreview(): String =
    this
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<li\\b[^>]*>", RegexOption.IGNORE_CASE), "- ")
        .replace(Regex("</li\\s*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<strong\\b[^>]*>|<b\\b[^>]*>", RegexOption.IGNORE_CASE), "**")
        .replace(Regex("</strong\\s*>|</b\\s*>", RegexOption.IGNORE_CASE), "**")
        .replace(Regex("<em\\b[^>]*>|<i\\b[^>]*>", RegexOption.IGNORE_CASE), "_")
        .replace(Regex("</em\\s*>|</i\\s*>", RegexOption.IGNORE_CASE), "_")
        .replace(Regex("<code\\b[^>]*>", RegexOption.IGNORE_CASE), "`")
        .replace(Regex("</code\\s*>", RegexOption.IGNORE_CASE), "`")
        .replace(Regex("<s\\b[^>]*>|<strike\\b[^>]*>|<del\\b[^>]*>", RegexOption.IGNORE_CASE), "~~")
        .replace(Regex("</s\\s*>|</strike\\s*>|</del\\s*>", RegexOption.IGNORE_CASE), "~~")
        .replace(Regex("<u\\b[^>]*>", RegexOption.IGNORE_CASE), "__")
        .replace(Regex("</u\\s*>", RegexOption.IGNORE_CASE), "__")
        .replace(Regex("<[^>]*>"), "")
        .decodeHtmlEntities()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")

private fun String.decodeHtmlEntities(): String =
    replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

private fun formatNoteDate(localMillis: Long): String {
    if (localMillis == 0L) return ""
    val y = extractYear(localMillis)
    return "${formatDateShort(localMillis)} $y"
}
