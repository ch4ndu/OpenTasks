package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.AccountBoundaryRejectedException
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.domain.usecase.task.ObserveAllTasksUseCase
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.repository.CommittedMutation
import com.udnahc.opentasks.domain.action.note.AddNoteAction
import com.udnahc.opentasks.domain.action.note.DeleteNoteAction
import com.udnahc.opentasks.domain.action.note.UpdateNoteAction
import com.udnahc.opentasks.domain.time.DateTimeTextFormatter
import com.udnahc.opentasks.domain.time.EnglishDateTimeFormatter
import com.udnahc.opentasks.domain.time.LocalDaySignal
import com.udnahc.opentasks.domain.usecase.note.ObserveAllNotesUseCase
import com.udnahc.opentasks.domain.usecase.note.ObserveNoteByIdUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.lighthousegames.logging.logging

private val log = logging("NoteViewModel")

data class NoteListItem(
    val note: Note,
    val previewText: String,
    val updatedAtText: String,
    val linkedTaskCount: Int = 0,
)

sealed interface NoteSelectionState {
    val noteId: String?

    data object Unselected : NoteSelectionState {
        override val noteId: String? = null
    }
    data class Loading(override val noteId: String) : NoteSelectionState
    data class Missing(override val noteId: String) : NoteSelectionState
    data class Ready(val note: Note) : NoteSelectionState {
        override val noteId: String = note.id
    }
}

sealed interface NoteMutationOperation {
    val requestToken: Long

    data class Create(
        override val requestToken: Long,
        val recoveryNoteId: String? = null,
    ) : NoteMutationOperation

    data class Update(
        override val requestToken: Long,
        val noteId: String,
    ) : NoteMutationOperation

    data class Delete(
        override val requestToken: Long,
        val note: Note,
    ) : NoteMutationOperation
}

sealed interface NoteMutationState {
    data object Idle : NoteMutationState
    data class Busy(val operation: NoteMutationOperation) : NoteMutationState
    data class Success(
        val operation: NoteMutationOperation,
        val hasPostCommitWarning: Boolean,
        val committedNote: Note? = null,
    ) : NoteMutationState
    data class Error(val operation: NoteMutationOperation) : NoteMutationState
}

internal fun NoteMutationOperation.matchesEditor(requestToken: Long, noteId: String?): Boolean {
    if (this.requestToken != requestToken) return false
    return when (this) {
        is NoteMutationOperation.Create -> recoveryNoteId == noteId
        is NoteMutationOperation.Update -> this.noteId == noteId
        is NoteMutationOperation.Delete -> note.id == noteId
    }
}

