package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.AppConstants
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskListFilter
import com.udnahc.opentasks.data.model.TaskSortOption
import com.udnahc.opentasks.domain.action.category.AddCategoryAction
import com.udnahc.opentasks.domain.action.task.TaskCompletionHandler
import com.udnahc.opentasks.domain.action.task.ToggleTaskCompleteAction
import com.udnahc.opentasks.domain.action.task.ToggleTaskStarredAction
import com.udnahc.opentasks.domain.action.task.UpdateSectionAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.action.settings.SaveTaskSortOptionAction
import com.udnahc.opentasks.domain.usecase.settings.ObserveTaskSortOptionUseCase
import com.udnahc.opentasks.data.extensions.MILLIS_PER_DAY
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.extensions.todayLocal
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskListViewMode
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.domain.action.settings.SaveTaskListViewModeAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskStatusAction
import com.udnahc.opentasks.domain.usecase.settings.ObserveTaskListViewModeUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveAllTasksUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTasksForCategoryUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveTodayTasksUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskListViewModel(
    observeTasksForCategory: ObserveTasksForCategoryUseCase,
    observeAllTasks: ObserveAllTasksUseCase,
    observeAllCategories: ObserveAllCategoriesUseCase,
    toggleTaskCompleteAction: ToggleTaskCompleteAction,
    private val toggleTaskStarredAction: ToggleTaskStarredAction,
    private val addCategoryAction: AddCategoryAction,
    observeTaskSortOption: ObserveTaskSortOptionUseCase,
    private val saveTaskSortOptionAction: SaveTaskSortOptionAction,
    observeTodayTasks: ObserveTodayTasksUseCase,
    private val updateSectionAction: UpdateSectionAction,
    observeTaskListViewMode: ObserveTaskListViewModeUseCase,
    private val saveTaskListViewModeAction: SaveTaskListViewModeAction,
    private val updateTaskStatusAction: UpdateTaskStatusAction,
) : ViewModel() {

    data class SectionGroup(val name: String?, val tasks: List<Task>)

    private val _filter = MutableStateFlow<TaskListFilter>(
        TaskListFilter.Category(AppConstants.DEFAULT_INBOX_ID)
    )
    val currentFilter: StateFlow<TaskListFilter> = _filter

    val selectedCategoryId: StateFlow<String> = _filter
        .map { (it as? TaskListFilter.Category)?.id ?: "" }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppConstants.DEFAULT_INBOX_ID)

    private val completionHandler = TaskCompletionHandler(toggleTaskCompleteAction, viewModelScope)
    val taskPendingSeriesChoice: StateFlow<Task?> = completionHandler.taskPendingSeriesChoice

    @OptIn(ExperimentalCoroutinesApi::class)
    val tasksForSelectedCategory: StateFlow<List<Task>> = _filter
        .flatMapLatest { filter ->
            when (filter) {
                is TaskListFilter.Category -> observeTasksForCategory(selectedCategoryId)
                is TaskListFilter.Starred -> observeAllTasks().map { tasks ->
                    tasks.filter { it.isStarred }
                }
                is TaskListFilter.Today -> observeTodayTasks().map { todayTasks ->
                    todayTasks.overdue + todayTasks.today + todayTasks.completedToday
                }
                is TaskListFilter.Overdue -> observeAllTasks().map { tasks ->
                    val today = todayLocal()
                    val startOfToday = startOfDayLocalMillis(today.year, today.monthNumber, today.dayOfMonth)
                    tasks.filter { it.status != TaskStatus.DONE && it.deadline != null && it.deadline < startOfToday }
                }
                is TaskListFilter.NoDate -> observeAllTasks().map { tasks ->
                    tasks.filter { it.status != TaskStatus.DONE && it.deadline == null }
                }
                is TaskListFilter.HighPriority -> observeAllTasks().map { tasks ->
                    tasks.filter { it.status != TaskStatus.DONE && it.priority == TaskPriority.HIGH }
                }
                is TaskListFilter.DueThisWeek -> observeAllTasks().map { tasks ->
                    val today = todayLocal()
                    val startOfToday = startOfDayLocalMillis(today.year, today.monthNumber, today.dayOfMonth)
                    val endOfWeek = startOfToday + 7 * MILLIS_PER_DAY
                    tasks.filter { it.status != TaskStatus.DONE && it.deadline != null && it.deadline >= startOfToday && it.deadline < endOfWeek }
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sortOption: StateFlow<TaskSortOption> = observeTaskSortOption()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskSortOption.RECENTLY_UPDATED)

    val activeTasksForSelectedCategory: StateFlow<List<Task>> =
        combine(tasksForSelectedCategory, sortOption) { tasks, sort ->
            val active = tasks.filter { it.status != TaskStatus.DONE }
            sortTasks(active, sort)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasksForSelectedCategory: StateFlow<List<Task>> = tasksForSelectedCategory
        .map { tasks -> tasks.filter { it.status == TaskStatus.DONE } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedActiveTasks: StateFlow<List<SectionGroup>> =
        activeTasksForSelectedCategory.map { tasks ->
            val grouped = tasks.groupBy { it.section }
            val sections = mutableListOf<SectionGroup>()
            // Unsectioned tasks first (section = null)
            grouped[null]?.let { sections.add(SectionGroup(null, it)) }
            // Then named sections sorted alphabetically
            grouped.keys.filterNotNull().sorted().forEach { name ->
                grouped[name]?.let { sectionTasks ->
                    sections.add(SectionGroup(name, sectionTasks))
                }
            }
            sections
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _categorySearchQuery = MutableStateFlow("")
    val categorySearchQuery: StateFlow<String> = _categorySearchQuery

    val filteredCategories: StateFlow<List<Category>> =
        combine(categories, _categorySearchQuery) { categories, query ->
            if (query.isBlank()) categories
            else categories.filter { it.name.contains(query, ignoreCase = true) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setCategorySearchQuery(query: String) {
        _categorySearchQuery.value = query
    }

    fun selectFilter(filter: TaskListFilter) { _filter.value = filter }

    fun selectCategory(categoryId: String) { selectFilter(TaskListFilter.Category(categoryId)) }

    fun toggleComplete(task: Task) = completionHandler.toggleComplete(task)
    fun completeOccurrence() = completionHandler.completeOccurrence()
    fun completeSeries() = completionHandler.completeSeries()
    fun dismissSeriesChoice() = completionHandler.dismissSeriesChoice()

    fun toggleStar(task: Task) {
        viewModelScope.launch(Dispatchers.IO) { toggleTaskStarredAction(task) }
    }

    fun addCategory(name: String) {
        viewModelScope.launch(Dispatchers.IO) { addCategoryAction(name) }
    }

    fun setSortOption(option: TaskSortOption) {
        viewModelScope.launch(Dispatchers.IO) { saveTaskSortOptionAction(option) }
    }

    fun renameSection(oldName: String, newName: String) {
        val tasks = groupedActiveTasks.value.find { it.name == oldName }?.tasks ?: return
        viewModelScope.launch(Dispatchers.IO) { updateSectionAction.renameSection(tasks, newName) }
    }

    fun deleteSection(sectionName: String) {
        val tasks = groupedActiveTasks.value.find { it.name == sectionName }?.tasks ?: return
        viewModelScope.launch(Dispatchers.IO) { updateSectionAction.clearSection(tasks) }
    }

    val viewMode: StateFlow<TaskListViewMode> = observeTaskListViewMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskListViewMode.LIST)

    val tasksByStatus: StateFlow<Map<TaskStatus, List<Task>>> =
        combine(tasksForSelectedCategory, sortOption) { tasks, sort ->
            TaskStatus.entries.associateWith { status ->
                sortTasks(tasks.filter { it.status == status }, sort)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setViewMode(mode: TaskListViewMode) {
        viewModelScope.launch(Dispatchers.IO) { saveTaskListViewModeAction(mode) }
    }

    fun moveTaskToStatus(task: Task, targetStatus: TaskStatus) {
        if (targetStatus == task.status) return
        if (targetStatus == TaskStatus.DONE && task.status != TaskStatus.DONE) {
            completionHandler.toggleComplete(task)
        } else {
            viewModelScope.launch(Dispatchers.IO) { updateTaskStatusAction(task, targetStatus) }
        }
    }

    private fun sortTasks(tasks: List<Task>, sort: TaskSortOption): List<Task> = when (sort) {
        TaskSortOption.RECENTLY_UPDATED -> tasks.sortedByDescending { it.updatedAt }
        TaskSortOption.BY_DEADLINE -> tasks.sortedWith(compareBy(nullsLast()) { it.deadline })
        TaskSortOption.BY_PRIORITY -> tasks.sortedBy { it.priority.ordinal }
        TaskSortOption.BY_TITLE -> tasks.sortedBy { it.title.lowercase() }
    }
}
