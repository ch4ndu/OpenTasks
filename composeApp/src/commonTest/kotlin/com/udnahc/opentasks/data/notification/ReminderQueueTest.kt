package com.udnahc.opentasks.data.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun commandValidatorAcceptsOnlyCanonicalPayloadsForEachPolicy() {
        val task = ReminderIdentity("task-1", 100L, ReminderKind.DATE, 0)
        val ongoing = ReminderIdentity("task-1", 100L, ReminderKind.ONGOING, 0)
        val countdown = ReminderIdentity("countdown-1", 200L, ReminderKind.COUNTDOWN, 0)

        assertAccepted(ReminderCommand.MARK_DONE, task)
        assertAccepted(ReminderCommand.TASK_TAP, task)
        assertAccepted(ReminderCommand.DELIVERY, task)
        assertAccepted(ReminderCommand.GOT_IT, ongoing)
        assertAccepted(ReminderCommand.SHEET_DISMISS, task)
        assertAccepted(ReminderCommand.SHEET_DISMISS, ongoing)
        assertAccepted(ReminderCommand.ONGOING_TAP, ongoing)
        assertAccepted(ReminderCommand.COUNTDOWN_TAP, countdown)
        assertAccepted(ReminderCommand.COUNTDOWN_DELIVERY, countdown)

        assertAccepted(ReminderCommand.MARK_DONE, ongoing)
        assertRejectedFields(ReminderCommand.TASK_TAP, countdown)
        assertRejectedFields(ReminderCommand.GOT_IT, task)
        assertRejectedFields(ReminderCommand.SHEET_DISMISS, countdown)
    }

    @Test
    fun commandValidatorRejectsMismatchedOrMalformedDuplicatedFields() {
        val identity = ReminderIdentity("task-1", 100L, ReminderKind.DATE, 2)

        assertRejectedFields(ReminderCommand.MARK_DONE, identity, semanticKey = "v1|7|task-1|100|DATE|1")
        assertRejectedFields(ReminderCommand.MARK_DONE, identity, eventId = "other-task")
        assertRejectedFields(ReminderCommand.MARK_DONE, identity, occurrenceUtcMillis = 101L)
        assertRejectedFields(ReminderCommand.MARK_DONE, identity, accountId = "")
        assertRejectedFields(ReminderCommand.MARK_DONE, identity, boundaryEpoch = 0L)
        assertRejectedFields(ReminderCommand.MARK_DONE, identity, semanticKey = identity.semanticKey + "|extra")
        assertRejectedFields(ReminderCommand.MARK_DONE, identity, semanticKey = "task-1")
    }

    @Test
    fun semanticKeyParserFailsClosedOnOverflowingEventLength() {
        assertEquals(
            null,
            ReminderIdentity.fromSemanticKey("v1|2147483647|task-1|100|DATE|0"),
        )
    }

    private fun assertAccepted(command: ReminderCommand, identity: ReminderIdentity) {
        val result = validateReminderCommand(
            command = command,
            semanticKey = identity.semanticKey,
            eventId = identity.eventId,
            occurrenceUtcMillis = identity.occurrenceUtcMillis,
            accountId = "account-1",
            boundaryEpoch = 1L,
        )
        val accepted = result as? ReminderCommandValidation.Accepted
            ?: error("Expected the canonical reminder command to be accepted")
        assertEquals(identity, accepted.identity)
    }

    private fun assertRejectedFields(
        command: ReminderCommand,
        identity: ReminderIdentity,
        semanticKey: String? = identity.semanticKey,
        eventId: String? = identity.eventId,
        occurrenceUtcMillis: Long? = identity.occurrenceUtcMillis,
        accountId: String? = "account-1",
        boundaryEpoch: Long = 1L,
    ) {
        val result = validateReminderCommand(
            command = command,
            semanticKey = semanticKey,
            eventId = eventId,
            occurrenceUtcMillis = occurrenceUtcMillis,
            accountId = accountId,
            boundaryEpoch = boundaryEpoch,
        )
        assertFalse(result is ReminderCommandValidation.Accepted)
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