class NoteViewModel(
    observeAllNotes: ObserveAllNotesUseCase,
    private val observeNoteById: ObserveNoteByIdUseCase,
    private val addNoteAction: AddNoteAction,
    private val updateNoteAction: UpdateNoteAction,
    private val deleteNoteAction: DeleteNoteAction,
    private val accountBoundaryExecutor: AccountBoundaryExecutor? = null,
    observeAllTasks: ObserveAllTasksUseCase? = null,
    private val dateTimeFormatter: DateTimeTextFormatter = EnglishDateTimeFormatter,
    localDaySignal: LocalDaySignal = LocalDaySignal(),
) : ViewModel() {

    private val _selectedNoteId = MutableStateFlow<String?>(null)
    private val _mutationState = MutableStateFlow<NoteMutationState>(NoteMutationState.Idle)
    val mutationState: StateFlow<NoteMutationState> = _mutationState

    val searchQuery = MutableStateFlow("")

    val noteListItems: StateFlow<List<NoteListItem>> = flow {
        val projections = NoteListProjectionCache(dateTimeFormatter)
        emitAll(combine(
            observeAllNotes(),
            localDaySignal.formattingContexts { dateTimeFormatter.formattingContextKey },
        ) { notes, context -> projections.project(notes, context) })
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allTasks: StateFlow<List<Task>> = (observeAllTasks?.invoke() ?: flowOf(emptyList()))
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val taskCountsByNote = allTasks
        .map { tasks -> tasks.mapNotNull { it.sourceNoteId }.groupingBy { it }.eachCount() }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val libraryItems = combine(noteListItems, taskCountsByNote, searchQuery) { items, counts, query ->
        val needle = query.trim()
        val matching = if (needle.isEmpty()) items else items.filter {
            it.note.title.contains(needle, ignoreCase = true) || it.previewText.contains(needle, ignoreCase = true)
        }
        matching.map { it.copy(linkedTaskCount = counts[it.note.id] ?: 0) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedNoteState: StateFlow<NoteSelectionState> = _selectedNoteId
        .flatMapLatest { id ->
            if (id == null) flowOf(NoteSelectionState.Unselected)
            else observeNoteById(id)
                .map<Note?, NoteSelectionState> { note ->
                    if (note == null) NoteSelectionState.Missing(id) else NoteSelectionState.Ready(note)
                }
                .onStart { emit(NoteSelectionState.Loading(id)) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NoteSelectionState.Unselected)

    val linkedTasks = combine(_selectedNoteId, allTasks) { id, tasks ->
        if (id == null) emptyList() else tasks.filter { it.sourceNoteId == id }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val linkedTaskPreview = linkedTasks.map { it.take(2) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectNote(noteId: String?) {
        _selectedNoteId.value = noteId
    }

    fun addNote(
        title: String,
        content: String
    ) {
        addNote(LEGACY_NOTE_REQUEST_TOKEN, title, content)
    }

    fun addNote(
        requestToken: Long,
        title: String,
        content: String,
        recoveryNoteId: String? = null,
    ) {
        launchMutation(NoteMutationOperation.Create(requestToken, recoveryNoteId)) {
            addNoteAction(title, content)
        }
    }

    fun updateNote(note: Note) {
        updateNote(LEGACY_NOTE_REQUEST_TOKEN, note)
    }

    fun updateNote(requestToken: Long, note: Note) {
        launchMutation(NoteMutationOperation.Update(requestToken, note.id)) {
            updateNoteAction(note)
        }
    }

    fun deleteNote(note: Note) {
        deleteNote(LEGACY_NOTE_REQUEST_TOKEN, note)
    }

    fun deleteNote(requestToken: Long, note: Note) {
        launchMutation(NoteMutationOperation.Delete(requestToken, note)) {
            deleteNoteAction(note)
        }
    }

    fun consumeMutationState(state: NoteMutationState): Boolean =
        when (state) {
            is NoteMutationState.Success,
            is NoteMutationState.Error,
            -> _mutationState.compareAndSet(state, NoteMutationState.Idle)
            NoteMutationState.Idle,
            is NoteMutationState.Busy,
            -> false
        }

    private fun launchMutation(
        operation: NoteMutationOperation,
        block: suspend () -> CommittedMutation<Note>,
    ) {
        val busy = NoteMutationState.Busy(operation)
        if (!_mutationState.compareAndSet(NoteMutationState.Idle, busy)) return
        val expectedBoundary = accountBoundaryExecutor?.captureForegroundBoundary()
        if (accountBoundaryExecutor != null && expectedBoundary == null) {
            log.w { "Note mutation rejected because no foreground account boundary is active" }
            _mutationState.compareAndSet(busy, NoteMutationState.Error(operation))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val committed = accountBoundaryExecutor.withForegroundActionBoundary(
                    expectedBoundary,
                    block,
                )
                _mutationState.compareAndSet(
                    busy,
                    NoteMutationState.Success(
                        operation = operation,
                        hasPostCommitWarning = committed.postCommitWarning != null,
                        committedNote = committed.value,
                    ),
                )
            } catch (error: CancellationException) {
                _mutationState.compareAndSet(busy, NoteMutationState.Idle)
                throw error
            } catch (_: AccountBoundaryRejectedException) {
                log.w { "Note mutation skipped because the foreground account boundary changed" }
                _mutationState.compareAndSet(busy, NoteMutationState.Error(operation))
            } catch (_: Exception) {
                log.e { "Note mutation failed before commit" }
                _mutationState.compareAndSet(busy, NoteMutationState.Error(operation))
            }
        }
    }
}

/** Confined to one collection; only the current list's display inputs are retained. */
internal class NoteListProjectionCache(
    private val dateTimeFormatter: DateTimeTextFormatter,
    private val previewContent: (String) -> String = ::noteContentPreview,
) {
    private var itemsById = emptyMap<String, NoteListItem>()
    private var previousContext: String? = null

    fun project(notes: List<Note>, formattingContext: String): List<NoteListItem> {
        val items = notes.map { note ->
            val previous = itemsById[note.id]
            val preview = if (previous != null && previous.note.content == note.content) {
                previous.previewText
            } else {
                previewContent(note.content)
            }
            val date = if (previous != null && previous.note.updatedAt == note.updatedAt &&
                previousContext == formattingContext
            ) {
                previous.updatedAtText
            } else {
                formatNoteDate(note.updatedAt, dateTimeFormatter)
            }
            if (previous != null && previous.note === note &&
                previous.previewText == preview && previous.updatedAtText == date
            ) {
                previous
            } else {
                // Keep the latest entity for callbacks even when its display inputs are unchanged.
                NoteListItem(note = note, previewText = preview, updatedAtText = date)
            }
        }
        itemsById = items.associateBy { it.note.id }
        previousContext = formattingContext
        return items
    }
}

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

private fun formatNoteDate(
    localMillis: Long,
    dateTimeFormatter: DateTimeTextFormatter,
): String {
    if (localMillis == 0L) return ""
    return dateTimeFormatter.formatDateWithYear(localMillis)
}

private const val LEGACY_NOTE_REQUEST_TOKEN = 0L
