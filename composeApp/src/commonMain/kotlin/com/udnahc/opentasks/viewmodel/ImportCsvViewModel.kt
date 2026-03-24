package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.calendar.CsvParser
import com.udnahc.opentasks.domain.action.task.ImportCsvTasksAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportCsvUiState(
    val isLoading: Boolean = false,
    val importedCount: Int? = null,
    val error: String? = null,
    val fileName: String? = null,
)

class ImportCsvViewModel(
    private val importAction: ImportCsvTasksAction,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportCsvUiState())
    val uiState: StateFlow<ImportCsvUiState> = _uiState.asStateFlow()

    fun importFromCsvContent(fileName: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null, importedCount = null, fileName = fileName) }
            try {
                val tasks = CsvParser.parse(content)
                if (tasks.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, error = "No tasks found in file") }
                    return@launch
                }
                val count = importAction(tasks)
                _uiState.update { it.copy(isLoading = false, importedCount = count) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Import failed") }
            }
        }
    }

    fun resetState() {
        _uiState.value = ImportCsvUiState()
    }
}
