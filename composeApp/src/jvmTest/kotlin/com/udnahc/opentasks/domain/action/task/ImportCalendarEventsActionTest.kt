package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.notification.NotificationScheduler
import com.udnahc.opentasks.domain.action.tag.AddTagAction
import com.udnahc.opentasks.testutil.FakeCategoryRepository
import com.udnahc.opentasks.testutil.FakeTagRepository
import com.udnahc.opentasks.testutil.FakeTaskRepository
import com.udnahc.opentasks.testutil.testCalendarEvent
import com.udnahc.opentasks.testutil.testTask
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportCalendarEventsActionTest {
    @Test
    fun importsTimedEventWithEndDeadlineAndMetadata() = runTest {
        val taskRepository = FakeTaskRepository()
        val categoryRepository = FakeCategoryRepository()
        val tagRepository = FakeTagRepository()
        val action = ImportCalendarEventsAction(
            taskRepository = taskRepository,
            categoryRepository = categoryRepository,
            tagRepository = tagRepository,
            addTagAction = AddTagAction(tagRepository),
            scheduleTaskRemindersAction = ScheduleTaskRemindersAction(
                NotificationScheduler(),
                taskRepository,
            ),
        )
        val event = testCalendarEvent(
            externalId = "calendar-event",
            title = "Planning",
            description = "Agenda",
            startTimeUtcMillis = 1_778_000_000_000L,
            endTimeUtcMillis = 1_778_003_600_000L,
            calendarName = "",
            isAllDay = false,
        )

        val count = action(listOf(event))

        assertEquals(1, count)
        val inserted = taskRepository.inserted.single()
        assertEquals("Planning", inserted.title)
        assertEquals(utcToLocal(event.startTimeUtcMillis), inserted.deadline)
        assertEquals(utcToLocal(event.endTimeUtcMillis ?: 0L), inserted.endDeadline)
        assertEquals(event.externalId, inserted.sourceExternalId)
        assertTrue("Agenda" in inserted.content)
        assertEquals(1, categoryRepository.inserted.size)
        assertEquals(1, tagRepository.insertedTaskTags.size)
    }

    @Test
    fun skipsCalendarEventWhenExternalIdAlreadyExists() = runTest {
        val existing = testTask(id = "existing", sourceExternalId = "calendar-event")
        val taskRepository = FakeTaskRepository(listOf(existing))
        val categoryRepository = FakeCategoryRepository()
        val tagRepository = FakeTagRepository()
        val action = ImportCalendarEventsAction(
            taskRepository = taskRepository,
            categoryRepository = categoryRepository,
            tagRepository = tagRepository,
            addTagAction = AddTagAction(tagRepository),
            scheduleTaskRemindersAction = ScheduleTaskRemindersAction(
                NotificationScheduler(),
                taskRepository,
            ),
        )

        val count = action(listOf(testCalendarEvent(externalId = "calendar-event", calendarName = "")))

        assertEquals(0, count)
        assertTrue(taskRepository.inserted.isEmpty())
        assertTrue(tagRepository.insertedTaskTags.isEmpty())
    }
}
