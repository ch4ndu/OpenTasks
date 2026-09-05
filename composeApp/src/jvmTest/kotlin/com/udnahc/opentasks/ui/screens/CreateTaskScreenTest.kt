package com.udnahc.opentasks.ui.screens

import com.udnahc.opentasks.data.extensions.MILLIS_PER_HOUR
import com.udnahc.opentasks.data.extensions.MILLIS_PER_MINUTE
import com.udnahc.opentasks.data.extensions.startOfDayLocalMillis
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.TaskFormData
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.testutil.testTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreateTaskScreenTest {
    @Test
    fun reopeningAnEditorSeedsEveryPersistedFormField() {
        val deadline = startOfDayLocalMillis(2026, 8, 12) + (9 * 60 * 60 * 1_000L)
        val endDeadline = deadline + (90 * 60 * 1_000L)
        val task = testTask(
            id = "restore-form",
            title = "Duration task",
            content = "Description",
            deadline = deadline,
            endDeadline = endDeadline,
            recurrenceType = RecurrenceType.MONTHLY,
            categoryId = "work",
            section = "This week",
            status = TaskStatus.DONE,
            priority = TaskPriority.HIGH,
        ).copy(
            subtasks = "subtasks",
            isAllDay = true,
            notifyBeforeValue = 3,
            location = "Room 4",
            url = "https://example.com",
            organizer = "owner@example.com",
            eventStatus = "CONFIRMED",
            attendees = "a@example.com",
            durationReminders = "0,-1",
            dateReminders = "60,0",
        )

        val restored = task.toTaskFormData()

        assertEquals(task.title, restored.title)
        assertEquals(task.content, restored.content)
        assertEquals(task.subtasks, restored.subtasks)
        assertEquals(TaskPriority.HIGH, restored.priority)
        assertEquals(deadline, restored.deadline)
        assertEquals(endDeadline, restored.endDeadline)
        assertTrue(restored.isAllDay)
        assertEquals(3, restored.reminderDays)
        assertEquals(RecurrenceType.MONTHLY, restored.recurrence)
        assertEquals("work", restored.categoryId)
        assertEquals("This week", restored.section)
        assertEquals(TaskStatus.DONE, restored.status)
        assertEquals("Room 4", restored.location)
        assertEquals("https://example.com", restored.url)
        assertEquals("owner@example.com", restored.organizer)
        assertEquals("CONFIRMED", restored.eventStatus)
        assertEquals("a@example.com", restored.attendees)
        assertEquals("0,-1", restored.durationReminders)
        assertEquals("60,0", restored.dateReminders)
        assertFalse(restored.pendingImages.isNotEmpty())
    }

    @Test
    fun titleOnlySavePreservesExactRangeAndInProgressStatus() {
        val deadline = startOfDayLocalMillis(2026, 8, 12) + 9 * MILLIS_PER_HOUR + 12_345L
        val endDeadline = startOfDayLocalMillis(2026, 8, 13) + MILLIS_PER_HOUR + 45 * MILLIS_PER_MINUTE + 987L
        val form = TaskFormData(
            title = "Original",
            content = "Body",
            deadline = deadline,
            endDeadline = endDeadline,
            status = TaskStatus.IN_PROGRESS,
        )
        val dateState = initialTaskEditorDateState(form, 0, 0, 0)

        val saved = assertIs<TaskFormBuildResult.Ready>(
            buildTaskFormDataForSave(
                draft = form.copy(title = "Renamed"),
                dateState = dateState,
                status = TaskStatus.IN_PROGRESS,
            ),
        ).formData

        assertEquals("Renamed", saved.title)
        assertEquals(deadline, saved.deadline)
        assertEquals(endDeadline, saved.endDeadline)
        assertEquals(TaskStatus.IN_PROGRESS, saved.status)
    }

    @Test
    fun movingAnOvernightRangePreservesCivilSpanAndExactWallClockTimes() {
        val deadline = startOfDayLocalMillis(2026, 5, 4) + 22 * MILLIS_PER_HOUR + 15 * MILLIS_PER_MINUTE + 543L
        val endDeadline = startOfDayLocalMillis(2026, 5, 5) + MILLIS_PER_HOUR + 45 * MILLIS_PER_MINUTE + 987L
        val state = initialTaskEditorDateState(
            TaskFormData(title = "Overnight", content = "", deadline = deadline, endDeadline = endDeadline),
            0,
            0,
            0,
        )

        val moved = state.selectDate(day = 31, month = 12, year = 2026)

        assertEquals(
            startOfDayLocalMillis(2026, 12, 31) + 22 * MILLIS_PER_HOUR + 15 * MILLIS_PER_MINUTE + 543L,
            moved.deadline,
        )
        assertEquals(
            startOfDayLocalMillis(2027, 1, 1) + MILLIS_PER_HOUR + 45 * MILLIS_PER_MINUTE + 987L,
            moved.endDeadline,
        )
        assertTrue(moved.isValidRange)

        val confirmedWithoutTimeChanges = moved
            .selectStartTime(moved.selectedHour, moved.selectedMinute)
            .selectEndTime(moved.endHour, moved.endMinute)

        assertEquals(moved, confirmedWithoutTimeChanges)
        assertEquals(1, moved.civilDaySpan)
        assertEquals(
            210,
            durationMinutesForCivilSpan(
                daySpan = moved.civilDaySpan,
                startHour = moved.selectedHour,
                startMinute = moved.selectedMinute,
                endHour = moved.endHour,
                endMinute = moved.endMinute,
            ),
        )
    }

    @Test
    fun movingAMultiDayAllDayRangeUsesCivilDaysAcrossLeapMonth() {
        val state = initialTaskEditorDateState(
            TaskFormData(
                title = "Conference",
                content = "",
                deadline = startOfDayLocalMillis(2025, 12, 30),
                endDeadline = startOfDayLocalMillis(2026, 1, 2),
                isAllDay = true,
            ),
            0,
            0,
            0,
        )

        val moved = state.selectDate(day = 27, month = 2, year = 2024)

        assertEquals(startOfDayLocalMillis(2024, 2, 27), moved.deadline)
        assertEquals(startOfDayLocalMillis(2024, 3, 1), moved.endDeadline)
        assertEquals(3, moved.civilDaySpan)
    }

    @Test
    fun timeEditsKeepTheEstablishedDaySpanAndNewDurationsStaySameDay() {
        val overnight = initialTaskEditorDateState(
            TaskFormData(
                title = "Overnight",
                content = "",
                deadline = startOfDayLocalMillis(2026, 5, 4) + 22 * MILLIS_PER_HOUR,
                endDeadline = startOfDayLocalMillis(2026, 5, 5) + MILLIS_PER_HOUR,
            ),
            0,
            0,
            0,
        )
            .selectStartTime(hour = 23, minute = 30)
            .selectEndTime(hour = 2, minute = 15)

        assertEquals(
            startOfDayLocalMillis(2026, 5, 4) + 23 * MILLIS_PER_HOUR + 30 * MILLIS_PER_MINUTE,
            overnight.deadline,
        )
        assertEquals(
            startOfDayLocalMillis(2026, 5, 5) + 2 * MILLIS_PER_HOUR + 15 * MILLIS_PER_MINUTE,
            overnight.endDeadline,
        )

        val sameDay = initialTaskEditorDateState(null, 4, 5, 2026)
            .selectStartTime(hour = 16, minute = 0)
            .selectEndTime(hour = 17, minute = 0)
        assertTrue(sameDay.isValidRange)
        assertEquals(startOfDayLocalMillis(2026, 5, 4) + 17 * MILLIS_PER_HOUR, sameDay.endDeadline)

        val invalid = sameDay.selectEndTime(hour = 15, minute = 0)
        assertFalse(invalid.isValidRange)
        assertIs<TaskFormBuildResult.InvalidDateRange>(
            buildTaskFormDataForSave(TaskFormData(title = "Invalid", content = ""), invalid, TaskStatus.TODO),
        )
    }

    @Test
    fun clearingADateClearsTheCompleteRange() {
        val state = initialTaskEditorDateState(
            TaskFormData(
                title = "Clear me",
                content = "",
                deadline = startOfDayLocalMillis(2026, 5, 4),
                endDeadline = startOfDayLocalMillis(2026, 5, 6),
            ),
            0,
            0,
            0,
        ).clearDate()

        val saved = assertIs<TaskFormBuildResult.Ready>(
            buildTaskFormDataForSave(TaskFormData(title = "Clear me", content = ""), state, TaskStatus.TODO),
        ).formData

        assertNull(saved.deadline)
        assertNull(saved.endDeadline)
    }

    @Test
    fun completionToggleRestoresTheInitialIncompleteStatus() {
        assertEquals(
            TaskStatus.TODO,
            toggleTaskEditorCompletionStatus(TaskStatus.DONE, TaskStatus.TODO),
        )
        assertEquals(
            TaskStatus.IN_PROGRESS,
            toggleTaskEditorCompletionStatus(TaskStatus.DONE, TaskStatus.IN_PROGRESS),
        )
        assertEquals(
            TaskStatus.TODO,
            toggleTaskEditorCompletionStatus(TaskStatus.DONE, TaskStatus.DONE),
        )
        assertEquals(
            TaskStatus.DONE,
            toggleTaskEditorCompletionStatus(TaskStatus.IN_PROGRESS, TaskStatus.IN_PROGRESS),
        )
    }

    @Test
    fun recurringCompletionRestoresTheEntryStatusAfterTheRetainedDraftBecomesDone() {
        val entryRestoreStatus = taskEditorCompletionRestoreStatus(TaskStatus.IN_PROGRESS)
        val retainedDraftStatus = TaskStatus.DONE

        assertEquals(TaskStatus.DONE, retainedDraftStatus)
        assertEquals(
            TaskStatus.IN_PROGRESS,
            toggleTaskEditorCompletionStatus(TaskStatus.DONE, entryRestoreStatus),
        )
    }

    @Test
    fun dateStateSurvivesSaveableRecreationWithoutLosingPrecision() {
        val original = TaskEditorDateState(
            deadline = startOfDayLocalMillis(2026, 5, 4) + 12_345L,
            endDeadline = startOfDayLocalMillis(2026, 5, 7) + 67_890L,
            pendingStartHour = 8,
            pendingStartMinute = 12,
            pendingEndHour = 17,
            pendingEndMinute = 45,
        )

        val restored = taskEditorDateStateFromSaveableValues(original.toSaveableValues())

        assertEquals(original, restored)
    }
}
