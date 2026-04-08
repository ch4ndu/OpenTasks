package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.calendar.CalendarPermissionStatus
import com.udnahc.opentasks.data.extensions.MILLIS_PER_DAY
import com.udnahc.opentasks.data.extensions.nowUtcMillis
import com.udnahc.opentasks.domain.action.task.ImportCalendarEventsAction
import com.udnahc.opentasks.domain.usecase.settings.CheckCalendarPermissionUseCase
import com.udnahc.opentasks.domain.usecase.task.FetchCalendarEventsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lighthousegames.logging.logging

private val log = logging("ImportCalendarViewModel")

enum class ImportRangeUnit { DAYS, WEEKS, MONTHS, YEARS }

data class ImportCalendarUiState(
    val permissionStatus: CalendarPermissionStatus = CalendarPermissionStatus.NOT_DETERMINED,
    val isLoading: Boolean = false,
    val importedCount: Int? = null,
    val error: String? = null,
    val rangeValue: Int = 1,
    val rangeUnit: ImportRangeUnit = ImportRangeUnit.MONTHS,
)

class ImportCalendarViewModel(
    private val fetchCalendarEvents: FetchCalendarEventsUseCase,
    private val checkCalendarPermission: CheckCalendarPermissionUseCase,
    private val importAction: ImportCalendarEventsAction,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportCalendarUiState())
    val uiState: StateFlow<ImportCalendarUiState> = _uiState.asStateFlow()

    val isAvailable: Boolean = fetchCalendarEvents.isAvailable()

    fun checkPermission() {
        viewModelScope.launch(Dispatchers.IO) {
            val status = checkCalendarPermission()
            _uiState.update { it.copy(permissionStatus = status) }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                permissionStatus = if (granted) CalendarPermissionStatus.GRANTED
                else CalendarPermissionStatus.DENIED
            )
        }
    }

    fun updateRangeValue(value: Int) {
        _uiState.update { it.copy(rangeValue = value.coerceIn(1, 99)) }
    }

    fun updateRangeUnit(unit: ImportRangeUnit) {
        _uiState.update { it.copy(rangeUnit = unit) }
    }

    fun importEvents() {
        val selectedEvents = _uiState.value
        log.d { "Importing ${selectedEvents.rangeValue} ${selectedEvents.rangeUnit} of calendar events" }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null, importedCount = null) }
            try {
                val now = nowUtcMillis()
                val rangeMillis = computeRangeMillis(
                    _uiState.value.rangeValue,
                    _uiState.value.rangeUnit,
                )
                val events = fetchCalendarEvents(now - rangeMillis, now + rangeMillis)
                val count = importAction(events)
                _uiState.update { it.copy(isLoading = false, importedCount = count) }
            } catch (e: Exception) {
                log.e { "Calendar import failed: ${e.message}" }
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Import failed")
                }
            }
        }
    }

    fun resetState() {
        _uiState.update {
            ImportCalendarUiState(permissionStatus = it.permissionStatus)
        }
    }

    private fun computeRangeMillis(value: Int, unit: ImportRangeUnit): Long {
        val days = when (unit) {
            ImportRangeUnit.DAYS -> value.toLong()
            ImportRangeUnit.WEEKS -> value.toLong() * 7
            ImportRangeUnit.MONTHS -> value.toLong() * 30
            ImportRangeUnit.YEARS -> value.toLong() * 365
        }
        return days * MILLIS_PER_DAY
    }
}
