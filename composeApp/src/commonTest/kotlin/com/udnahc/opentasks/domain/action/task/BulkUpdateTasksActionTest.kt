package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.model.*
import com.udnahc.opentasks.testutil.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BulkUpdateTasksActionTest {
    @Test
    fun movingToCurrentListIsANoOpAndMovingElsewhereClearsOnlyTheSection() = runTest {
        val original = testTask(id = "task", categoryId = "work", section = "Today").copy(sourceNoteId = "note")
        val repository = FakeTaskRepository(listOf(original))
        val categories = FakeCategoryRepository(listOf(testCategory(id = "work"), testCategory(id = "home")))
        val action = BulkUpdateTasksAction(repository, categories)
        action(setOf("task"), BulkTaskChange.Move("work"))
        assertEquals(original, repository.getTaskById("task"))
        action(setOf("task"), BulkTaskChange.Move("home"))
        val moved = assertNotNull(repository.getTaskById("task"))
        assertEquals("home", moved.categoryId)
        assertNull(moved.section)
        assertEquals("note", moved.sourceNoteId)
        assertEquals(original.deadline, moved.deadline)
    }

    @Test
    fun reportsMissingRowsAndPreservesUnrelatedFields() = runTest {
        val repository = FakeTaskRepository()
        val categories = FakeCategoryRepository()
        val task = testTask(id = "task", title = "Current title", content = "Current content").copy(
            sourceNoteId = "note",
            isShortlisted = true,
            isUrgent = true,
            isImportant = false,
        )
        repository.insert(task)
        val action = BulkUpdateTasksAction(repository, categories)
        val result = action(setOf("task", "missing"), BulkTaskChange.Priority(TaskPriority.MEDIUM))
        assertEquals(setOf("task"), result.succeeded)
        assertEquals(setOf("missing"), result.failed)
        val updated = assertNotNull(repository.getTaskById("task"))
        assertEquals(task.title, updated.title)
        assertEquals(task.content, updated.content)
        assertEquals("note", updated.sourceNoteId)
        assertTrue(updated.isShortlisted)
        assertEquals(TaskPriority.MEDIUM, updated.priority)
        assertEquals(task.isUrgent, updated.isUrgent)
        assertEquals(task.isImportant, updated.isImportant)
    }
}
