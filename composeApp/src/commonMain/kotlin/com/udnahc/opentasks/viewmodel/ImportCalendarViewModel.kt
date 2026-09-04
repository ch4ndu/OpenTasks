package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.data.calendar.CalendarPermissionStatus
import com.udnahc.opentasks.data.calendar.CalendarProviderException
import com.udnahc.opentasks.data.calendar.CalendarProviderFailure
import com.udnahc.opentasks.data.extensions.nowUtcMillis
import com.udnahc.opentasks.domain.action.task.ImportCalendarEventsAction
import com.udnahc.opentasks.domain.usecase.settings.CheckCalendarPermissionUseCase
import com.udnahc.opentasks.domain.usecase.task.FetchCalendarEventsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.lighthousegames.logging.logging
import kotlin.time.Instant

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
    private val accountBoundaryExecutor: AccountBoundaryExecutor? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowUtcMillisProvider: () -> Long = ::nowUtcMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportCalendarUiState())
    val uiState: StateFlow<ImportCalendarUiState> = _uiState.asStateFlow()
    private val importInProgress = MutableStateFlow(false)

    val isAvailable: Boolean = fetchCalendarEvents.isAvailable()
    val supportsExplicitImportWithoutPermissionRequest: Boolean =
        fetchCalendarEvents.supportsExplicitImportWithoutPermissionRequest()

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
        _uiState.update { state ->
            state.copy(rangeValue = value.coerceIn(1, state.rangeUnit.maximumValue))
        }
    }

    fun updateRangeUnit(unit: ImportRangeUnit) {
        _uiState.update { state ->
            state.copy(
                rangeUnit = unit,
                rangeValue = state.rangeValue.coerceIn(1, unit.maximumValue),
            )
        }
    }

    fun importEvents() {
        val expectedBoundary = accountBoundaryExecutor?.captureForegroundBoundary()
        if (accountBoundaryExecutor != null && expectedBoundary == null) return
        if (!importInProgress.compareAndSet(expect = false, update = true)) return
        val selectedEvents = _uiState.value
        log.d { "Importing ${selectedEvents.rangeValue} ${selectedEvents.rangeUnit} of calendar events" }
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoading = true, error = null, importedCount = null) }
            try {
                val now = nowUtcMillisProvider()
                val (startUtcMillis, endUtcMillis) = computeRangeBoundsUtcMillis(
                    nowUtcMillis = now,
                    value = selectedEvents.rangeValue,
                    unit = selectedEvents.rangeUnit,
                )
                val events = fetchCalendarEvents(startUtcMillis, endUtcMillis)
                val count = accountBoundaryExecutor.withForegroundActionBoundary(expectedBoundary) {
                    importAction(events)
                }
                _uiState.update { it.copy(isLoading = false, importedCount = count) }
            } catch (e: CancellationException) {
                throw e
            } catch (error: CalendarProviderException) {
                log.e { "Calendar import failed" }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = ImportErrorState(
                            type = error.failure.toImportErrorType(),
                            detail = null,
                        ),
                    )
                }
            } catch (_: Exception) {
                log.e { "Calendar import failed" }
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
        _uiState.update {
            ImportCalendarUiState(permissionStatus = it.permissionStatus)
        }
    }

    private fun computeRangeBoundsUtcMillis(
        nowUtcMillis: Long,
        value: Int,
        unit: ImportRangeUnit,
    ): Pair<Long, Long> {
        val safeValue = value.coerceIn(1, unit.maximumValue)
        val timeZone = TimeZone.currentSystemDefault()
        val nowLocal = Instant.fromEpochMilliseconds(nowUtcMillis).toLocalDateTime(timeZone)
        val startDate = when (unit) {
            ImportRangeUnit.DAYS -> nowLocal.date.minus(safeValue, DateTimeUnit.DAY)
            ImportRangeUnit.WEEKS -> nowLocal.date.minus(safeValue * 7, DateTimeUnit.DAY)
            ImportRangeUnit.MONTHS -> nowLocal.date.minus(safeValue, DateTimeUnit.MONTH)
            ImportRangeUnit.YEARS -> nowLocal.date.minus(safeValue, DateTimeUnit.YEAR)
        }
        val endDate = when (unit) {
            ImportRangeUnit.DAYS -> nowLocal.date.plus(safeValue, DateTimeUnit.DAY)
            ImportRangeUnit.WEEKS -> nowLocal.date.plus(safeValue * 7, DateTimeUnit.DAY)
            ImportRangeUnit.MONTHS -> nowLocal.date.plus(safeValue, DateTimeUnit.MONTH)
            ImportRangeUnit.YEARS -> nowLocal.date.plus(safeValue, DateTimeUnit.YEAR)
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

private val ImportRangeUnit.maximumValue: Int
    get() = if (this == ImportRangeUnit.YEARS) 10 else 99

private fun CalendarProviderFailure.toImportErrorType(): ImportErrorType = when (this) {
    CalendarProviderFailure.ACCESS_DENIED -> ImportErrorType.CALENDAR_ACCESS_DENIED
    CalendarProviderFailure.TRANSPORT -> ImportErrorType.CALENDAR_TRANSPORT
    CalendarProviderFailure.INVALID_RESPONSE -> ImportErrorType.CALENDAR_INVALID_RESPONSE
    CalendarProviderFailure.TOO_MANY_EVENTS -> ImportErrorType.CALENDAR_TOO_MANY_EVENTS
}
