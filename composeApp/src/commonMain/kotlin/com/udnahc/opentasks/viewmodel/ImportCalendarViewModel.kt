package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.calendar.CalendarPermissionStatus
import com.udnahc.opentasks.data.extensions.nowUtcMillis
import com.udnahc.opentasks.domain.action.task.ImportCalendarEventsAction
import com.udnahc.opentasks.domain.usecase.settings.CheckCalendarPermissionUseCase
import com.udnahc.opentasks.domain.usecase.task.FetchCalendarEventsUseCase
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
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
    val error: ImportErrorState? = null,
    val rangeValue: Int = 1,
    val rangeUnit: ImportRangeUnit = ImportRangeUnit.MONTHS,
)

class ImportCalendarViewModel(
    private val fetchCalendarEvents: FetchCalendarEventsUseCase,
    private val checkCalendarPermission: CheckCalendarPermissionUseCase,
    private val importAction: ImportCalendarEventsAction,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowUtcMillisProvider: () -> Long = ::nowUtcMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportCalendarUiState())
    val uiState: StateFlow<ImportCalendarUiState> = _uiState.asStateFlow()

    val isAvailable: Boolean = fetchCalendarEvents.isAvailable()

    fun checkPermission() {
        viewModelScope.launch(ioDispatcher) {
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
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoading = true, error = null, importedCount = null) }
            try {
                val now = nowUtcMillisProvider()
                val (startUtcMillis, endUtcMillis) = computeRangeBoundsUtcMillis(
                    nowUtcMillis = now,
                    value = _uiState.value.rangeValue,
                    unit = _uiState.value.rangeUnit,
                )
                val events = fetchCalendarEvents(startUtcMillis, endUtcMillis)
                val count = importAction(events)
                _uiState.update { it.copy(isLoading = false, importedCount = count) }
            } catch (e: Exception) {
                log.e(e) { "Calendar import failed" }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = ImportErrorState(
                            type = ImportErrorType.GENERIC,
                            detail = e.message,
                        ),
                    )
                }
            }
        }
    }

    fun resetState() {
        _uiState.update {
            ImportCalendarUiState(permissionStatus = it.permissionStatus)
        }
    }

    private fun computeRangeBoundsUtcMillis(
        nowUtcMillis: Long,
        value: Int,
        unit: ImportRangeUnit,
    ): Pair<Long, Long> {
        val timeZone = TimeZone.currentSystemDefault()
        val nowLocal = Instant.fromEpochMilliseconds(nowUtcMillis).toLocalDateTime(timeZone)
        val startDate = when (unit) {
            ImportRangeUnit.DAYS -> nowLocal.date.minus(value, DateTimeUnit.DAY)
            ImportRangeUnit.WEEKS -> nowLocal.date.minus(value * 7, DateTimeUnit.DAY)
            ImportRangeUnit.MONTHS -> nowLocal.date.minus(value, DateTimeUnit.MONTH)
            ImportRangeUnit.YEARS -> nowLocal.date.minus(value, DateTimeUnit.YEAR)
        }
        val endDate = when (unit) {
            ImportRangeUnit.DAYS -> nowLocal.date.plus(value, DateTimeUnit.DAY)
            ImportRangeUnit.WEEKS -> nowLocal.date.plus(value * 7, DateTimeUnit.DAY)
            ImportRangeUnit.MONTHS -> nowLocal.date.plus(value, DateTimeUnit.MONTH)
            ImportRangeUnit.YEARS -> nowLocal.date.plus(value, DateTimeUnit.YEAR)
        }
        val startUtcMillis = LocalDateTime(startDate, nowLocal.time)
            .toInstant(timeZone)
            .toEpochMilliseconds()
        val endUtcMillis = LocalDateTime(endDate, nowLocal.time)
            .toInstant(timeZone)
            .toEpochMilliseconds()
        return startUtcMillis to endUtcMillis
    }
}
