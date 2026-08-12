package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.extensions.localMillisToLocalDateTime
import com.udnahc.opentasks.data.model.RecurrenceType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParseQuickTaskInputUseCaseTest {
    private val parser = ParseQuickTaskInputUseCase()
    private val reference = LocalDateTime(2028, 2, 27, 14, 30)
    private val context = QuickTaskCreationContext(categoryId = "inbox")

    @Test
    fun recognizesOnlyBoundedTrailingEnglishClauses() {
        val parsed = parser(
            "Submit report tomorrow at 3pm",
            reference,
            context,
        )

        assertEquals("Submit report", parsed.cleanedTitle)
        assertEquals(
            listOf(QuickTaskTokenKind.DATE, QuickTaskTokenKind.TIME),
            parsed.recognizedTokens.map { it.kind },
        )
        assertEquals(LocalDateTime(2028, 2, 28, 15, 0), parsed.deadlineDateTime())
        assertFalse(parsed.isAllDay)

        listOf(
            "Tomorrow we submit the report",
            "Submit tomorrowish",
            "Submit report in 0 days",
            "Submit report in 366 days",
            "Submit report in 53 weeks",
            "Submit report at 25:00",
            "Submit report at 13 pm",
            "Submit report mañana",
        ).forEach { input ->
            val literal = parser(input, reference, context)
            assertEquals(input, literal.cleanedTitle)
            assertTrue(literal.recognizedTokens.isEmpty(), input)
            assertNull(literal.deadline)
        }
    }

    @Test
    fun supportsBoundedRelativeDatesWeeksAndValidTimeForms() {
        assertEquals(
            LocalDateTime(2029, 2, 26, 8, 0),
            parser("Plan in 365 days", reference, context).deadlineDateTime(),
        )
        assertEquals(
            LocalDateTime(2029, 2, 25, 8, 0),
            parser("Plan in 52 weeks", reference, context).deadlineDateTime(),
        )
        assertEquals(
            LocalDateTime(2028, 2, 28, 0, 0),
            parser("Plan tomorrow at 12am", reference, context).deadlineDateTime(),
        )
        assertEquals(
            LocalDateTime(2028, 2, 28, 12, 45),
            parser("Plan tomorrow at 12:45 pm", reference, context).deadlineDateTime(),
        )
        assertEquals(
            LocalDateTime(2028, 2, 28, 23, 59),
            parser("Plan tomorrow at 23:59", reference, context).deadlineDateTime(),
        )
    }

    @Test
    fun explicitDateOverridesFallbackWhileEachTokenOverridesOnlyItsField() {
        val calendarContext = context.copy(fallbackDate = LocalDate(2030, 6, 10))

        val contextual = parser("Call at 4pm", reference, calendarContext)
        assertEquals(LocalDateTime(2030, 6, 10, 16, 0), contextual.deadlineDateTime())
        assertEquals(RecurrenceType.NONE, contextual.recurrenceType)

        val explicit = parser("Call tomorrow weekly at 4pm", reference, calendarContext)
        assertEquals(LocalDateTime(2028, 2, 28, 16, 0), explicit.deadlineDateTime())
        assertEquals(RecurrenceType.WEEKLY, explicit.recurrenceType)
        assertFalse(explicit.isAllDay)

        val fallbackOnly = parser("Call", reference, calendarContext)
        assertEquals(LocalDateTime(2030, 6, 10, 8, 0), fallbackOnly.deadlineDateTime())
        assertTrue(fallbackOnly.isAllDay)
    }

    @Test
    fun recurrenceStartOverridesCalendarFallbackWhenNoExplicitDate() {
        val mondayAfterDefaultTime = LocalDateTime(2028, 3, 6, 9, 0)
        val calendarContext = context.copy(fallbackDate = LocalDate(2030, 6, 12))

        val parsed = parser("Repeat every Monday", mondayAfterDefaultTime, calendarContext)

        assertEquals(LocalDateTime(2028, 3, 13, 8, 0), parsed.deadlineDateTime())
        assertEquals(RecurrenceType.WEEKLY, parsed.recurrenceType)
    }

    @Test
    fun timeOnlyUsesStrictlyFutureMinuteAndDateOnlyUsesAllDayAnchor() {
        assertEquals(
            LocalDateTime(2028, 2, 27, 15, 0),
            parser("Call at 3pm", reference, context).deadlineDateTime(),
        )
        assertEquals(
            LocalDateTime(2028, 2, 28, 14, 30),
            parser("Call at 14:30", reference, context).deadlineDateTime(),
        )
        val dateOnly = parser("Call tomorrow", reference, context)
        assertEquals(LocalDateTime(2028, 2, 28, 8, 0), dateOnly.deadlineDateTime())
        assertTrue(dateOnly.isAllDay)
    }

    @Test
    fun weekdayAndRelativeMathCrossesWeekMonthYearLeapAndDstAdjacentDatesCivilly() {
        val sundayMorning = LocalDateTime(2028, 3, 5, 7, 0)
        assertEquals(
            LocalDateTime(2028, 3, 5, 8, 0),
            parser("Review Sunday at 8am", sundayMorning, context).deadlineDateTime(),
        )
        assertEquals(
            LocalDateTime(2028, 3, 12, 8, 0),
            parser("Review Sunday", sundayMorning, context).deadlineDateTime(),
        )
        assertEquals(
            LocalDateTime(2028, 3, 6, 8, 0),
            parser("Review Monday", sundayMorning, context).deadlineDateTime(),
        )
        assertEquals(
            LocalDateTime(2028, 2, 29, 8, 0),
            parser("Review in 2 days", reference, context).deadlineDateTime(),
        )
        assertEquals(
            LocalDateTime(2029, 1, 1, 8, 0),
            parser("Review tomorrow", LocalDateTime(2028, 12, 31, 20, 0), context).deadlineDateTime(),
        )
        assertEquals(
            LocalDateTime(2028, 3, 13, 8, 0),
            parser("Review in 1 day", LocalDateTime(2028, 3, 12, 23, 30), context).deadlineDateTime(),
        )
    }

    @Test
    fun supportedRecurrencesChooseDeterministicFutureAnchors() {
        val mondayAfterEight = LocalDateTime(2028, 3, 6, 9, 0)
        val expectations = mapOf(
            "daily" to RecurrenceType.DAILY,
            "every day" to RecurrenceType.DAILY,
            "weekly" to RecurrenceType.WEEKLY,
            "every week" to RecurrenceType.WEEKLY,
            "every month" to RecurrenceType.MONTHLY,
            "monthly" to RecurrenceType.MONTHLY,
            "yearly" to RecurrenceType.YEARLY,
            "every year" to RecurrenceType.YEARLY,
            "every weekday" to RecurrenceType.EVERY_WEEKDAY,
            "every Monday" to RecurrenceType.WEEKLY,
        )
        expectations.forEach { (phrase, recurrence) ->
            assertEquals(
                recurrence,
                parser("Repeat $phrase", mondayAfterEight, context).recurrenceType,
                phrase,
            )
        }
        assertEquals(
            LocalDateTime(2028, 3, 7, 8, 0),
            parser("Repeat daily", mondayAfterEight, context).deadlineDateTime(),
        )
        assertEquals(
            LocalDateTime(2028, 3, 13, 8, 0),
            parser("Repeat every Monday", mondayAfterEight, context).deadlineDateTime(),
        )
        assertEquals(
            LocalDateTime(2028, 3, 7, 8, 0),
            parser("Repeat every weekday", mondayAfterEight, context).deadlineDateTime(),
        )
        assertEquals(
            LocalDateTime(2028, 2, 29, 8, 0),
            parser("Repeat monthly", LocalDateTime(2028, 1, 31, 9, 0), context).deadlineDateTime(),
        )
        assertEquals(
            LocalDateTime(2029, 2, 28, 8, 0),
            parser("Repeat yearly", LocalDateTime(2028, 2, 29, 9, 0), context).deadlineDateTime(),
        )
        assertEquals(
            LocalDateTime(2028, 3, 13, 8, 0),
            parser("Repeat every weekday", LocalDateTime(2028, 3, 10, 9, 0), context).deadlineDateTime(),
        )
    }

    @Test
    fun explicitTodayAtAPastTimeRemainsOverdue() {
        val parsed = parser("Send today at 1pm", reference, context)

        assertEquals(LocalDateTime(2028, 2, 27, 13, 0), parsed.deadlineDateTime())
        assertEquals("Send", parsed.cleanedTitle)
    }

    @Test
    fun rightmostKindWinsAndDisplacedOrDismissedTextRemainsLiteral() {
        val initial = parser("Review tomorrow today at 4pm", reference, context)
        assertEquals("Review tomorrow", initial.cleanedTitle)
        val dateToken = initial.recognizedTokens.single { it.kind == QuickTaskTokenKind.DATE }

        val dismissed = parser(
            rawInput = initial.rawInput,
            reference = reference,
            context = context,
            suppressedTokenSignatures = setOf(dateToken.signature),
        )
        assertEquals("Review tomorrow today", dismissed.cleanedTitle)
        assertFalse(dismissed.recognizedTokens.single { it.kind == QuickTaskTokenKind.DATE }.isActive)
        assertEquals(LocalDateTime(2028, 2, 27, 16, 0), dismissed.deadlineDateTime())

        val changed = parser(
            rawInput = "Review tomorrow Monday at 4pm",
            reference = reference,
            context = context,
            suppressedTokenSignatures = setOf(dateToken.signature),
        )
        assertTrue(changed.recognizedTokens.single { it.kind == QuickTaskTokenKind.DATE }.isActive)
        assertEquals("Review tomorrow", changed.cleanedTitle)
    }

    @Test
    fun fullyConsumedInferenceCannotProduceASavableTitle() {
        val parsed = parser("tomorrow at 3pm", reference, context)

        assertEquals("", parsed.cleanedTitle)
        assertTrue(parsed.recognizedTokens.all { it.isActive })
    }

    private fun QuickTaskParseResult.deadlineDateTime(): LocalDateTime? =
        deadline?.let(::localMillisToLocalDateTime)
}
