package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.dayKeyFromDate
import com.udnahc.opentasks.data.extensions.todayLocal
import com.udnahc.opentasks.data.model.CalendarListDisplayModePreference
import com.udnahc.opentasks.data.model.CalendarViewPreference
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.isCountdownItem
import com.udnahc.opentasks.domain.action.settings.SaveCalendarListDisplayModePreferenceAction
import com.udnahc.opentasks.domain.action.settings.SaveCalendarViewPreferenceAction
import com.udnahc.opentasks.domain.action.task.TaskCompletionHandler
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.time.LocalDaySignal
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.countdown.ObserveAllCountdownsUseCase
import com.udnahc.opentasks.domain.usecase.countdown.projectCountdownCalendarTasks
import com.udnahc.opentasks.domain.usecase.settings.ObserveCalendarListDisplayModePreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveCalendarViewPreferenceUseCase
import com.udnahc.opentasks.domain.usecase.task.CalendarDayProjection
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByDayUseCase
import com.udnahc.opentasks.domain.usecase.task.projectCalendarDay
import com.udnahc.opentasks.domain.usecase.task.sortCalendarTasksForDay
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CalendarViewModel(
    observeTasksByDay: ObserveTasksByDayUseCase,
    observeAllCountdowns: ObserveAllCountdownsUseCase,
    observeAllCategories: ObserveAllCategoriesUseCase,
    toggleTaskCompleteAction: ToggleTaskCompleteAction,
    observeCalendarViewPreference: ObserveCalendarViewPreferenceUseCase,
    saveCalendarViewPreference: SaveCalendarViewPreferenceAction,
    observeCalendarListDisplayModePreference: ObserveCalendarListDisplayModePreferenceUseCase,
    saveCalendarListDisplayModePreference: SaveCalendarListDisplayModePreferenceAction,
    localDaySignal: LocalDaySignal = LocalDaySignal(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val completionHandler = TaskCompletionHandler(toggleTaskCompleteAction, viewModelScope)
    val taskPendingSeriesChoice = completionHandler.taskPendingSeriesChoice
    private val _listSelectedDayKey = MutableStateFlow<Long?>(null)
    private val _monthSelectedDayKey = MutableStateFlow<Long?>(null)
    private val saveCalendarViewPreferenceAction = saveCalendarViewPreference
    private val saveCalendarListDisplayModePreferenceAction =
        saveCalendarListDisplayModePreference

    val calendarViewPreference: StateFlow<CalendarViewPreference> = observeCalendarViewPreference()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            CalendarViewPreference.MONTH,
        )

    val calendarListDisplayModePreference: StateFlow<CalendarListDisplayModePreference> =
        observeCalendarListDisplayModePreference()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                CalendarListDisplayModePreference.TIMELINE,
            )

    /** The sole calendar-facing source of the local civil day. */
    val today = localDaySignal.dates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), todayLocal())

    val categoryNames: StateFlow<Map<String, String>> = observeAllCategories()
        .map { cats -> cats.associate { it.id to it.name } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val tasksByDay: StateFlow<Map<Long, List<Task>>> = combine(
        observeTasksByDay(),
        observeAllCountdowns(),
        today,
    ) { tasksMap, countdowns, today ->
        val merged = tasksMap.toMutableMap()
        for (countdownTask in projectCountdownCalendarTasks(countdowns, today)) {
            val deadline = countdownTask.deadline ?: continue
            val dk = dayKey(deadline)
            val existing = merged[dk].orEmpty()
            merged[dk] = existing + countdownTask
        }
        merged.mapValues { (_, tasks) -> sortCalendarTasksForDay(tasks) }
    }
        .scan(emptyMap<Long, List<Task>>()) { previous, next ->
            next.mapValues { (day, tasks) ->
                previous[day]?.takeIf { it == tasks } ?: tasks
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val calendarDaysByDay: StateFlow<Map<Long, CalendarDayProjection>> = combine(
        tasksByDay,
        today,
    ) { byDay, todayDate ->
        val todayDayKey = dayKeyFromDate(todayDate.year, todayDate.monthNumber, todayDate.dayOfMonth)
        byDay.mapValues { (day, tasks) ->
            projectCalendarDay(tasks, targetDayKey = day, todayDayKey = todayDayKey)
        }
    }
        .scan(emptyMap<Long, CalendarDayProjection>()) { previous, next ->
            next.mapValues { (day, tasks) ->
                previous[day]?.takeIf { it == tasks } ?: tasks
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val selectedListDayProjection: StateFlow<CalendarDayProjection> = combine(
        calendarDaysByDay,
        _listSelectedDayKey,
        today,
    ) { byDay, selectedKey, todayDate ->
        val todayDayKey = dayKeyFromDate(todayDate.year, todayDate.monthNumber, todayDate.dayOfMonth)
        val day = selectedKey ?: todayDayKey
        byDay[day] ?: projectCalendarDay(emptyList(), targetDayKey = day, todayDayKey = todayDayKey)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            projectCalendarDay(emptyList(), dayKeyFromDate(today.value.year, today.value.monthNumber, today.value.dayOfMonth), dayKeyFromDate(today.value.year, today.value.monthNumber, today.value.dayOfMonth)),
        )

    val selectedMonthDayProjection: StateFlow<CalendarDayProjection> = combine(
        calendarDaysByDay,
        _monthSelectedDayKey,
        today,
    ) { byDay, selectedKey, todayDate ->
        val todayDayKey = dayKeyFromDate(todayDate.year, todayDate.monthNumber, todayDate.dayOfMonth)
        val day = selectedKey ?: todayDayKey
        byDay[day] ?: projectCalendarDay(emptyList(), targetDayKey = day, todayDayKey = todayDayKey)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            projectCalendarDay(emptyList(), dayKeyFromDate(today.value.year, today.value.monthNumber, today.value.dayOfMonth), dayKeyFromDate(today.value.year, today.value.monthNumber, today.value.dayOfMonth)),
        )

    val selectedListDayTasks: StateFlow<List<Task>> = selectedListDayProjection
        .map { projection -> projection.rows.map { row -> row.task } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedMonthDayTasks: StateFlow<List<Task>> = selectedMonthDayProjection
        .map { projection -> projection.rows.map { row -> row.task } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectListDay(millis: Long) {
        _listSelectedDayKey.value = dayKey(millis)
    }

    fun clearMonthSelectedDay() {
        _monthSelectedDayKey.value = null
    }

    fun saveCalendarViewPreference(preference: CalendarViewPreference) {
        viewModelScope.launch(ioDispatcher) {
            saveCalendarViewPreferenceAction(preference)
        }
    }

    fun saveCalendarListDisplayModePreference(preference: CalendarListDisplayModePreference) {
        viewModelScope.launch(ioDispatcher) {
            saveCalendarListDisplayModePreferenceAction(preference)
        }
    }

    fun selectMonthDay(
        year: Int,
        month: Int,
        day: Int
    ) {
        _monthSelectedDayKey.value = dayKeyFromDate(year, month, day)
    }

    fun toggleComplete(task: Task) {
        if (task.isCountdownItem) return
        completionHandler.toggleComplete(
            task.id,
            task.status,
            task.recurrenceType,
            task.deadline,
        )
    }

    fun completeOccurrence() = completionHandler.completeOccurrence()
    fun completeSeries() = completionHandler.completeSeries()
    fun dismissSeriesChoice() = completionHandler.dismissSeriesChoice()
}
