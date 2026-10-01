package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskListFilter
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.domain.time.LocalDaySignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/** Declaration order is the precedence: a task appears only in the first group it matches. */
enum class ShortlistSuggestion { IMPORTANT_NOT_URGENT, DUE_TODAY, NO_DATE }

data class ShortlistSnapshot(
    val shortlisted: List<Task> = emptyList(),
    val candidates: List<Task> = emptyList(),
    val suggestions: Map<ShortlistSuggestion, List<Task>> = emptyMap(),
    val dueOutsideCount: Int = 0,
    val overdueIds: Set<String> = emptySet(),
)

class ObserveShortlistUseCase(
    private val repository: TaskRepository,
    private val localDaySignal: LocalDaySignal,
) {
    operator fun invoke(): Flow<ShortlistSnapshot> =
        combine(repository.getAllTasks(), localDaySignal.dates) { tasks, today ->
            val day = TaskFilterDay(today)
            val unfinished = tasks.filter { it.status != TaskStatus.DONE }
            val (shortlisted, candidates) = unfinished.partition { it.isShortlisted }
            val grouped = candidates.groupBy { suggestionFor(it, day) }
            ShortlistSnapshot(
                shortlisted = shortlisted,
                candidates = candidates,
                suggestions = ShortlistSuggestion.entries
                    .mapNotNull { suggestion -> grouped[suggestion]?.let { suggestion to it } }
                    .toMap(),
                dueOutsideCount = candidates.count { TaskListFilter.Today.includes(it, day) },
                overdueIds = unfinished
                    .filter { TaskListFilter.Overdue.includes(it, day) }
                    .mapTo(mutableSetOf()) { it.id },
            )
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    private fun suggestionFor(task: Task, day: TaskFilterDay): ShortlistSuggestion? = when {
        task.priority == TaskPriority.MEDIUM -> ShortlistSuggestion.IMPORTANT_NOT_URGENT
        day.isDueToday(task.deadline) -> ShortlistSuggestion.DUE_TODAY
        task.deadline == null -> ShortlistSuggestion.NO_DATE
        else -> null
    }
}
