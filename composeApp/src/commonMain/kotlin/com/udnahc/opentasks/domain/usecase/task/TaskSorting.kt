package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskSortOption
import com.udnahc.opentasks.data.model.TaskStatus

fun List<Task>.sortedByStatusAndDeadline(): List<Task> =
    sortedWith(
        compareBy<Task> { it.status == TaskStatus.DONE }
            .thenBy { it.deadline == null }
            .thenBy { it.deadline ?: Long.MAX_VALUE }
    )

fun List<Task>.sortedFor(sort: TaskSortOption): List<Task> = when (sort) {
    TaskSortOption.RECENTLY_UPDATED -> sortedByDescending { it.updatedAt }
    TaskSortOption.BY_DEADLINE -> sortedWith(compareBy(nullsLast()) { it.deadline })
    TaskSortOption.BY_PRIORITY -> sortedBy { it.priority.ordinal }
    TaskSortOption.BY_TITLE -> sortedWith { first, second ->
        first.title.compareTo(second.title, ignoreCase = true)
    }
}
