package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.TaskListFilter
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.time.LocalDaySignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

val taskOverviewSmartFilters: List<TaskListFilter> = listOf(
    TaskListFilter.Today,
    TaskListFilter.Overdue,
    TaskListFilter.Starred,
    TaskListFilter.NoDate,
    TaskListFilter.HighPriority,
    TaskListFilter.DueThisWeek,
)

private val countedFilters = taskOverviewSmartFilters + TaskListFilter.Shortlist

/** Unfinished-task counts keyed by smart filter, Shortlist, and `TaskListFilter.Category`. */
class ObserveTaskOverviewCountsUseCase(
    private val repository: TaskRepository,
    private val localDaySignal: LocalDaySignal,
) {
    operator fun invoke(): Flow<Map<TaskListFilter, Int>> =
        combine(repository.getAllTasks(), localDaySignal.dates) { tasks, today ->
            val day = TaskFilterDay(today)
            val counts = mutableMapOf<TaskListFilter, Int>()
            tasks.filter { it.status != TaskStatus.DONE }.forEach { task ->
                countedFilters.forEach { filter ->
                    if (filter.includes(task, day)) counts[filter] = (counts[filter] ?: 0) + 1
                }
                val category = TaskListFilter.Category(task.categoryId)
                counts[category] = (counts[category] ?: 0) + 1
            }
            counts.toMap()
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
}
