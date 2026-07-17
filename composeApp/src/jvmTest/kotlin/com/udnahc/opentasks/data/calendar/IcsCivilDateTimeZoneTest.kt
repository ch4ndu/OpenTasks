package com.udnahc.opentasks.data.calendar

import com.udnahc.opentasks.data.extensions.computeDeadlineUtcMillis
import com.udnahc.opentasks.data.extensions.localMillisToLocalDate
import com.udnahc.opentasks.data.extensions.utcToLocal
import com.udnahc.opentasks.testutil.testTask
import java.util.TimeZone as JavaTimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IcsCivilDateTimeZoneTest {
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
