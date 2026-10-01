package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskListFilter
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

internal class TaskFilterDay(today: LocalDate) {
    val startOfToday: Long = today.startOfDayMillis()
    val startOfTomorrow: Long = today.plus(1, DateTimeUnit.DAY).startOfDayMillis()

    fun isDueToday(deadline: Long?): Boolean =
        deadline != null && deadline >= startOfToday && deadline < startOfTomorrow
    val endOfWeek: Long = today.plus(7, DateTimeUnit.DAY).startOfDayMillis()
}

private fun LocalDate.startOfDayMillis(): Long =
    startOfDayLocalMillis(year, monthNumber, dayOfMonth)

internal fun TaskListFilter.includes(task: Task, day: TaskFilterDay): Boolean {
    val isDone = task.status == TaskStatus.DONE
    val deadline = task.deadline
    return when (this) {
        is TaskListFilter.Category -> task.categoryId == id
        TaskListFilter.Shortlist -> !isDone && task.isShortlisted
        TaskListFilter.Starred -> task.isStarred
        TaskListFilter.Today -> if (isDone) {
            task.completedAt != null &&
                task.completedAt >= day.startOfToday &&
                task.completedAt < day.startOfTomorrow
        } else {
            deadline != null && deadline < day.startOfTomorrow
        }
        TaskListFilter.Overdue -> !isDone && deadline != null && deadline < day.startOfToday
        TaskListFilter.NoDate -> !isDone && deadline == null
        TaskListFilter.HighPriority -> !isDone && task.priority == TaskPriority.HIGH
        TaskListFilter.DueThisWeek -> !isDone && deadline != null &&
            deadline >= day.startOfToday && deadline < day.endOfWeek
    }
}
