package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.model.*
import com.udnahc.opentasks.testutil.testTask
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WeeklyReviewProjectionTest {
    @Test
    fun reviewUsesCivilDatesAndNeverResetsTheShortlist() {
        val today = LocalDate(2026, 10, 2)
        val tasks = listOf(
            testTask(id = "inbox", categoryId = AppConstants.DEFAULT_INBOX_ID, priority = TaskPriority.NONE),
            testTask(id = "overdue", categoryId = "work", deadline = startOfDayLocalMillis(2026, 10, 1)),
            testTask(id = "today", categoryId = "work", deadline = startOfDayLocalMillis(2026, 10, 2)),
            testTask(id = "important", categoryId = "work", priority = TaskPriority.MEDIUM).copy(isShortlisted = true),
            testTask(id = "done", status = TaskStatus.DONE).copy(isShortlisted = true),
        )
        assertEquals(listOf("inbox"), reviewTasks(tasks, ReviewStep.INBOX, today).map { it.id })
        assertEquals(listOf("overdue"), reviewTasks(tasks, ReviewStep.OVERDUE, today).map { it.id })
        assertEquals(listOf("important"), reviewTasks(tasks, ReviewStep.IMPORTANT, today).map { it.id })
        assertEquals(listOf("important"), reviewTasks(tasks, ReviewStep.SHORTLIST, LocalDate(2026, 10, 3)).map { it.id })
    }
}
