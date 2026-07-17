package com.udnahc.opentasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WidgetNavigationEventTest {

    @Test
    fun repeatedIdenticalActionProducesDistinctMonotonicEvents() {
        val publisher = WidgetNavigationEventPublisher()

        val first = publisher.publish(WidgetNavigationAction.VIEW_LIST)
        val second = publisher.publish(WidgetNavigationAction.VIEW_LIST)

        assertEquals(1L, first?.id)
        assertEquals(2L, second?.id)
        assertEquals(first?.action, second?.action)
    }

    @Test
    fun calendarEventRetainsExactDateAndRejectsInvalidPayload() {
        val publisher = WidgetNavigationEventPublisher()
        val date = WidgetCalendarDate(year = 2026, month = 2, day = 28)

        val event = publisher.publish(WidgetNavigationAction.VIEW_CALENDAR, calendarDate = date)

        assertEquals(date, event?.calendarDate)
        assertNull(
            publisher.publish(
                WidgetNavigationAction.VIEW_CALENDAR,
                calendarDate = WidgetCalendarDate(year = 2026, month = 13, day = 1),
            )
        )
        assertNull(publisher.publish(WidgetNavigationAction.VIEW_TASK))
    }

    @Test
    fun calendarAcknowledgementClearsOnlyTheHandledEventOnce() {
        val first = WidgetNavigationEvent(1, WidgetNavigationAction.VIEW_CALENDAR, calendarDate = WidgetCalendarDate(2026, 7, 17))
        val second = first.copy(id = 2)

        assertNull(consumeCalendarNavigationEvent(first, first.id))
        assertEquals(second, consumeCalendarNavigationEvent(second, first.id))
    }
}
