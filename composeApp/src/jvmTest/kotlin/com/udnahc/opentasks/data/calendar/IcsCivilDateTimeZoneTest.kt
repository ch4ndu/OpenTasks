package com.udnahc.opentasks.data.calendar

import com.udnahc.opentasks.data.extensions.computeDeadlineUtcMillis
import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.localMillisToLocalDate
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.data.model.CalendarEventSourceKind
import com.udnahc.opentasks.domain.usecase.task.CalendarVisibleRange
import com.udnahc.opentasks.domain.usecase.task.expandCalendarVisibleInputs
import com.udnahc.opentasks.testutil.testTask
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.util.TimeZone as JavaTimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class IcsCivilDateTimeZoneTest {
    @Test
    fun newAllDayImportsRetainEveryIncludedDateThroughProjectionAndExport() {
        val cases = listOf(
            "2026-05-04" to "2026-05-05",
            "2026-05-04" to "2026-05-07",
            "2026-03-07" to "2026-03-10",
            "2026-10-03" to "2026-10-06",
        )
        listOf("America/Los_Angeles", "Asia/Kathmandu", "Australia/Adelaide").forEach { zone ->
            withTimeZone(zone) {
                cases.forEach { (startText, exclusiveEndText) ->
                    val startDate = LocalDate.parse(startText)
                    val endDate = LocalDate.parse(exclusiveEndText)
                    val startUtc = startDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                    val exclusiveEndUtc = endDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                    val parsed = IcsParser.parse(
                        """
                        BEGIN:VCALENDAR
                        BEGIN:VEVENT
                        UID:span
                        SUMMARY:All day span
                        DTSTART;VALUE=DATE:${startText.replace("-", "")}
                        DTEND;VALUE=DATE:${exclusiveEndText.replace("-", "")}
                        END:VEVENT
                        END:VCALENDAR
                        """.trimIndent(),
                    ).single()
                    val sources = listOf(
                        parsed,
                        parsed.copy(sourceKind = CalendarEventSourceKind.ANDROID),
                        parsed.copy(sourceKind = CalendarEventSourceKind.IOS, endTimeUtcMillis = exclusiveEndUtc),
                        parsed.copy(sourceKind = CalendarEventSourceKind.MACOS, endTimeUtcMillis = exclusiveEndUtc),
                    )
                    sources.forEach { event ->
                        val endUtc = calendarTaskEndUtcMillis(event)
                        assertEquals(exclusiveEndUtc - 1L, endUtc, "$zone $startText ${event.sourceKind}")
                        val rawTask = testTask(id = "span", deadline = startUtc, endDeadline = endUtc, isAllDay = true)
                        val localTask = rawTask.copy(deadline = utcToLocal(startUtc), endDeadline = endUtc?.let(::utcToLocal))
                        val keys = expandCalendarVisibleInputs(
                            mapOf(dayKey(utcToLocal(startUtc)) to listOf(localTask)),
                            CalendarVisibleRange(startDate.minus(1, DateTimeUnit.DAY), endDate.plus(1, DateTimeUnit.DAY)),
                        ).keys
                        assertEquals((startDate.toEpochDays() until endDate.toEpochDays()).map { it.toLong() }.toSet(), keys)
                        val exported = IcsGenerator.generate(listOf(rawTask))
                        assertTrue("DTEND;VALUE=DATE:${exclusiveEndText.replace("-", "")}" in exported)
                        assertEquals(endUtc, calendarTaskEndUtcMillis(IcsParser.parse(exported).single()))
                    }
                }
            }
        }
    }

    @Test
    fun timedMidnightEndRemainsExclusiveAndAllDayInvalidEndsRemainAbsent() = withTimeZone("UTC") {
        val parsed = IcsParser.parse(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:timed
            SUMMARY:Timed span
            DTSTART:20260504T120000Z
            DTEND:20260506T000000Z
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
        ).single()
        val task = testTask(id = "timed", deadline = parsed.startTimeUtcMillis, endDeadline = parsed.endTimeUtcMillis)
        val keys = expandCalendarVisibleInputs(
            mapOf(dayKey(parsed.startTimeUtcMillis) to listOf(task)),
            CalendarVisibleRange(LocalDate(2026, 5, 4), LocalDate(2026, 5, 7)),
        ).keys
        assertEquals(setOf(LocalDate(2026, 5, 4).toEpochDays().toLong(), LocalDate(2026, 5, 5).toEpochDays().toLong()), keys)
        assertTrue("DTEND:20260506T000000Z" in IcsGenerator.generate(listOf(task)))
        assertEquals(parsed.endTimeUtcMillis, calendarTaskEndUtcMillis(parsed))
        val nativeAllDay = parsed.copy(isAllDay = true, sourceKind = CalendarEventSourceKind.IOS)
        assertNull(calendarTaskEndUtcMillis(nativeAllDay.copy(endTimeUtcMillis = null)))
        assertNull(calendarTaskEndUtcMillis(nativeAllDay.copy(endTimeUtcMillis = parsed.startTimeUtcMillis)))
        assertNull(calendarTaskEndUtcMillis(nativeAllDay.copy(endTimeUtcMillis = parsed.startTimeUtcMillis - 1L)))
        assertEquals(parsed.endTimeUtcMillis, calendarTaskEndUtcMillis(parsed.copy(isAllDay = true, sourceKind = CalendarEventSourceKind.LEGACY)))
    }

    @Test
    fun allDayIcsDatesRoundTripAsCivilDatesInRequiredTimeZones() {
        listOf("America/Los_Angeles", "Asia/Kathmandu", "Australia/Adelaide").forEach { timeZoneId ->
            withTimeZone(timeZoneId) {
                val parsed = IcsParser.parse(
                    """
                    BEGIN:VCALENDAR
                    VERSION:2.0
                    BEGIN:VEVENT
                    UID:civil-$timeZoneId
                    SUMMARY:Civil date
                    DTSTART;VALUE=DATE:20260504
                    DTEND;VALUE=DATE:20260505
                    END:VEVENT
                    END:VCALENDAR
                    """.trimIndent(),
                ).single()
                assertEquals("2026-05-04", localMillisToLocalDate(utcToLocal(parsed.startTimeUtcMillis)).toString())

                val startUtc = computeDeadlineUtcMillis(2026, 5, 4, 0, 0)
                val ics = IcsGenerator.generate(
                    listOf(testTask(id = "civil-$timeZoneId", deadline = startUtc, endDeadline = startUtc, isAllDay = true)),
                )
                assertTrue("DTSTART;VALUE=DATE:20260504" in ics)
                assertTrue("DTEND;VALUE=DATE:20260505" in ics)
            }
        }
    }

    private inline fun withTimeZone(timeZoneId: String, block: () -> Unit) {
        val previous = JavaTimeZone.getDefault()
        JavaTimeZone.setDefault(JavaTimeZone.getTimeZone(timeZoneId))
        try {
            block()
        } finally {
            JavaTimeZone.setDefault(previous)
        }
    }
}
