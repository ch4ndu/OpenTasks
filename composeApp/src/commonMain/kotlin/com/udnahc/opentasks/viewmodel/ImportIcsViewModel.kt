package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.calendar.IcsParser
import com.udnahc.opentasks.domain.action.task.ImportCalendarEventsAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable
import org.lighthousegames.logging.logging

private val log = logging("ImportIcsViewModel")

@Immutable
data class ImportIcsUiState(
    val isLoading: Boolean = false,
    val importedCount: Int? = null,
    val error: String? = null,
    val fileName: String? = null,
)

class ImportIcsViewModel(
    private val importAction: ImportCalendarEventsAction,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportIcsUiState())
    val uiState: StateFlow<ImportIcsUiState> = _uiState.asStateFlow()

    fun importFromIcsContent(fileName: String, content: String) {
        log.d { "Importing ICS events" }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null, importedCount = null, fileName = fileName) }
            try {
                val events = IcsParser.parse(content)
                if (events.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, error = "No events found in file") }
                    return@launch
                }
                val count = importAction(events)
                _uiState.update { it.copy(isLoading = false, importedCount = count) }
            } catch (e: Exception) {
                log.e { "ICS import failed: ${e.message}" }
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Import failed") }
            }
        }
    }

    fun resetState() {
        _uiState.value = ImportIcsUiState()
    }
}
