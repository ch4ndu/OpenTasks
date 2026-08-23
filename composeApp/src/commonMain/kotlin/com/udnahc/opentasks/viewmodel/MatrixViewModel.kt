package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.AttachmentSummary
import com.udnahc.opentasks.data.model.TaskCategory
import com.udnahc.opentasks.data.model.TaskListViewMode
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.domain.action.task.TaskCompletionHandler
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskStarredAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskStatusAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.attachment.ObserveTaskImageSummariesUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByPriorityUseCase
import com.udnahc.opentasks.domain.usecase.task.PlainTaskDueTextProvider
import com.udnahc.opentasks.domain.usecase.task.TaskDueTextProvider
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class MatrixViewModel(
    observeTasksByPriority: ObserveTasksByPriorityUseCase,
    observeAllCategories: ObserveAllCategoriesUseCase,
    toggleTaskCompleteAction: ToggleTaskCompleteAction,
    private val toggleTaskStarredAction: ToggleTaskStarredAction,
    private val updateTaskStatusAction: UpdateTaskStatusAction,
    observeTaskImageSummaries: ObserveTaskImageSummariesUseCase,
    localDaySignal: LocalDaySignal,
    private val taskDueTextProvider: TaskDueTextProvider = PlainTaskDueTextProvider,
    accountBoundaryExecutor: AccountBoundaryExecutor? = null,
) : ViewModel() {

    data class TaskCategoryGroup(
        val category: TaskCategory,
        val tasks: List<Task>
    )

    data class PriorityProjection(
        val tasks: List<Task> = emptyList(),
        val visibleTasks: List<Task> = emptyList(),
        val hasMore: Boolean = false,
    )

    private val _selectedPriority = MutableStateFlow(TaskPriority.HIGH)
    private val _viewMode = MutableStateFlow(TaskListViewMode.LIST)
    private val mutationLauncher = ForegroundMutationLauncher(
        accountBoundaryExecutor,
        viewModelScope,
    )
    private val completionHandler = TaskCompletionHandler(
        toggleTaskCompleteAction,
        viewModelScope,
        accountBoundaryExecutor,
        mutationLauncher::launch,
    )
    val taskPendingSeriesChoice = completionHandler.taskPendingSeriesChoice
    val viewMode: StateFlow<TaskListViewMode> = _viewMode

    val taskImageSummaries: StateFlow<Map<String, AttachmentSummary>> = observeTaskImageSummaries()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val categoryNames: StateFlow<Map<String, String>> = observeAllCategories()
        .map { cats -> cats.associate { it.id to it.name } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val tasksByPriority: StateFlow<Map<TaskPriority, List<Task>>> = observeTasksByPriority()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val tasksForSelectedPriority: StateFlow<List<Task>> =
        combine(tasksByPriority, _selectedPriority) { tasks, priority -> tasks[priority].orEmpty() }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val priorityProjections: StateFlow<Map<TaskPriority, PriorityProjection>> = tasksByPriority
        .map { tasksByPriority ->
            TaskPriority.entries.associateWith { priority ->
                val tasks = tasksByPriority[priority].orEmpty()
                PriorityProjection(
                    tasks = tasks,
                    visibleTasks = tasks.take(MATRIX_VISIBLE_TASK_LIMIT),
                    hasMore = tasks.size > MATRIX_VISIBLE_TASK_LIMIT,
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val taskContentPreviews: StateFlow<Map<String, String>> = tasksForSelectedPriority
        .map(::taskPreviewTextById)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val taskDueTextById: StateFlow<Map<String, String>> = tasksByPriority
        .map { tasksByPriority ->
            tasksByPriority.values.flatten().associate { task -> task.id to taskDueTextProvider.matrixDueText(task) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val categorizedTasks: StateFlow<List<TaskCategoryGroup>> = _viewMode
        .flatMapLatest { mode ->
            if (mode == TaskListViewMode.LIST) {
                combine(tasksForSelectedPriority, localDaySignal.dates) { tasks, today ->
                    categorize(tasks, today)
                }
            } else {
                flowOf(emptyList())
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val tasksByStatus: StateFlow<Map<TaskStatus, List<Task>>> = _viewMode
        .flatMapLatest { mode ->
            if (mode == TaskListViewMode.BOARD) {
                tasksForSelectedPriority.map { tasks ->
                    TaskStatus.entries.associateWith { status ->
                        tasks.filter { it.status == status }
                    }
                }
            } else {
                flowOf(emptyMap())
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun selectPriority(priority: TaskPriority) {
        _selectedPriority.value = priority
    }

    fun setViewMode(mode: TaskListViewMode) {
        _viewMode.value = mode
    }

    fun toggleComplete(task: Task) = completionHandler.toggleComplete(
        task.id,
        task.status,
        task.recurrenceType,
        task.deadline,
    )
    fun completeOccurrence() = completionHandler.completeOccurrence()
    fun completeSeries() = completionHandler.completeSeries()
    fun dismissSeriesChoice() = completionHandler.dismissSeriesChoice()

    fun moveTaskToStatus(
        task: Task,
        targetStatus: TaskStatus
    ) {
        if (targetStatus == task.status) return
        if (targetStatus == TaskStatus.DONE && task.status != TaskStatus.DONE) {
            toggleComplete(task)
        } else {
            mutationLauncher.launch { updateTaskStatusAction(task.id, targetStatus) }
        }
    }

    fun toggleStar(task: Task) {
        mutationLauncher.launch { toggleTaskStarredAction(task.id) }
    }

    private fun categorize(
        tasks: List<Task>,
        today: LocalDate,
    ): List<TaskCategoryGroup> {
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        val next7 = today.plus(7, DateTimeUnit.DAY)
        val startOfToday = startOfDayLocalMillis(today.year, today.monthNumber, today.dayOfMonth)
        val startOfTomorrow =
            startOfDayLocalMillis(tomorrow.year, tomorrow.monthNumber, tomorrow.dayOfMonth)
        val endOfNext7Days = startOfDayLocalMillis(next7.year, next7.monthNumber, next7.dayOfMonth)

        val incomplete = tasks.filter { it.status != TaskStatus.DONE }
        val completed = tasks.filter { it.status == TaskStatus.DONE }

        return listOf(
            TaskCategoryGroup(
                TaskCategory.OVERDUE,
                incomplete.filter { it.deadline != null && it.deadline < startOfToday }
                    .sortedBy { it.deadline },
            ),
            TaskCategoryGroup(
                TaskCategory.TODAY,
                incomplete.filter { it.deadline != null && it.deadline >= startOfToday && it.deadline < startOfTomorrow }
                    .sortedBy { it.deadline },
            ),
            TaskCategoryGroup(
                TaskCategory.NEXT_7_DAYS,
                incomplete.filter { it.deadline != null && it.deadline >= startOfTomorrow && it.deadline < endOfNext7Days }
                    .sortedBy { it.deadline },
            ),
            TaskCategoryGroup(
                TaskCategory.LATER,
                incomplete.filter { it.deadline != null && it.deadline >= endOfNext7Days }
                    .sortedBy { it.deadline },
            ),
            TaskCategoryGroup(
                TaskCategory.NO_DATE,
                incomplete.filter { it.deadline == null }.sortedBy { it.createdAt },
            ),
            TaskCategoryGroup(
                TaskCategory.COMPLETED,
                completed.sortedByDescending { it.updatedAt },
            ),
        )
    }
}

private const val MATRIX_VISIBLE_TASK_LIMIT = 6
