package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.model.AttachmentSummary
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskListFilter
import com.udnahc.opentasks.data.model.TaskListViewMode
import com.udnahc.opentasks.data.model.TaskSortOption
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.domain.action.settings.SaveTaskListViewModeAction
import com.udnahc.opentasks.domain.action.settings.SaveTaskSortOptionAction
import com.udnahc.opentasks.domain.action.task.TaskCompletionHandler
import com.udnahc.opentasks.domain.action.task.TaskWriteResult
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskStarredAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskStatusAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.attachment.ObserveTaskImageSummariesUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveTaskListViewModeUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveTaskSortOptionUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksForFilterUseCase
import com.udnahc.opentasks.domain.usecase.task.PlainTaskDueTextProvider
import com.udnahc.opentasks.domain.usecase.task.TaskDueTextProvider
import com.udnahc.opentasks.domain.usecase.task.sortedFor
import com.udnahc.opentasks.domain.usecase.task.taskPreviewTextById
import com.udnahc.opentasks.domain.time.LocalDaySignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class TaskListViewModel(
    val filter: TaskListFilter,
    observeTasksForFilter: ObserveTasksForFilterUseCase,
    observeAllCategories: ObserveAllCategoriesUseCase,
    toggleTaskCompleteAction: ToggleTaskCompleteAction,
    private val toggleTaskStarredAction: ToggleTaskStarredAction,
    observeTaskSortOption: ObserveTaskSortOptionUseCase,
    private val saveTaskSortOptionAction: SaveTaskSortOptionAction,
    observeTaskListViewMode: ObserveTaskListViewModeUseCase,
    private val saveTaskListViewModeAction: SaveTaskListViewModeAction,
    private val updateTaskStatusAction: UpdateTaskStatusAction,
    observeTaskImageSummaries: ObserveTaskImageSummariesUseCase,
    localDaySignal: LocalDaySignal,
    private val taskDueTextProvider: TaskDueTextProvider = PlainTaskDueTextProvider,
    accountBoundaryExecutor: AccountBoundaryExecutor? = null,
) : ViewModel() {

    data class SectionGroup(
        val category: ActiveTaskListSection,
        val tasks: List<Task>
    )

    data class ListProjection(
        val completedTasks: List<Task> = emptyList(),
        val groupedActiveTasks: List<SectionGroup> = emptyList(),
    )

    enum class ActiveTaskListSection {
        OVERDUE,
        UPCOMING
    }

    private val mutationLauncher = ForegroundMutationLauncher(
        accountBoundaryExecutor,
        viewModelScope,
    )
    private val taskMutationFailureEvents = TaskMutationFailureEventStore()
    val taskMutationFailureEvent = taskMutationFailureEvents.event
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

    val tasksForSelectedCategory: StateFlow<List<Task>> = observeTasksForFilter(filter)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryName: StateFlow<String> = if (filter is TaskListFilter.Category) {
        observeAllCategories()
            .map { categories -> categories.find { it.id == filter.id }?.name.orEmpty() }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    } else {
        MutableStateFlow("")
    }

    val sortOption: StateFlow<TaskSortOption> = observeTaskSortOption()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            TaskSortOption.RECENTLY_UPDATED
        )

    val viewMode: StateFlow<TaskListViewMode> = observeTaskListViewMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskListViewMode.LIST)

    val taskImageSummaries: StateFlow<Map<String, AttachmentSummary>> = observeTaskImageSummaries()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val taskContentPreviews: StateFlow<Map<String, String>> = tasksForSelectedCategory
        .map(::taskPreviewTextById)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val taskDueTextById: StateFlow<Map<String, String>> = tasksForSelectedCategory
        .map { tasks -> tasks.associate { task -> task.id to taskDueTextProvider.listDueText(task) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val boardTaskDueTextById: StateFlow<Map<String, String>> = tasksForSelectedCategory
        .map { tasks -> tasks.associate { task -> task.id to taskDueTextProvider.matrixDueText(task) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val activeTasksForSelectedCategory: StateFlow<List<Task>> =
        combine(tasksForSelectedCategory, sortOption) { tasks, sort ->
            val active = tasks.filter { it.status != TaskStatus.DONE }
            active.sortedFor(sort)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasksForSelectedCategory: StateFlow<List<Task>> = tasksForSelectedCategory
        .map { tasks -> tasks.filter { it.status == TaskStatus.DONE } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedActiveTasks: StateFlow<List<SectionGroup>> =
        combine(activeTasksForSelectedCategory, localDaySignal.dates) { tasks, today ->
            groupActiveTasks(tasks, today)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val listProjection: StateFlow<ListProjection> = viewMode
        .flatMapLatest { mode ->
            if (mode == TaskListViewMode.LIST) {
                combine(tasksForSelectedCategory, sortOption, localDaySignal.dates) { tasks, sort, today ->
                    val active = tasks.filter { it.status != TaskStatus.DONE }
                    ListProjection(
                        completedTasks = tasks.filter { it.status == TaskStatus.DONE },
                        groupedActiveTasks = groupActiveTasks(active.sortedFor(sort), today),
                    )
                }
            } else {
                flowOf(ListProjection())
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListProjection())

    fun toggleComplete(task: Task) = completionHandler.toggleComplete(
        task.id,
        task.status,
        task.recurrenceType,
        task.deadline,
    )
    fun completeOccurrence() = completionHandler.completeOccurrence()
    fun completeSeries() = completionHandler.completeSeries()
    fun dismissSeriesChoice() = completionHandler.dismissSeriesChoice()

    fun consumeTaskMutationFailureEvent(event: TaskMutationFailureEvent): Boolean =
        taskMutationFailureEvents.consume(event)

    fun toggleStar(task: Task) {
        mutationLauncher.launch(
            onBoundaryRejected = {
                taskMutationFailureEvents.publish(TaskMutationFailureReason.BOUNDARY_CHANGED)
            },
            onFailure = {
                taskMutationFailureEvents.publish(TaskMutationFailureReason.OPERATION_FAILED)
            },
        ) {
            when (toggleTaskStarredAction(task.id)) {
                is TaskWriteResult.Updated -> Unit
                is TaskWriteResult.CompletionChoiceRequired,
                TaskWriteResult.Missing,
                TaskWriteResult.NoOp,
                TaskWriteResult.StaleOccurrence,
                -> taskMutationFailureEvents.publish(TaskMutationFailureReason.OPERATION_FAILED)
            }
        }
    }

    fun setSortOption(option: TaskSortOption) {
        viewModelScope.launch(Dispatchers.IO) { saveTaskSortOptionAction(option) }
    }

    val tasksByStatus: StateFlow<Map<TaskStatus, List<Task>>> =
        combine(tasksForSelectedCategory, sortOption) { tasks, sort ->
            TaskStatus.entries.associateWith { status ->
                tasks.filter { it.status == status }.sortedFor(sort)
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setViewMode(mode: TaskListViewMode) {
        viewModelScope.launch(Dispatchers.IO) { saveTaskListViewModeAction(mode) }
    }

    fun moveTaskToStatus(
        task: Task,
        targetStatus: TaskStatus
    ) {
        if (targetStatus == task.status) return
        if (targetStatus == TaskStatus.DONE && task.status != TaskStatus.DONE) {
            toggleComplete(task)
        } else {
            mutationLauncher.launch(
                onBoundaryRejected = {
                    taskMutationFailureEvents.publish(TaskMutationFailureReason.BOUNDARY_CHANGED)
                },
                onFailure = {
                    taskMutationFailureEvents.publish(TaskMutationFailureReason.OPERATION_FAILED)
                },
            ) {
                when (updateTaskStatusAction(task.id, targetStatus).value) {
                    is TaskWriteResult.Updated -> Unit
                    is TaskWriteResult.CompletionChoiceRequired,
                    TaskWriteResult.Missing,
                    TaskWriteResult.NoOp,
                    TaskWriteResult.StaleOccurrence,
                    -> taskMutationFailureEvents.publish(TaskMutationFailureReason.OPERATION_FAILED)
                }
            }
        }
    }

    private fun groupActiveTasks(
        tasks: List<Task>,
        today: LocalDate,
    ): List<SectionGroup> {
        val startOfToday =
            startOfDayLocalMillis(today.year, today.monthNumber, today.dayOfMonth)
        val overdueTasks = tasks.filter { it.deadline != null && it.deadline < startOfToday }
        val upcomingTasks = tasks.filterNot { it.deadline != null && it.deadline < startOfToday }

        return buildList {
            if (overdueTasks.isNotEmpty()) {
                add(SectionGroup(ActiveTaskListSection.OVERDUE, overdueTasks))
            }
            if (upcomingTasks.isNotEmpty()) {
                add(SectionGroup(ActiveTaskListSection.UPCOMING, upcomingTasks))
            }
        }
    }

}
