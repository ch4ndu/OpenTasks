package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.dayKeyFromDate
import com.udnahc.opentasks.data.model.CalendarListDisplayModePreference
import com.udnahc.opentasks.data.model.CalendarViewPreference
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.isCountdownItem
import com.udnahc.opentasks.domain.action.settings.SaveCalendarListDisplayModePreferenceAction
import com.udnahc.opentasks.domain.action.settings.SaveCalendarViewPreferenceAction
import com.udnahc.opentasks.domain.action.task.TaskCompletionHandler
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.time.LocalDaySignal
import com.udnahc.opentasks.domain.time.DateTimeTextFormatter
import com.udnahc.opentasks.domain.time.EnglishDateTimeFormatter
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.countdown.ObserveAllCountdownsUseCase
import com.udnahc.opentasks.domain.usecase.countdown.projectCountdownCalendarTasks
import com.udnahc.opentasks.domain.usecase.settings.ObserveCalendarListDisplayModePreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveCalendarViewPreferenceUseCase
import com.udnahc.opentasks.domain.usecase.task.CalendarDayProjection
import com.udnahc.opentasks.domain.usecase.task.CalendarProjectionCache
import com.udnahc.opentasks.domain.usecase.task.CalendarRenderState
import com.udnahc.opentasks.domain.usecase.task.EMPTY_CALENDAR_DAY_PROJECTION
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

private data class CalendarSourceSnapshot(
    val today: LocalDate,
    val dayInputs: Map<Long, List<Task>>,
    val formattingContextKey: String,
)

private data class CalendarRenderRequest(
    val source: CalendarSourceSnapshot,
    val viewPreference: CalendarViewPreference,
    val listDisplayModePreference: CalendarListDisplayModePreference,
    val listSelectedDayKey: Long?,
    val monthSelectedDayKey: Long?,
)

