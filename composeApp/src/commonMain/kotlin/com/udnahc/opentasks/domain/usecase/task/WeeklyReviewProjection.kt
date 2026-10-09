package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.*
import kotlinx.datetime.LocalDate

enum class ReviewStep { INBOX, OVERDUE, IMPORTANT, SHORTLIST }

internal fun reviewTasks(tasks: List<Task>, step: ReviewStep, today: LocalDate): List<Task> {
    val day = TaskFilterDay(today)
    return tasks.filter { task ->
        !task.isDeleted && task.status != TaskStatus.DONE && when (step) {
            ReviewStep.INBOX -> task.categoryId == AppConstants.DEFAULT_INBOX_ID
            ReviewStep.OVERDUE -> task.deadline?.let { it < day.startOfToday } == true
            ReviewStep.IMPORTANT -> task.deadline == null && task.priority in setOf(TaskPriority.HIGH, TaskPriority.MEDIUM)
            ReviewStep.SHORTLIST -> task.isShortlisted
        }
    }.sortedFor(TaskSortOption.BY_PRIORITY)
}
