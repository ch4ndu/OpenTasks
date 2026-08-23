package com.udnahc.opentasks

import com.udnahc.opentasks.data.auth.CacheBinding
import com.udnahc.opentasks.data.model.COUNTDOWN_ID_PREFIX
import com.udnahc.opentasks.data.notification.ReminderIdentity
import com.udnahc.opentasks.data.notification.ReminderKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationDeepLinkTest {

    @Test
    fun matchingCountdownEventProvidesIdForNavigation() {
        val binding = binding(accountId = "account-a", boundaryEpoch = 7L)

        assertEquals(
            "countdown-a",
            countdownEvent(accountId = "account-a", boundaryEpoch = 7L)
                .countdownIdIfMatches(binding),
        )
    }

    @Test
    fun countdownEventFromAnotherAccountIsRejected() {
        val event = countdownEvent(accountId = "account-a", boundaryEpoch = 7L)

        assertNull(event.countdownIdIfMatches(binding(accountId = "account-b", boundaryEpoch = 7L)))
    }

    @Test
    fun countdownEventFromAnOlderEpochIsRejectedForTheSameAccount() {
        val event = countdownEvent(accountId = "account-a", boundaryEpoch = 6L)

        assertNull(event.countdownIdIfMatches(binding(accountId = "account-a", boundaryEpoch = 7L)))
    }

    @Test
    fun exactConsumptionPreventsReplayWithoutClearingNewerEvent() {
        val delivered = countdownEvent(accountId = "account-a", boundaryEpoch = 7L)
        val newer = countdownEvent(accountId = "account-a", boundaryEpoch = 7L)

        assertNotEquals(delivered, newer)
        assertNull(consumeNotificationDeepLinkEvent(delivered, delivered))
        assertEquals(newer, consumeNotificationDeepLinkEvent(newer, delivered))
    }

    @Test
    fun rejectedBoundaryDecisionHappensBeforeAnyConsumerCanAccessCountdown() {
        val event = countdownEvent(accountId = "account-a", boundaryEpoch = 6L)
        val activeBinding = binding(accountId = "account-b", boundaryEpoch = 7L)
        var consumerCalls = 0

        val countdownId = event.countdownIdIfMatches(activeBinding)
        countdownId?.let {
            consumerCalls += 1
        }

        assertNull(countdownId)
        assertEquals(0, consumerCalls)
    }

    @Test
    fun staleAccountATapCannotMatchAccountBWithTheSameLocalEventId() {
        assertFalse(
            notificationOwnershipMatches(
                eventId = "task-shared",
                accountId = "account-a",
                boundaryEpoch = 7L,
                expectedEventId = "task-shared",
                expectedAccountId = "account-b",
                expectedBoundaryEpoch = 8L,
            )
        )
        assertFalse(
            notificationOwnershipMatches(
                eventId = "task-shared",
                accountId = "account-a",
                boundaryEpoch = 6L,
                expectedEventId = "task-shared",
                expectedAccountId = "account-a",
                expectedBoundaryEpoch = 7L,
            )
        )
    }

    @Test
    fun malformedOwnershipCannotMatchAnAuthenticatedBoundary() {
        assertFalse(
            notificationOwnershipMatches(
                eventId = null,
                accountId = "account-a",
                boundaryEpoch = 7L,
                expectedEventId = "task-shared",
                expectedAccountId = "account-a",
                expectedBoundaryEpoch = 7L,
            )
        )
        assertFalse(
            notificationOwnershipMatches(
                eventId = "",
                accountId = "account-a",
                boundaryEpoch = 7L,
                expectedEventId = "task-shared",
                expectedAccountId = "account-a",
                expectedBoundaryEpoch = 7L,
            )
        )
        assertFalse(
            notificationOwnershipMatches(
                eventId = "   ",
                accountId = "account-a",
                boundaryEpoch = 7L,
                expectedEventId = "task-shared",
                expectedAccountId = "account-a",
                expectedBoundaryEpoch = 7L,
            )
        )
        assertFalse(
            notificationOwnershipMatches(
                eventId = "task-shared",
                accountId = null,
                boundaryEpoch = 7L,
                expectedEventId = "task-shared",
                expectedAccountId = "account-a",
                expectedBoundaryEpoch = 7L,
            )
        )
        assertFalse(
            notificationOwnershipMatches(
                eventId = "task-shared",
                accountId = "account-a",
                boundaryEpoch = 0L,
                expectedEventId = "task-shared",
                expectedAccountId = "account-a",
                expectedBoundaryEpoch = 7L,
            )
        )
    }

    @Test
    fun presentationCleanupCannotRemoveTheDestinationAccountNotification() {
        assertFalse(
            notificationOwnershipMatches(
                eventId = "task-shared",
                accountId = "account-b",
                boundaryEpoch = 8L,
                expectedEventId = "task-shared",
                expectedAccountId = "account-a",
                expectedBoundaryEpoch = 7L,
            )
        )
        assertTrue(
            notificationOwnershipMatches(
                eventId = "task-shared",
                accountId = "account-a",
                boundaryEpoch = 7L,
                expectedEventId = "task-shared",
                expectedAccountId = "account-a",
                expectedBoundaryEpoch = 7L,
            )
        )
    }

    @Test
    fun foregroundDuplicateCleanupSelectsOnlyTheExactActiveBoundary() {
        val delivered = listOf(
            DeliveredNotification("task-shared", "account-a", 7L),
            DeliveredNotification("task-shared", "account-b", 8L),
        )

        val matchingAccountB = delivered.filter { notification ->
            notificationOwnershipMatches(
                eventId = notification.eventId,
                accountId = notification.accountId,
                boundaryEpoch = notification.boundaryEpoch,
                expectedEventId = "task-shared",
                expectedAccountId = "account-b",
                expectedBoundaryEpoch = 8L,
            )
        }

        assertEquals(listOf(delivered[1]), matchingAccountB)
    }

    @Test
    fun sharedPublisherAcceptsCanonicalTaskCountdownAndOngoingTaps() {
        clearPublishedEvent()
        try {
            listOf(
                ReminderIdentity("task-a", 100L, ReminderKind.DATE, 0),
                ReminderIdentity(
                    "${COUNTDOWN_ID_PREFIX}countdown-a",
                    200L,
                    ReminderKind.COUNTDOWN,
                    1,
                ),
                ReminderIdentity("task-b", 300L, ReminderKind.ONGOING, 0),
            ).forEach { identity ->
                publishNotificationDeepLinkEvent(
                    eventId = identity.eventId,
                    occurrenceDeadlineUtcMillis = identity.occurrenceUtcMillis,
                    notificationAtUtcMillis = identity.occurrenceUtcMillis + 1L,
                    semanticKey = identity.semanticKey,
                    accountId = "account-a",
                    boundaryEpoch = 7L,
                )

                val event = assertNotNull(notificationDeepLinkEvent.value)
                assertEquals(identity.eventId, event.eventId)
                assertEquals(identity.semanticKey, event.semanticKey)
                assertEquals("account-a", event.accountId)
                assertEquals(7L, event.boundaryEpoch)
            }
        } finally {
            clearPublishedEvent()
        }
    }

    @Test
    fun malformedSharedPublisherPayloadDoesNotOverwriteANewerEvent() {
        clearPublishedEvent()
        try {
            val first = ReminderIdentity("task-a", 100L, ReminderKind.DATE, 0)
            val newer = ReminderIdentity("task-b", 200L, ReminderKind.DATE, 0)
            publishNotificationDeepLinkEvent(
                first.eventId,
                first.occurrenceUtcMillis,
                first.occurrenceUtcMillis + 1L,
                first.semanticKey,
                "account-a",
                7L,
            )
            publishNotificationDeepLinkEvent(
                newer.eventId,
                newer.occurrenceUtcMillis,
                newer.occurrenceUtcMillis + 1L,
                newer.semanticKey,
                "account-a",
                7L,
            )
            val current = assertNotNull(notificationDeepLinkEvent.value)

            val countdown = ReminderIdentity(
                "${COUNTDOWN_ID_PREFIX}countdown-a",
                300L,
                ReminderKind.COUNTDOWN,
                0,
            )
            assertPublicationRejected(current) {
                publishNotificationDeepLinkEvent(
                    newer.eventId,
                    countdown.occurrenceUtcMillis,
                    countdown.occurrenceUtcMillis + 1L,
                    countdown.semanticKey,
                    "account-a",
                    7L,
                )
            }
            assertPublicationRejected(current) {
                publishNotificationDeepLinkEvent(
                    "other-task",
                    newer.occurrenceUtcMillis,
                    newer.occurrenceUtcMillis + 1L,
                    newer.semanticKey,
                    "account-a",
                    7L,
                )
            }
            assertPublicationRejected(current) {
                publishNotificationDeepLinkEvent(
                    newer.eventId,
                    newer.occurrenceUtcMillis + 1L,
                    newer.occurrenceUtcMillis + 2L,
                    newer.semanticKey,
                    "account-a",
                    7L,
                )
            }
            assertPublicationRejected(current) {
                publishNotificationDeepLinkEvent(
                    newer.eventId,
                    newer.occurrenceUtcMillis,
                    newer.occurrenceUtcMillis + 1L,
                    newer.semanticKey,
                    "",
                    7L,
                )
            }
            assertPublicationRejected(current) {
                publishNotificationDeepLinkEvent(
                    newer.eventId,
                    newer.occurrenceUtcMillis,
                    newer.occurrenceUtcMillis + 1L,
                    newer.semanticKey,
                    "account-a",
                    0L,
                )
            }
        } finally {
            clearPublishedEvent()
        }
    }

    private fun countdownEvent(accountId: String, boundaryEpoch: Long) = NotificationDeepLinkEvent(
        eventId = "${COUNTDOWN_ID_PREFIX}countdown-a",
        accountId = accountId,
        boundaryEpoch = boundaryEpoch,
    )

    private fun binding(accountId: String, boundaryEpoch: Long) = CacheBinding(
        canonicalEndpoint = "https://tasks.example.com",
        serverInstanceId = "server",
        accountId = accountId,
        capabilityVersion = 2,
        boundaryEpoch = boundaryEpoch,
    )

    private fun clearPublishedEvent() {
        notificationDeepLinkEvent.value?.let(::clearNotificationDeepLinkEvent)
    }

    private fun assertPublicationRejected(
        current: NotificationDeepLinkEvent,
        publish: () -> Unit,
    ) {
        publish()
        assertEquals(current, notificationDeepLinkEvent.value)
    }
}

private data class DeliveredNotification(
    val eventId: String,
    val accountId: String,
    val boundaryEpoch: Long,
)
