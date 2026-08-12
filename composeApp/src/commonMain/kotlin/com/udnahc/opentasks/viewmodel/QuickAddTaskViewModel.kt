package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.AccountBoundaryRejectedException
import com.udnahc.opentasks.data.extensions.localMillisToLocalDateTime
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.domain.action.task.AddTaskAction
import com.udnahc.opentasks.domain.usecase.task.ParseQuickTaskInputUseCase
import com.udnahc.opentasks.domain.usecase.task.QuickTaskCreationContext
import com.udnahc.opentasks.domain.usecase.task.QuickTaskParseResult
import com.udnahc.opentasks.domain.usecase.task.QuickTaskToken
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import org.lighthousegames.logging.logging

private val log = logging("QuickAddTaskViewModel")

data class QuickAddTaskUiState(
    val input: String,
    val parseResult: QuickTaskParseResult,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val activeTokens: List<QuickTaskToken>
        get() = parseResult.recognizedTokens.filter { it.isActive }

    val canSave: Boolean
        get() = parseResult.cleanedTitle.isNotBlank() && !isSaving && !isSaved
}

sealed interface QuickAddTaskSaveEvent {
    data class Saved(val taskId: String) : QuickAddTaskSaveEvent
}

class QuickAddTaskViewModel(
    private val context: QuickTaskCreationContext,
    private val parser: ParseQuickTaskInputUseCase,
    private val addTaskAction: AddTaskAction,
    private val accountBoundaryExecutor: AccountBoundaryExecutor,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    referenceTimeProvider: () -> LocalDateTime = { localMillisToLocalDateTime(localNow()) },
) : ViewModel() {
    private val reference = referenceTimeProvider()
    private val openingBoundary: AccountBoundary? = accountBoundaryExecutor.captureForegroundBoundary()
    private val suppressedTokenSignatures = mutableSetOf<String>()
    private val _uiState = MutableStateFlow(stateFor(""))
    val uiState: StateFlow<QuickAddTaskUiState> = _uiState.asStateFlow()

    private val _saveEvent = MutableStateFlow<QuickAddTaskSaveEvent?>(null)
    val saveEvent: StateFlow<QuickAddTaskSaveEvent?> = _saveEvent.asStateFlow()

    fun onInputChanged(input: String) {
        if (_uiState.value.isSaving || _uiState.value.isSaved) return
        _uiState.value = stateFor(input)
    }

    fun dismissToken(signature: String) {
        val token = _uiState.value.activeTokens.firstOrNull { it.signature == signature } ?: return
        suppressedTokenSignatures += token.signature
        _uiState.value = stateFor(_uiState.value.input)
    }

    fun clearError() {
        _uiState.update { it.copy(saveFailed = false) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch(ioDispatcher) {
            try {
                val expectedBoundary = openingBoundary ?: throw AccountBoundaryRejectedException()
                val result = state.parseResult
                val task = accountBoundaryExecutor.withForegroundBoundary(expectedBoundary) {
                    addTaskAction(
                        title = result.cleanedTitle,
                        content = "",
                        subtasks = "",
                        priority = context.priority,
                        deadline = result.deadline,
                        isAllDay = result.isAllDay,
                        recurrenceType = result.recurrenceType,
                        categoryId = context.categoryId,
                    )
                }
                _uiState.update { it.copy(isSaved = true) }
                _saveEvent.value = QuickAddTaskSaveEvent.Saved(task.id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                log.e(error) { "Failed to save a quick task" }
                _uiState.update { it.copy(saveFailed = true) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun consumeSaveEvent(event: QuickAddTaskSaveEvent): Boolean =
        _saveEvent.compareAndSet(expect = event, update = null)

    private fun stateFor(input: String): QuickAddTaskUiState = QuickAddTaskUiState(
        input = input,
        parseResult = parser(
            rawInput = input,
            reference = reference,
            context = context,
            suppressedTokenSignatures = suppressedTokenSignatures,
        ),
    )
}
