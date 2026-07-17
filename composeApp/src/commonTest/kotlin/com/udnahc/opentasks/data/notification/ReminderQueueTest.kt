package com.udnahc.opentasks.data.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderQueueTest {
    @Test
    fun nearestBundleUsesOccurrenceNotEarliestTrigger() {
        val candidates = listOf(
            request("a", occurrence = 100, trigger = 90, reminder = 0),
            request("a", occurrence = 100, trigger = 95, reminder = 1),
            request("a", occurrence = 200, trigger = 50, reminder = 0),
        )

        val selected = selectFairReminderQueue(candidates, limit = 2)

        assertEquals(listOf(100L, 100L), selected.map(ReminderRequest::occurrenceUtcMillis))
    }

    @Test
    fun reservesOneCompleteNearestBundlePerEventBeforeFillingByTime() {
        val candidates = listOf(
            request("a", 100, 10, 0),
            request("a", 100, 20, 1),
            request("a", 200, 30, 0),
            request("b", 100, 40, 0),
            request("b", 100, 50, 1),
        )

        val selected = selectFairReminderQueue(candidates, limit = 4)

        assertEquals(4, selected.size)
        assertTrue(selected.count { it.eventId == "a" && it.occurrenceUtcMillis == 100L } == 2)
        assertTrue(selected.count { it.eventId == "b" && it.occurrenceUtcMillis == 100L } == 2)
    }

    @Test
    fun capsAndDeduplicatesOccurrenceQualifiedRequestIds() {
        val duplicate = request("a", 100, 10, 0)
        val candidates = listOf(duplicate, duplicate) +
            (1..100).map { request("event-$it", it.toLong(), it.toLong(), 0) }

        val selected = selectFairReminderQueue(candidates)

        assertEquals(IOS_PENDING_REMINDER_LIMIT, selected.size)
        assertEquals(selected.size, selected.map(ReminderRequest::requestId).distinct().size)
        assertTrue(request("a", 101, 10, 0).requestId != duplicate.requestId)
    }

    @Test
    fun recognizesCurrentAndLegacyReminderRequestIdentifiers() {
        assertTrue(isOpenTasksReminderRequestId("opentasks_reminder_task_100_0"))
        assertTrue(isOpenTasksReminderRequestId("task_event-1_reminder_0"))
        assertTrue(!isOpenTasksReminderRequestId("task_event-1_ongoing_8"))
    }

    @Test
    fun semanticKeysSeparateReminderKindsAndOriginalOrdinals() {
        val date = ReminderIdentity("event", 100L, ReminderKind.DATE, 0)
        val duration = ReminderIdentity("event", 100L, ReminderKind.DURATION, 0)
        val overdue = ReminderIdentity("event", 100L, ReminderKind.OVERDUE, 0)
        val countdown = ReminderIdentity("event", 100L, ReminderKind.COUNTDOWN, 0)
        val ongoing = ReminderIdentity("event", 100L, ReminderKind.ONGOING, 0)

        assertEquals(5, setOf(date, duration, overdue, countdown, ongoing).map { it.semanticKey }.size)
        assertEquals(date, ReminderIdentity.fromSemanticKey(date.semanticKey))
    }

    private fun request(
        event: String,
        occurrence: Long,
        trigger: Long,
        reminder: Int,
    ) = ReminderRequest(
        identity = ReminderIdentity(
            eventId = event,
            occurrenceUtcMillis = occurrence,
            kind = ReminderKind.DATE,
            ordinal = reminder,
        ),
        title = event,
        body = event,
        triggerAtUtcMillis = trigger,
    )
}
