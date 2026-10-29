package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.calendar.IcsParser
import com.udnahc.opentasks.data.extensions.computeDeadlineUtcMillis
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
import kotlin.test.assertNull

class ImportCalendarEventsActionTest {
    @Test
    fun allDayImportNormalizesTaskEndWithoutChangingNoUidReimportIdentity() = runTest {
        val event = IcsParser.parse(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:No UID all-day span
            DTSTART;VALUE=DATE:20260504
            DTEND;VALUE=DATE:20260507
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
        ).single()
        assertNull(event.rawUid)
        val historicalInclusiveEnd = computeDeadlineUtcMillis(2026, 5, 6, 0, 0)
        val exclusiveEnd = computeDeadlineUtcMillis(2026, 5, 7, 0, 0)
        val priorIdentity = ImportedIdentityBatch().nextCalendar(event.copy(endTimeUtcMillis = historicalInclusiveEnd)).canonicalId
        assertEquals(historicalInclusiveEnd, event.endTimeUtcMillis)

        fun actionFor(repository: FakeTaskRepository): ImportCalendarEventsAction {
            val tags = FakeTagRepository()
            return ImportCalendarEventsAction(
                taskRepository = repository,
                categoryRepository = FakeCategoryRepository(),
                tagRepository = tags,
                addTagAction = AddTagAction(tags),
                scheduleTaskRemindersAction = ScheduleTaskRemindersAction(NotificationScheduler(), repository),
            )
        }

        val newRepository = FakeTaskRepository()
        val importNew = actionFor(newRepository)
        assertEquals(1, importNew(listOf(event)))
        val imported = newRepository.inserted.single()
        assertEquals(utcToLocal(exclusiveEnd - 1L), imported.endDeadline)
        assertEquals(priorIdentity, imported.sourceExternalId)
        assertEquals(0, importNew(listOf(event)))

        val historicalTask = imported.copy(id = "historical", endDeadline = utcToLocal(historicalInclusiveEnd))
        val existingRepository = FakeTaskRepository(listOf(historicalTask))
        assertEquals(0, actionFor(existingRepository)(listOf(event)))
        assertTrue(existingRepository.inserted.isEmpty())
        assertEquals(historicalTask, existingRepository.tasks.single())
    }

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
