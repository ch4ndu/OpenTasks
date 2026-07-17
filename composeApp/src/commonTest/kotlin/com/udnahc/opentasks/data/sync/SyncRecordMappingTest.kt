package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.model.CountdownType
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.data.sync.records.taskTagLocalId
import com.udnahc.opentasks.data.sync.records.toCategory
import com.udnahc.opentasks.data.sync.records.toCategoryRecord
import com.udnahc.opentasks.data.sync.records.toCountdown
import com.udnahc.opentasks.data.sync.records.toCountdownRecord
import com.udnahc.opentasks.data.sync.records.toNote
import com.udnahc.opentasks.data.sync.records.toNoteRecord
import com.udnahc.opentasks.data.sync.records.toTag
import com.udnahc.opentasks.data.sync.records.toTagRecord
import com.udnahc.opentasks.data.sync.records.toTask
import com.udnahc.opentasks.data.sync.records.toTaskRecord
import com.udnahc.opentasks.data.sync.records.TaskRecord
import com.udnahc.opentasks.data.sync.records.toTaskTag
import com.udnahc.opentasks.data.sync.records.toTaskTagRecord
import com.udnahc.opentasks.testutil.testCategory
import com.udnahc.opentasks.testutil.testCountdown
import com.udnahc.opentasks.testutil.testNote
import com.udnahc.opentasks.testutil.testTag
import com.udnahc.opentasks.testutil.testTask
import com.udnahc.opentasks.testutil.testTaskTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class SyncRecordMappingTest {
    @Test
    fun taskRecordRoundTripPreservesSyncedFields() {
        val task = testTask(
            id = "task-1",
            title = "Task",
            priority = TaskPriority.HIGH,
            status = TaskStatus.IN_PROGRESS,
            deadline = 1_000L,
            endDeadline = 2_000L,
            isStarred = true,
            section = "Doing",
            sourceExternalId = "external",
            isDeleted = true,
            createdAt = 10L,
            updatedAt = 20L,
        ).copy(
            recurrenceType = com.udnahc.opentasks.data.model.RecurrenceType.MONTHLY,
            recurrenceAnchorDay = 31,
            completedAt = 1_500L,
        )

        val record = task.toTaskRecord()
        val roundTrip = record.toTask()

        assertEquals("task-1", record.localId)
        assertEquals(TaskPriority.HIGH, roundTrip.priority)
        assertEquals(TaskStatus.IN_PROGRESS, roundTrip.status)
        assertTrue(roundTrip.isSynced)
        assertEquals(task.isDeleted, roundTrip.isDeleted)
        assertEquals(task.updatedAt, roundTrip.updatedAt)
        assertEquals(31, record.recurrenceAnchorDay)
        assertEquals(31, roundTrip.recurrenceAnchorDay)
        assertEquals(1_500L, record.completedAt)
        assertEquals(1_500L, roundTrip.completedAt)
    }

    @Test
    fun taskRecordTreatsNullSubtasksAsEmpty() {
        val record = Json.decodeFromString<TaskRecord>(
            """
            {
              "id": "pb-task",
              "localId": "task-1",
              "title": "Task",
              "subtasks": null
            }
            """.trimIndent(),
        )

        assertEquals("", record.toTask().subtasks)
    }

    @Test
    fun nonTaskRecordRoundTripsPreserveLocalIdsAndSyncFlags() {
        assertTrue(testCategory(id = "cat", name = "Inbox").toCategoryRecord().toCategory().isSynced)
        assertEquals("Inbox", testCategory(id = "cat", name = "Inbox").toCategoryRecord().toCategory().name)

        assertTrue(testNote(id = "note", title = "N").toNoteRecord().toNote().isSynced)
        assertEquals("N", testNote(id = "note", title = "N").toNoteRecord().toNote().title)

        assertTrue(testTag(id = "tag", name = "T").toTagRecord().toTag().isSynced)
        assertEquals("T", testTag(id = "tag", name = "T").toTagRecord().toTag().name)

        val countdown = testCountdown(id = "count", title = "C", countdownType = CountdownType.BIRTHDAY)
        assertEquals(CountdownType.BIRTHDAY, countdown.toCountdownRecord().toCountdown().countdownType)

        val taskTag = testTaskTag(taskId = "task", tagId = "tag")
        assertEquals("task:tag", taskTag.toTaskTagRecord().localId)
        assertEquals("task:tag", taskTagLocalId("task", "tag"))
        assertTrue(taskTag.toTaskTagRecord().toTaskTag().isSynced)
    }
}
