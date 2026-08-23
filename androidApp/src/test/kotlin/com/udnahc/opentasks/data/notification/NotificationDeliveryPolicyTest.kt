package com.udnahc.opentasks.data.notification

import com.udnahc.opentasks.data.model.COUNTDOWN_ID_PREFIX
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NotificationDeliveryPolicyTest {
    @Test
    fun taskAndCountdownAlarmPayloadsConsumeTheirCanonicalDeliveryCommands() {
        val taskIdentity = ReminderIdentity("task", 100L, ReminderKind.DATE, 0)
        val countdownIdentity = ReminderIdentity(
            "${COUNTDOWN_ID_PREFIX}countdown",
            200L,
            ReminderKind.COUNTDOWN,
            0,
        )

        assertNotNull(
            DeliveryCommandPayload(
                eventId = taskIdentity.eventId,
                semanticKey = taskIdentity.semanticKey,
                occurrenceUtcMillis = taskIdentity.occurrenceUtcMillis,
                accountId = "account-a",
                boundaryEpoch = 1L,
            ).validatedDeliveryCommand(),
        )
        assertNotNull(
            DeliveryCommandPayload(
                eventId = countdownIdentity.eventId,
                semanticKey = countdownIdentity.semanticKey,
                occurrenceUtcMillis = countdownIdentity.occurrenceUtcMillis,
                accountId = "account-a",
                boundaryEpoch = 1L,
            ).validatedDeliveryCommand(),
        )
        assertNull(
            DeliveryCommandPayload(
                eventId = "${COUNTDOWN_ID_PREFIX}task",
                semanticKey = taskIdentity.copy(eventId = "${COUNTDOWN_ID_PREFIX}task").semanticKey,
                occurrenceUtcMillis = taskIdentity.occurrenceUtcMillis,
                accountId = "account-a",
                boundaryEpoch = 1L,
            ).validatedDeliveryCommand(),
        )
    }

    @Test
    fun invalidResolutionDiscardsExactKeyWithoutChainingOrDisplaying() = runTest {
        val events = mutableListOf<String>()

        runValidatedReminderDelivery(
            resolveCurrent = { ReminderDeliveryResolution.DiscardExact },
            cleanupPriorDisplays = { events += "cleanup" },
            discardExact = { events += "exact" },
            discardAll = { events += "all" },
            logOperationalFailure = { phase, _ -> events += "log:$phase" },
        )

        assertEquals(listOf("exact"), events)
    }

    @Test
    fun lookupFailureFailsClosedAndDiscardsOnlyTheExactKeyWithoutChainingOrDisplaying() = runTest {
        val events = mutableListOf<String>()

        runValidatedReminderDelivery(
            resolveCurrent = { throw IllegalStateException("lookup") },
            cleanupPriorDisplays = { events += "cleanup" },
            discardExact = { events += "exact" },
            discardAll = { events += "all" },
            logOperationalFailure = { phase, _ -> events += "log:$phase" },
        )

        assertEquals(listOf("log:persisted-truth lookup", "exact"), events)
    }

    @Test
    fun maintenanceFailuresDoNotSuppressTheOneCurrentDisplayAttempt() = runTest {
        var displayCalls = 0
        val phases = mutableListOf<String>()

        runValidatedReminderDelivery(
            resolveCurrent = {
                ReminderDeliveryResolution.Deliver(
                    prepareCurrentDisplay = { 1 },
                    chainNextOccurrence = { error("chain") },
                    displayCurrent = {
                        displayCalls += 1
                        true
                    },
                )
            },
            cleanupPriorDisplays = { error("cleanup") },
            discardExact = { error("must not discard") },
            discardAll = { error("must not discard") },
            logOperationalFailure = { phase, _ -> phases += phase },
        )

        assertEquals(1, displayCalls)
        assertEquals(listOf("prior display cleanup", "next occurrence chaining"), phases)
    }

    @Test
    fun deniedDisplayCleansTheExactKeyAndNeverRetriesDisplay() = runTest {
        var displayCalls = 0
        var exactCleanupCalls = 0

        runValidatedReminderDelivery(
            resolveCurrent = {
                ReminderDeliveryResolution.Deliver(
                    prepareCurrentDisplay = { 1 },
                    chainNextOccurrence = {},
                    displayCurrent = {
                        displayCalls += 1
                        false
                    },
                )
            },
            cleanupPriorDisplays = {},
            discardExact = { exactCleanupCalls += 1 },
            discardAll = { error("must not discard all") },
            logOperationalFailure = { _, _ -> error("must not log") },
        )

        assertEquals(1, displayCalls)
        assertEquals(1, exactCleanupCalls)
    }

    @Test
    fun unexpectedDisplayFailurePropagatesWithoutASecondDisplayAttempt() = runTest {
        var displayCalls = 0

        assertFailsWith<IllegalStateException> {
            runValidatedReminderDelivery(
                resolveCurrent = {
                    ReminderDeliveryResolution.Deliver(
                        prepareCurrentDisplay = { 1 },
                        chainNextOccurrence = {},
                        displayCurrent = {
                            displayCalls += 1
                            error("display")
                        },
                    )
                },
                cleanupPriorDisplays = {},
                discardExact = { error("must not discard") },
                discardAll = { error("must not discard") },
                logOperationalFailure = { _, _ -> error("must not log") },
            )
        }

        assertEquals(1, displayCalls)
    }

    @Test
    fun preparingTheFiredAllocationBeforeChainKeepsARecurringCurrentDisplay() = runTest {
        var allocationIsPending = true
        var displayCalls = 0
        val events = mutableListOf<String>()

        runValidatedReminderDelivery(
            resolveCurrent = {
                ReminderDeliveryResolution.Deliver(
                    prepareCurrentDisplay = {
                        allocationIsPending = false
                        events += "prepare"
                        42
                    },
                    chainNextOccurrence = {
                        if (allocationIsPending) events += "removed-pending"
                        events += "chain"
                    },
                    displayCurrent = { notificationId ->
                        events += "display:$notificationId"
                        displayCalls += 1
                        true
                    },
                )
            },
            cleanupPriorDisplays = { events += "cleanup" },
            discardExact = { error("must not discard") },
            discardAll = { error("must not discard") },
            logOperationalFailure = { _, _ -> error("must not log") },
        )

        assertEquals(1, displayCalls)
        assertEquals(listOf("prepare", "cleanup", "chain", "display:42"), events)
    }
}
