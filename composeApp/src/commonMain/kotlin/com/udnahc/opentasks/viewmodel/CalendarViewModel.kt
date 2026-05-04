package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.CalendarListDisplayModePreference
import com.udnahc.opentasks.data.model.CalendarViewPreference
import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.dayKeyFromDate
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.isCountdownItem
import com.udnahc.opentasks.data.model.toCalendarTask
import com.udnahc.opentasks.domain.action.settings.SaveCalendarListDisplayModePreferenceAction
import com.udnahc.opentasks.domain.action.settings.SaveCalendarViewPreferenceAction
import com.udnahc.opentasks.domain.action.task.TaskCompletionHandler
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.countdown.ObserveAllCountdownsUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveCalendarListDisplayModePreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveCalendarViewPreferenceUseCase
import com.udnahc.opentasks.domain.usecase.task.CalendarDayTasks
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByDayUseCase
import com.udnahc.opentasks.domain.usecase.task.splitCalendarDayTasks
import com.udnahc.opentasks.domain.usecase.task.sortCalendarTasksForDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
) : ViewModel() {

    private val completionHandler = TaskCompletionHandler(toggleTaskCompleteAction, viewModelScope)
    val taskPendingSeriesChoice: StateFlow<Task?> = completionHandler.taskPendingSeriesChoice
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

    val categoryNames: StateFlow<Map<String, String>> = observeAllCategories()
        .map { cats -> cats.associate { it.id to it.name } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val tasksByDay: StateFlow<Map<Long, List<Task>>> = combine(
        observeTasksByDay(),
        observeAllCountdowns(),
    ) { tasksMap, countdowns ->
        val merged = tasksMap.toMutableMap()
        for (countdown in countdowns) {
            val dk = dayKey(countdown.targetDate)
            val existing = merged[dk].orEmpty()
            merged[dk] = sortCalendarTasksForDay(existing + countdown.toCalendarTask())
        }
        merged.mapValues { (_, tasks) -> sortCalendarTasksForDay(tasks) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val timelineTasksByDay: StateFlow<Map<Long, CalendarDayTasks>> = tasksByDay
        .map { byDay -> byDay.mapValues { (_, tasks) -> splitCalendarDayTasks(tasks) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val selectedListDayTasks: StateFlow<List<Task>> = combine(
        tasksByDay,
        _listSelectedDayKey,
    ) { byDay, selectedKey -> selectedKey?.let { byDay[it] }.orEmpty() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedMonthDayTasks: StateFlow<List<Task>> = combine(
        tasksByDay,
        _monthSelectedDayKey,
    ) { byDay, selectedKey -> selectedKey?.let { byDay[it] }.orEmpty() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectListDay(millis: Long) {
        _listSelectedDayKey.value = dayKey(millis)
    }

    fun clearMonthSelectedDay() {
        _monthSelectedDayKey.value = null
    }

    fun saveCalendarViewPreference(preference: CalendarViewPreference) {
        viewModelScope.launch(Dispatchers.IO) {
            saveCalendarViewPreferenceAction(preference)
        }
    }

    fun saveCalendarListDisplayModePreference(preference: CalendarListDisplayModePreference) {
        viewModelScope.launch(Dispatchers.IO) {
            saveCalendarListDisplayModePreferenceAction(preference)
        }
    }

    fun selectMonthDay(year: Int, month: Int, day: Int) {
        _monthSelectedDayKey.value = dayKeyFromDate(year, month, day)
    }

    fun toggleComplete(task: Task) {
        if (task.isCountdownItem) return
        completionHandler.toggleComplete(task)
    }

    fun completeOccurrence() = completionHandler.completeOccurrence()
    fun completeSeries() = completionHandler.completeSeries()
    fun dismissSeriesChoice() = completionHandler.dismissSeriesChoice()
}