class CalendarViewModel(
    observeTasksByDay: ObserveTasksByDayUseCase,
    observeAllCountdowns: ObserveAllCountdownsUseCase,
    observeAllCategories: ObserveAllCategoriesUseCase,
    toggleTaskCompleteAction: ToggleTaskCompleteAction,
    observeCalendarViewPreference: ObserveCalendarViewPreferenceUseCase,
    saveCalendarViewPreference: SaveCalendarViewPreferenceAction,
    observeCalendarListDisplayModePreference: ObserveCalendarListDisplayModePreferenceUseCase,
    saveCalendarListDisplayModePreference: SaveCalendarListDisplayModePreferenceAction,
    localDaySignal: LocalDaySignal,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    projectionDispatcher: CoroutineDispatcher = Dispatchers.Default,
    accountBoundaryExecutor: AccountBoundaryExecutor? = null,
    private val dateTimeFormatter: DateTimeTextFormatter = EnglishDateTimeFormatter,
) : ViewModel() {

    private val taskMutationFailureEvents = TaskMutationFailureEventStore()
    val taskMutationFailureEvent = taskMutationFailureEvents.event
    private val mutationLauncher = ForegroundMutationLauncher(
        accountBoundaryExecutor,
        viewModelScope,
        ioDispatcher,
    )
    private val completionHandler = TaskCompletionHandler(
        toggleTaskCompleteAction,
        viewModelScope,
        accountBoundaryExecutor,
        mutationLauncher::launch,
        onMutationBoundaryRejected = {
            taskMutationFailureEvents.publish(TaskMutationFailureReason.BOUNDARY_CHANGED)
        },
        onMutationFailure = {
            taskMutationFailureEvents.publish(TaskMutationFailureReason.OPERATION_FAILED)
        },
        onMutationRejected = {
            taskMutationFailureEvents.publish(TaskMutationFailureReason.OPERATION_FAILED)
        },
    )
    val taskPendingSeriesChoice = completionHandler.taskPendingSeriesChoice
    private val _listSelectedDayKey = MutableStateFlow<Long?>(null)
    private val _monthSelectedDayKey = MutableStateFlow<Long?>(null)
    private val saveCalendarViewPreferenceAction = saveCalendarViewPreference
    private val saveCalendarListDisplayModePreferenceAction =
        saveCalendarListDisplayModePreference
    private val calendarViewPreferenceSource = observeCalendarViewPreference().distinctUntilChanged()
    private val calendarListDisplayModePreferenceSource =
        observeCalendarListDisplayModePreference().distinctUntilChanged()
    private val projectionCache = CalendarProjectionCache(dateTimeFormatter)
    private val initialToday = localDaySignal.snapshot()

    private val sourceSnapshots: StateFlow<CalendarSourceSnapshot> = combine(
        observeTasksByDay(),
        observeAllCountdowns(),
        localDaySignal.dates,
    ) { taskInputs, countdowns, today ->
        val merged = taskInputs.mapValuesTo(mutableMapOf()) { (_, tasks) -> tasks }
        projectCountdownCalendarTasks(countdowns, today).forEach { countdownTask ->
            val deadline = countdownTask.deadline ?: return@forEach
            val key = dayKey(deadline)
            merged[key] = merged[key].orEmpty() + countdownTask
        }
        CalendarSourceSnapshot(
            today = today,
            dayInputs = merged,
            formattingContextKey = dateTimeFormatter.formattingContextKey,
        )
    }
        .flowOn(projectionDispatcher)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            CalendarSourceSnapshot(
                today = initialToday,
                dayInputs = emptyMap(),
                formattingContextKey = dateTimeFormatter.formattingContextKey,
            ),
        )

    val calendarViewPreference: StateFlow<CalendarViewPreference> = calendarViewPreferenceSource
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            CalendarViewPreference.MONTH,
        )

    val calendarListDisplayModePreference: StateFlow<CalendarListDisplayModePreference> =
        calendarListDisplayModePreferenceSource
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                CalendarListDisplayModePreference.TIMELINE,
            )

    /** The sole calendar-facing source of the local civil day. */
    val today = sourceSnapshots
        .map { source -> source.today }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialToday)

    val categoryNames: StateFlow<Map<String, String>> = observeAllCategories()
        .map { cats -> cats.associate { it.id to it.name } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val tasksByDay: StateFlow<Map<Long, List<Task>>> = sourceSnapshots
        .map { source ->
            source.dayInputs.mapValues { (_, tasks) -> sortCalendarTasksForDay(tasks) }
        }
        .scan(emptyMap<Long, List<Task>>()) { previous, next ->
            next.mapValues { (day, tasks) ->
                previous[day]?.takeIf { it == tasks } ?: tasks
            }
        }
        .flowOn(projectionDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val taskDayKeys: StateFlow<Set<Long>> = sourceSnapshots
        .map { source -> source.dayInputs.keys.toSet() }
        .distinctUntilChanged()
        .flowOn(projectionDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val calendarRenderState: StateFlow<CalendarRenderState> = combine(
        sourceSnapshots,
        calendarViewPreferenceSource,
        calendarListDisplayModePreferenceSource,
        _listSelectedDayKey,
        _monthSelectedDayKey,
    ) { source, viewPreference, listDisplayModePreference, listSelectedDayKey, monthSelectedDayKey ->
        CalendarRenderRequest(
            source = source,
            viewPreference = viewPreference,
            listDisplayModePreference = listDisplayModePreference,
            listSelectedDayKey = listSelectedDayKey,
            monthSelectedDayKey = monthSelectedDayKey,
        )
    }
        .map { request ->
            projectionCache.render(
                today = request.source.today,
                dayInputs = request.source.dayInputs,
                formattingContextKey = request.source.formattingContextKey,
                viewPreference = request.viewPreference,
                listDisplayModePreference = request.listDisplayModePreference,
                listSelectedDayKey = request.listSelectedDayKey,
                monthSelectedDayKey = request.monthSelectedDayKey,
            )
        }
        .flowOn(projectionDispatcher)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            CalendarRenderState(
                viewPreference = CalendarViewPreference.MONTH,
                listDisplayModePreference = CalendarListDisplayModePreference.TIMELINE,
                today = initialToday,
            ),
        )

    val calendarDaysByDay: StateFlow<Map<Long, CalendarDayProjection>> = calendarRenderState
        .map { renderState -> renderState.calendarDaysByDay }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val selectedListDayProjection: StateFlow<CalendarDayProjection> = combine(
        calendarDaysByDay,
        _listSelectedDayKey,
        sourceSnapshots,
    ) { byDay, selectedKey, source ->
        val todayDate = source.today
        val todayDayKey = dayKeyFromDate(todayDate.year, todayDate.monthNumber, todayDate.dayOfMonth)
        val day = selectedKey ?: todayDayKey
        byDay[day] ?: projectCalendarDay(
            emptyList(),
            targetDayKey = day,
            todayDayKey = todayDayKey,
            dateTimeFormatter = dateTimeFormatter,
        )
    }
        .flowOn(projectionDispatcher)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            EMPTY_CALENDAR_DAY_PROJECTION,
        )

    val selectedMonthDayProjection: StateFlow<CalendarDayProjection> = combine(
        calendarDaysByDay,
        _monthSelectedDayKey,
        sourceSnapshots,
    ) { byDay, selectedKey, source ->
        val todayDate = source.today
        val todayDayKey = dayKeyFromDate(todayDate.year, todayDate.monthNumber, todayDate.dayOfMonth)
        val day = selectedKey ?: todayDayKey
        byDay[day] ?: projectCalendarDay(
            emptyList(),
            targetDayKey = day,
            todayDayKey = todayDayKey,
            dateTimeFormatter = dateTimeFormatter,
        )
    }
        .flowOn(projectionDispatcher)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            EMPTY_CALENDAR_DAY_PROJECTION,
        )

    val selectedListDayTasks: StateFlow<List<Task>> = selectedListDayProjection
        .map { projection -> projection.rows.map { row -> row.task } }
        .flowOn(projectionDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedMonthDayTasks: StateFlow<List<Task>> = selectedMonthDayProjection
        .map { projection -> projection.rows.map { row -> row.task } }
        .flowOn(projectionDispatcher)
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

    fun consumeTaskMutationFailureEvent(event: TaskMutationFailureEvent): Boolean =
        taskMutationFailureEvents.consume(event)
}
