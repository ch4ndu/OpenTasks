package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.testutil.testTask
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskSortingTest {
    @Test
    fun sortedByStatusAndDeadlinePutsActiveDatedTasksBeforeNoDateAndDoneTasks() {
        val tasks = listOf(
            testTask(id = "done-early", status = TaskStatus.DONE, deadline = 1L),
            testTask(id = "no-date", status = TaskStatus.TODO, deadline = null),
            testTask(id = "later", status = TaskStatus.IN_PROGRESS, deadline = 30L),
            testTask(id = "soon", status = TaskStatus.TODO, deadline = 10L),
            testTask(id = "done-late", status = TaskStatus.DONE, deadline = 40L),
        )

        assertEquals(
            listOf("soon", "later", "no-date", "done-early", "done-late"),
            tasks.sortedByStatusAndDeadline().map { it.id },
        )
    }
}
