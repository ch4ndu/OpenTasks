package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.domain.action.task.ImportCalendarEventsAction
import kotlinx.coroutines.CancellationException
import com.udnahc.opentasks.domain.usecase.task.ParseIcsUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lighthousegames.logging.logging

private val log = logging("ImportIcsViewModel")

data class ImportIcsUiState(
    val isLoading: Boolean = false,
    val importedCount: Int? = null,
    val error: ImportErrorState? = null,
    val fileName: String? = null,
)

class ImportIcsViewModel(
    private val parseIcs: ParseIcsUseCase,
    private val importAction: ImportCalendarEventsAction,
    private val accountBoundaryExecutor: AccountBoundaryExecutor? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportIcsUiState())
    val uiState: StateFlow<ImportIcsUiState> = _uiState.asStateFlow()
    private val importInProgress = MutableStateFlow(false)

    fun importFromIcsContent(
        fileName: String,
        content: String
    ) {
        val expectedBoundary = accountBoundaryExecutor?.captureForegroundBoundary()
        if (accountBoundaryExecutor != null && expectedBoundary == null) return
        if (!importInProgress.compareAndSet(expect = false, update = true)) return
        log.d { "Importing ICS events" }
        viewModelScope.launch(ioDispatcher) {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    importedCount = null,
                    fileName = fileName
                )
            }
            try {
                val events = parseIcs(content)
                if (events.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = ImportErrorState(ImportErrorType.EMPTY_ICS_FILE),
                        )
                    }
                    return@launch
                }
                val count = accountBoundaryExecutor.withForegroundActionBoundary(expectedBoundary) {
                    importAction(events)
                }
                _uiState.update { it.copy(isLoading = false, importedCount = count) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "ICS import failed" }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = ImportErrorState(
                            type = ImportErrorType.GENERIC,
                            detail = e.message,
                        ),
                    )
                }
            } finally {
                importInProgress.value = false
            }
        }
    }

    fun resetState() {
        _uiState.value = ImportIcsUiState()
    }

    fun fileSelectionFailed(detail: String?) {
        _uiState.update {
            it.copy(
                isLoading = false,
                error = ImportErrorState(ImportErrorType.GENERIC, detail),
            )
        }
    }
}
