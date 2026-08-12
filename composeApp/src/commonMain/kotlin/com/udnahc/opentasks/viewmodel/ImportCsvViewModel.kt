package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.domain.action.task.ImportCsvTasksAction
import kotlinx.coroutines.CancellationException
import com.udnahc.opentasks.ExternalInputFailure
import com.udnahc.opentasks.domain.usecase.task.ParseCsvUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lighthousegames.logging.logging

private val log = logging("ImportCsvViewModel")

data class ImportCsvUiState(
    val isLoading: Boolean = false,
    val importedCount: Int? = null,
    val error: ImportErrorState? = null,
    val fileName: String? = null,
)

class ImportCsvViewModel(
    private val parseCsv: ParseCsvUseCase,
    private val importAction: ImportCsvTasksAction,
    private val accountBoundaryExecutor: AccountBoundaryExecutor? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportCsvUiState())
    val uiState: StateFlow<ImportCsvUiState> = _uiState.asStateFlow()
    private val importInProgress = MutableStateFlow(false)

    fun importFromCsvContent(
        fileName: String,
        content: String
    ) {
        val expectedBoundary = accountBoundaryExecutor?.captureForegroundBoundary()
        if (accountBoundaryExecutor != null && expectedBoundary == null) return
        if (!importInProgress.compareAndSet(expect = false, update = true)) return
        log.d { "Importing CSV tasks" }
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
                val tasks = parseCsv(content)
                if (tasks.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = ImportErrorState(ImportErrorType.EMPTY_CSV_FILE),
                        )
                    }
                    return@launch
                }
                val count = accountBoundaryExecutor.withForegroundActionBoundary(expectedBoundary) {
                    importAction(tasks)
                }
                _uiState.update { it.copy(isLoading = false, importedCount = count) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                log.e { "CSV import failed" }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = ImportErrorState(
                            type = ImportErrorType.GENERIC,
                            detail = null,
                        ),
                    )
                }
            } finally {
                importInProgress.value = false
            }
        }
    }

    fun resetState() {
        _uiState.value = ImportCsvUiState()
    }

    fun fileSelectionFailed(reason: ExternalInputFailure, detail: String?) {
        _uiState.update {
            it.copy(
                isLoading = false,
                error = ImportErrorState(
                    type = if (reason == ExternalInputFailure.TOO_LARGE) {
                        ImportErrorType.FILE_TOO_LARGE
                    } else {
                        ImportErrorType.GENERIC
                    },
                    detail = detail,
                ),
            )
        }
    }
}
