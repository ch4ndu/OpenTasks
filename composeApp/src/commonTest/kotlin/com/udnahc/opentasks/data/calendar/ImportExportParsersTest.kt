package com.udnahc.opentasks.data.calendar

import com.udnahc.opentasks.data.extensions.localMillisToLocalDate
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.testutil.testTask
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportExportParsersTest {
    @Test
    fun csvParserHandlesQuotedMultilineFieldsPriorityReminderAndRecurrence() {
        val csv = """
            Date:,2026-05-04
            "Folder Name","List Name","Title","Content","Is All Day","Start Date","Due Date","Reminder","Repeat","Priority","Status","Created Time","Completed Time","Order","Timezone","Is Floating"
            "","Work","Write, tests","Line one
            Line two","false","2026-05-04T15:00:00+0000","2026-05-04T16:00:00+0000","-PT30M
            -PT0S","RRULE:FREQ=WEEKLY","5","2","2026-05-01T12:00:00+0000","","0","",""
        """.trimIndent()

        val task = CsvParser.parse(csv).single()

        assertEquals("Write, tests", task.title)
        assertEquals("Line one\nLine two", task.content)
        assertEquals("Work", task.listName)
        assertEquals(TaskPriority.HIGH, task.priority)
        assertEquals(true, task.isCompleted)
        assertEquals("30,0", task.durationReminders)
        assertEquals(RecurrenceType.WEEKLY, task.recurrenceType)
    }

    @Test
    fun csvGeneratorRoundTripsParsableTaskFields() {
        val task = testTask(
            id = "task-1",
            title = "Export me",
            content = "Has, comma",
            categoryId = "work",
            priority = TaskPriority.MEDIUM,
            deadline = 1_778_000_000_000L,
            endDeadline = 1_778_003_600_000L,
            durationReminders = "60,0",
            recurrenceType = RecurrenceType.MONTHLY,
            status = TaskStatus.DONE,
            createdAt = 1_777_900_000_000L,
        ).copy(completedAt = 1_778_004_200_000L)

        val csv = CsvGenerator.generate(listOf(task), listOf(Category(id = "work", name = "Work")))
        val parsed = CsvParser.parse(csv).single()

        assertEquals(task.title, parsed.title)
        assertEquals(task.content, parsed.content)
        assertEquals("Work", parsed.listName)
        assertEquals(TaskPriority.MEDIUM, parsed.priority)
        assertEquals(true, parsed.isCompleted)
        assertEquals(RecurrenceType.MONTHLY, parsed.recurrenceType)
        assertEquals("60,0", parsed.durationReminders)
        assertEquals(task.completedAt, parsed.completedAt)
    }

    @Test
    fun csvParserPreservesCompletionTimeAndDueDateWithoutStartDate() {
        val csv = """
            "Folder Name","List Name","Title","Content","Is All Day","Start Date","Due Date","Reminder","Repeat","Priority","Status","Created Time","Completed Time","Order","Timezone","Is Floating"
            "","Inbox","Done","","false","","2026-05-04T16:00:00+0000","","","0","2","2026-05-01T12:00:00+0000","2026-05-04T17:00:00+0000","0","",""
        """.trimIndent()

        val parsed = CsvParser.parse(csv).single()

        assertEquals(Instant.parse("2026-05-04T16:00:00Z").toEpochMilliseconds(), parsed.dueDate)
        assertEquals(Instant.parse("2026-05-04T17:00:00Z").toEpochMilliseconds(), parsed.completedAt)
        assertTrue(parsed.isCompleted)
    }

    @Test
    fun icsParserHandlesAllDayAndEscapedEventMetadata() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            X-WR-CALNAME:Team Calendar
            BEGIN:VEVENT
            UID:event-1
            SUMMARY:Planning\, Review
            DESCRIPTION:Line one\nLine two
            DTSTART;VALUE=DATE:20260504
            DTEND;VALUE=DATE:20260505
            LOCATION:Room\; A
            ORGANIZER;CN="Murali":mailto:m@example.com
            ATTENDEE;CN="Alex":mailto:a@example.com
            STATUS:CONFIRMED
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val event = IcsParser.parse(ics).single()

        assertEquals("ics_event-1", event.externalId)
        assertEquals("Planning, Review", event.title)
        assertEquals("Line one\nLine two", event.description)
        assertEquals("Team Calendar", event.calendarName)
        assertEquals(true, event.isAllDay)
        assertEquals("2026-05-04", localMillisToLocalDate(utcToLocal(event.startTimeUtcMillis)).toString())
        assertEquals("Room; A", event.location)
        assertEquals("MURALI", event.organizer)
        assertEquals(listOf("ALEX"), event.attendees)
        assertEquals("Confirmed", event.status)
    }

    @Test
    fun icsGeneratorSkipsTasksWithoutMeaningfulTimeAndEscapesText() {
        val ics = IcsGenerator.generate(
            listOf(
                testTask(id = "skip", title = "Skip", createdAt = 0L),
                testTask(id = "keep", title = "Title, with comma", content = "Line\nTwo", deadline = 1_778_000_000_000L),
            )
        )

        assertTrue("UID:keep" in ics)
        assertTrue("UID:skip" !in ics)
        assertTrue("SUMMARY:Title\\, with comma" in ics)
        assertTrue("DESCRIPTION:Line\\nTwo" in ics)
    }
}
