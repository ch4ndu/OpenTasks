package com.udnahc.opentasks.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flatMapLatest
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
import com.udnahc.opentasks.domain.action.task.TaskWriteResult
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskStarredAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskStatusAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.attachment.ObserveTaskImageSummariesUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksByPriorityUseCase
import com.udnahc.opentasks.domain.usecase.task.PlainTaskDueTextProvider
import com.udnahc.opentasks.domain.usecase.task.TaskDueTextProvider
import com.udnahc.opentasks.domain.usecase.task.taskPreviewTexts
import com.udnahc.opentasks.domain.usecase.task.distinctDueTextInputs
import com.udnahc.opentasks.domain.time.LocalDaySignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class MatrixLoadState { LOADING, READY, ERROR }

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

    class PriorityProjection(
        tasks: List<Task> = emptyList(),
        val visibleTasks: List<Task> = emptyList(),
        val totalCount: Int = tasks.size,
    ) {
        @Deprecated("Compatibility only; production consumers use visibleTasks or selected-priority flows")
        val tasks: List<Task> = tasks

        val hasMore: Boolean get() = totalCount > visibleTasks.size

        override fun equals(other: Any?): Boolean =
            other is PriorityProjection &&
                visibleTasks == other.visibleTasks &&
                totalCount == other.totalCount

        override fun hashCode(): Int = 31 * visibleTasks.hashCode() + totalCount
    }

    data class ReimaginedState(
        val projections: Map<TaskPriority, PriorityProjection> = emptyMap(),
        val dueText: Map<String, String> = emptyMap(),
        val totalCount: Int = 0,
    )

    private val readGeneration = MutableStateFlow(0L)
    private val _loadState = MutableStateFlow(MatrixLoadState.LOADING)
    // The filter-free overview needs only the task source; category names are used by Classic detail/creation.
    val loadState: StateFlow<MatrixLoadState> = _loadState

    private val _categoryNamesReadFailed = MutableStateFlow(false)
    val categoryNamesReadFailed: StateFlow<Boolean> = _categoryNamesReadFailed
    private val categoryLoadState = MutableStateFlow(MatrixLoadState.LOADING)
    val classicQuadrantListLoadState: StateFlow<MatrixLoadState> = combine(_loadState, categoryLoadState) { tasks, categories ->
        when {
            tasks == MatrixLoadState.ERROR || categories == MatrixLoadState.ERROR -> MatrixLoadState.ERROR
            tasks == MatrixLoadState.LOADING || categories == MatrixLoadState.LOADING -> MatrixLoadState.LOADING
            else -> MatrixLoadState.READY
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MatrixLoadState.LOADING)

    fun retryLoad() {
        _loadState.value = MatrixLoadState.LOADING
        _categoryNamesReadFailed.value = false
        categoryLoadState.value = MatrixLoadState.LOADING
        readGeneration.value += 1L
    }

    private val _selectedPriority = MutableStateFlow(TaskPriority.HIGH)
    private val _viewMode = MutableStateFlow(TaskListViewMode.LIST)
    private val taskMutationFailureEvents = TaskMutationFailureEventStore()
    val taskMutationFailureEvent = taskMutationFailureEvents.event
    private val mutationLauncher = ForegroundMutationLauncher(
        accountBoundaryExecutor,
        viewModelScope,
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
    val viewMode: StateFlow<TaskListViewMode> = _viewMode

    val taskImageSummaries: StateFlow<Map<String, AttachmentSummary>> = observeTaskImageSummaries()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val categoryNames: StateFlow<Map<String, String>> = readGeneration.flatMapLatest {
        observeAllCategories().map { cats -> cats.associate { it.id to it.name } }
            .onStart {
                _categoryNamesReadFailed.value = false
                categoryLoadState.value = MatrixLoadState.LOADING
            }
            .onEach { categoryLoadState.value = MatrixLoadState.READY }
            .catch { error ->
                if (error is CancellationException) throw error
                _categoryNamesReadFailed.value = true
                categoryLoadState.value = MatrixLoadState.ERROR
            }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val tasksByPriority: StateFlow<Map<TaskPriority, List<Task>>> = readGeneration.flatMapLatest {
        observeTasksByPriority()
            .onEach { _loadState.value = MatrixLoadState.READY }
            .catch { error ->
                if (error is CancellationException) throw error
                _loadState.value = MatrixLoadState.ERROR
            }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val formattingContexts = localDaySignal.formattingContexts { taskDueTextProvider.formattingContextKey }

    val reimaginedState = flow {
        var previousInputs: Map<String, Long?>? = null
        var previousContext: String? = null
        var dueText = emptyMap<String, String>()
        emitAll(combine(tasksByPriority, formattingContexts) { groups, context ->
            val projections = groups.mapValues { (_, tasks) -> PriorityProjection(visibleTasks = tasks.take(7), totalCount = tasks.size) }
            val visibleTasks = projections.values.flatMap(PriorityProjection::visibleTasks)
            val inputs = visibleTasks.associate { it.id to it.deadline }
            if (inputs != previousInputs || context != previousContext) {
                dueText = visibleTasks.associate { it.id to taskDueTextProvider.matrixDueText(it) }
                previousInputs = inputs
                previousContext = context
            }
            // Publish current callback entities and their matching labels in the same snapshot.
            ReimaginedState(
                totalCount = groups.values.sumOf { it.size },
                projections = projections,
                dueText = dueText,
            )
        })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReimaginedState())

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
                    totalCount = tasks.size,
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val taskContentPreviews: StateFlow<Map<String, String>> = tasksForSelectedPriority
        .taskPreviewTexts()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val taskDueTextById: StateFlow<Map<String, String>> = combine(
        priorityProjections.map { it.values.flatMap(PriorityProjection::visibleTasks) }.distinctDueTextInputs(),
        formattingContexts,
    ) { tasks, _ ->
        tasks.associate { task -> task.id to taskDueTextProvider.matrixDueText(task) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val selectedPriorityTaskDueTextById: StateFlow<Map<String, String>> = combine(
        tasksForSelectedPriority.distinctDueTextInputs(),
        formattingContexts,
    ) { tasks, _ ->
        tasks.associate { task -> task.id to taskDueTextProvider.matrixDueText(task) }
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

    fun consumeTaskMutationFailureEvent(event: TaskMutationFailureEvent): Boolean =
        taskMutationFailureEvents.consume(event)

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
