package com.udnahc.opentasks

import com.udnahc.opentasks.navigation.Screen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedTaskPayloadTest {
    @Test
    fun activeRouteModalOwnershipInvalidatesAReservedTicketAndSignalsAfterTheLastDismissal() {
        val accountId = "modal-account"
        val epoch = 505L
        val staleId = 91_106L
        try {
            updateSharedTaskIntakeAppActive(true)
            updateSharedTaskIntakeReadiness(accountId, epoch, isMounted = true, isUiBusy = false)
            val ticket = assertNotNull(reserveSharedTaskIntake(staleId))
            val initialSignalRevision = sharedTaskIntakeScanRequestRevision

            var busyRoute = childModalBusyRouteAfterChange(
                currentBusyRoute = null,
                activeRoute = Screen.Settings,
                reportingRoute = Screen.Settings,
                isBusy = true,
            )
            updateSharedTaskIntakeReadiness(accountId, epoch, isMounted = true, isUiBusy = true)
            val activeCountdownRoute = Screen.CountdownDetail("active-countdown")
            busyRoute = childModalBusyRouteAfterChange(
                currentBusyRoute = busyRoute,
                activeRoute = activeCountdownRoute,
                reportingRoute = activeCountdownRoute,
                isBusy = true,
            )
            busyRoute = childModalBusyRouteAfterChange(
                currentBusyRoute = busyRoute,
                activeRoute = activeCountdownRoute,
                reportingRoute = Screen.Settings,
                isBusy = false,
            )
            busyRoute = childModalBusyRouteAfterChange(
                currentBusyRoute = busyRoute,
                activeRoute = activeCountdownRoute,
                reportingRoute = Screen.CountdownDetail("departing-countdown"),
                isBusy = false,
            )
            assertEquals(activeCountdownRoute, busyRoute)

            busyRoute = childModalBusyRouteAfterChange(
                currentBusyRoute = busyRoute,
                activeRoute = activeCountdownRoute,
                reportingRoute = activeCountdownRoute,
                isBusy = false,
            )
            assertNull(busyRoute)
            updateSharedTaskIntakeReadiness(accountId, epoch, isMounted = true, isUiBusy = false)
            assertEquals(initialSignalRevision, sharedTaskIntakeScanRequestRevision)
            assertFalse(
                publishSharedTaskIntake(
                    ticket.id,
                    ticket.readinessGeneration,
                    ticket.accountId,
                    ticket.boundaryEpoch,
                    description = "stale",
                )
            )
            assertTrue(abandonSharedTaskIntakeReservation(staleId))
            assertEquals(initialSignalRevision + 1L, sharedTaskIntakeScanRequestRevision)
        } finally {
            releaseSharedTaskPayloadReservation(staleId)
            deactivateSharedTaskIntake(accountId, epoch)
        }
    }

    @Test
    fun claimRetiresOnlyTheExactIcsPayloadAndCannotClearANewerPayload() {
        val oldId = 90_001L
        val newerId = 90_002L
        clearSharedTaskPayload(oldId)
        clearSharedTaskPayload(newerId)

        try {
            publishSharedTaskPayload(oldId, icsContent = validIcs("old"))
            assertEquals(oldId, claimSharedIcsPayload(oldId)?.id)
            assertNull(sharedTaskPayload.value)

            publishSharedTaskPayload(newerId, icsContent = validIcs("new"))
            assertNull(claimSharedIcsPayload(oldId))
            clearSharedTaskPayload(oldId)
            assertEquals(newerId, sharedTaskPayload.value?.id)
        } finally {
            clearSharedTaskPayload(oldId)
            clearSharedTaskPayload(newerId)
        }
    }

    @Test
    fun coldLaunchWaitsForMountAndAllowsOnlyOneTicketAndReview() {
        val accountId = "cold-launch-account"
        val epoch = 501L
        val firstId = 91_001L
        val secondId = 91_002L
        try {
            updateSharedTaskIntakeReadiness(accountId, epoch, isMounted = false, isUiBusy = false)
            assertNull(reserveSharedTaskIntake(firstId))

            updateSharedTaskIntakeReadiness(accountId, epoch, isMounted = true, isUiBusy = false)
            val ticket = assertNotNull(reserveSharedTaskIntake(firstId))
            assertNull(reserveSharedTaskIntake(secondId))
            assertTrue(
                publishSharedTaskIntake(
                    id = ticket.id,
                    readinessGeneration = ticket.readinessGeneration,
                    accountId = ticket.accountId,
                    boundaryEpoch = ticket.boundaryEpoch,
                    description = "first",
                )
            )

            val claimed = assertNotNull(claimSharedTaskPayloadForReview(firstId, accountId, epoch))
            assertEquals(firstId, claimed.id)
            assertNull(sharedTaskPayload.value)
            assertEquals(firstId, sharedTaskIntakeStatus.value.activeReviewId)
            assertNull(reserveSharedTaskIntake(secondId))

            assertTrue(completeSharedTaskReview(firstId))
            assertNotNull(reserveSharedTaskIntake(secondId))
        } finally {
            releaseSharedTaskPayloadReservation(firstId)
            releaseSharedTaskPayloadReservation(secondId)
            completeSharedTaskReview(firstId)
            deactivateSharedTaskIntake(accountId, epoch)
        }
    }

    @Test
    fun changedReadinessReplaysScanWhenAStaleReservationIsAbandoned() {
        val accountId = "transition-account"
        val epoch = 502L
        val staleId = 91_101L
        val nextId = 91_102L
        try {
            updateSharedTaskIntakeAppActive(true)
            updateSharedTaskIntakeReadiness(accountId, epoch, isMounted = true, isUiBusy = false)
            val staleTicket = assertNotNull(reserveSharedTaskIntake(staleId))
            assertEquals(staleTicket.readinessGeneration, sharedTaskIntakeStatus.value.readinessGeneration)
            val signalRevision = sharedTaskIntakeScanRequestRevision

            updateSharedTaskIntakeReadiness(accountId, epoch, isMounted = true, isUiBusy = true)
            updateSharedTaskIntakeReadiness(accountId, epoch, isMounted = true, isUiBusy = false)
            assertEquals(signalRevision, sharedTaskIntakeScanRequestRevision)
            assertFalse(
                publishSharedTaskIntake(
                    staleTicket.id,
                    staleTicket.readinessGeneration,
                    staleTicket.accountId,
                    staleTicket.boundaryEpoch,
                    description = "stale",
                )
            )
            assertTrue(abandonSharedTaskIntakeReservation(staleId))
            assertEquals(signalRevision + 1L, sharedTaskIntakeScanRequestRevision)
            assertNotNull(reserveSharedTaskIntake(nextId))
        } finally {
            releaseSharedTaskPayloadReservation(staleId)
            releaseSharedTaskPayloadReservation(nextId)
            deactivateSharedTaskIntake(accountId, epoch)
        }
    }

    @Test
    fun sameGenerationAbandonmentDoesNotSpinTheScanner() {
        val accountId = "quiet-abandon-account"
        val epoch = 503L
        val payloadId = 91_103L
        try {
            updateSharedTaskIntakeAppActive(true)
            updateSharedTaskIntakeReadiness(accountId, epoch, isMounted = true, isUiBusy = false)
            assertNotNull(reserveSharedTaskIntake(payloadId))
            val signalRevision = sharedTaskIntakeScanRequestRevision

            assertTrue(abandonSharedTaskIntakeReservation(payloadId))

            assertEquals(signalRevision, sharedTaskIntakeScanRequestRevision)
            assertTrue(canScanSharedTaskIntake())
        } finally {
            releaseSharedTaskPayloadReservation(payloadId)
            deactivateSharedTaskIntake(accountId, epoch)
        }
    }

    @Test
    fun coldMountWaitsForActiveAndResignInvalidatesAnInFlightTicket() {
        val accountId = "lifecycle-account"
        val epoch = 504L
        val staleId = 91_104L
        val nextId = 91_105L
        try {
            updateSharedTaskIntakeAppActive(false)
            updateSharedTaskIntakeReadiness(accountId, epoch, isMounted = true, isUiBusy = false)
            assertNull(reserveSharedTaskIntake(staleId))
            val inactiveSignalRevision = sharedTaskIntakeScanRequestRevision

            updateSharedTaskIntakeAppActive(true)
            assertEquals(inactiveSignalRevision + 1L, sharedTaskIntakeScanRequestRevision)
            val ticket = assertNotNull(reserveSharedTaskIntake(staleId))

            updateSharedTaskIntakeAppActive(false)
            assertFalse(
                publishSharedTaskIntake(
                    ticket.id,
                    ticket.readinessGeneration,
                    ticket.accountId,
                    ticket.boundaryEpoch,
                    description = "inactive",
                )
            )
            updateSharedTaskIntakeAppActive(true)
            val resumeSignalRevision = sharedTaskIntakeScanRequestRevision
            assertTrue(abandonSharedTaskIntakeReservation(staleId))
            assertEquals(resumeSignalRevision + 1L, sharedTaskIntakeScanRequestRevision)
            assertNotNull(reserveSharedTaskIntake(nextId))
        } finally {
            releaseSharedTaskPayloadReservation(staleId)
            releaseSharedTaskPayloadReservation(nextId)
            deactivateSharedTaskIntake(accountId, epoch)
            updateSharedTaskIntakeAppActive(true)
        }
    }

    @Test
    fun accountBoundaryChangeRejectsOldTicketWithoutRetargeting() {
        val oldAccount = "old-account"
        val newAccount = "new-account"
        val oldId = 91_201L
        val newId = 91_202L
        try {
            updateSharedTaskIntakeReadiness(oldAccount, 601L, isMounted = true, isUiBusy = false)
            val oldTicket = assertNotNull(reserveSharedTaskIntake(oldId))

            updateSharedTaskIntakeReadiness(newAccount, 602L, isMounted = true, isUiBusy = false)
            assertFalse(
                publishSharedTaskIntake(
                    oldTicket.id,
                    oldTicket.readinessGeneration,
                    oldTicket.accountId,
                    oldTicket.boundaryEpoch,
                    description = "wrong owner",
                )
            )
            val newTicket = assertNotNull(reserveSharedTaskIntake(newId))
            assertEquals(newAccount, newTicket.accountId)
            assertEquals(602L, newTicket.boundaryEpoch)
        } finally {
            releaseSharedTaskPayloadReservation(oldId)
            releaseSharedTaskPayloadReservation(newId)
            deactivateSharedTaskIntake(newAccount, 602L)
        }
    }

    @Test
    fun boundaryInitializationPreservesLegacyShareQueuedBeforeMount() {
        val payloadId = 91_301L
        val accountId = "__opentasks_local__"
        val epoch = 701L
        try {
            publishSharedTaskPayload(payloadId, description = "queued before mount")
            updateSharedTaskIntakeReadiness(accountId, epoch, isMounted = true, isUiBusy = false)

            assertEquals(payloadId, sharedTaskPayload.value?.id)
            assertNotNull(claimSharedTaskPayloadForReview(payloadId, accountId, epoch))
            assertEquals(payloadId, sharedTaskIntakeStatus.value.activeReviewId)
        } finally {
            completeSharedTaskReview(payloadId)
            clearSharedTaskPayload(payloadId)
            deactivateSharedTaskIntake(accountId, epoch)
        }
    }

    @Test
    fun queuedSharesRemainOneAtATimeUntilEachReviewCompletes() {
        val accountId = "review-account"
        val epoch = 801L
        val firstId = 91_401L
        val secondId = 91_402L
        try {
            publishSharedTaskPayload(firstId, description = "first")
            publishSharedTaskPayload(secondId, description = "second")
            updateSharedTaskIntakeReadiness(accountId, epoch, isMounted = true, isUiBusy = false)

            assertNotNull(claimSharedTaskPayloadForReview(firstId, accountId, epoch))
            assertEquals(secondId, sharedTaskPayload.value?.id)
            assertNull(claimSharedTaskPayloadForReview(secondId, accountId, epoch))

            assertTrue(completeSharedTaskReview(firstId))
            assertNotNull(claimSharedTaskPayloadForReview(secondId, accountId, epoch))
        } finally {
            completeSharedTaskReview(firstId)
            completeSharedTaskReview(secondId)
            clearSharedTaskPayload(firstId)
            clearSharedTaskPayload(secondId)
            deactivateSharedTaskIntake(accountId, epoch)
        }
    }

    @Test
    fun rejectionAlsoHoldsTheSingleReviewSlotUntilFeedbackCompletes() {
        val accountId = "rejection-account"
        val epoch = 802L
        val rejectionId = 91_501L
        val nextId = 91_502L
        try {
            publishSharedTaskPayloadRejection(rejectionId, ExternalInputFailure.TOO_LARGE)
            publishSharedTaskPayload(nextId, description = "next")
            updateSharedTaskIntakeReadiness(accountId, epoch, isMounted = true, isUiBusy = false)

            val rejection = assertNotNull(
                claimSharedTaskRejectionForReview(rejectionId, accountId, epoch)
            )
            assertEquals(ExternalInputFailure.TOO_LARGE, rejection.reason)
            assertNull(claimSharedTaskPayloadForReview(nextId, accountId, epoch))

            assertTrue(completeSharedTaskReview(rejectionId))
            assertNotNull(claimSharedTaskPayloadForReview(nextId, accountId, epoch))
        } finally {
            completeSharedTaskReview(rejectionId)
            completeSharedTaskReview(nextId)
            clearSharedTaskPayload(rejectionId)
            clearSharedTaskPayload(nextId)
            deactivateSharedTaskIntake(accountId, epoch)
        }
    }

    @Test
    fun rejectionFeedbackCoroutineRetainsTheReviewAcrossItsOwnQueueRevision() = runTest {
        val accountId = "feedback-account"
        val epoch = 803L
        val rejectionId = 91_601L
        val nextId = 91_602L
        val feedbackStarted = CompletableDeferred<Unit>()
        val finishFeedback = CompletableDeferred<Unit>()
        try {
            publishSharedTaskPayloadRejection(rejectionId, ExternalInputFailure.TOO_LARGE)
            publishSharedTaskPayload(nextId, description = "next")
            updateSharedTaskIntakeReadiness(accountId, epoch, isMounted = true, isUiBusy = false)
            assertNotNull(claimSharedTaskRejectionForReview(rejectionId, accountId, epoch))

            val feedbackJob = launchSharedTaskRejectionFeedback(rejectionId) {
                feedbackStarted.complete(Unit)
                finishFeedback.await()
            }
            feedbackStarted.await()
            assertEquals(rejectionId, sharedTaskIntakeStatus.value.activeReviewId)
            assertNull(claimSharedTaskPayloadForReview(nextId, accountId, epoch))

            finishFeedback.complete(Unit)
            feedbackJob.join()
            assertNull(sharedTaskIntakeStatus.value.activeReviewId)
            assertNotNull(claimSharedTaskPayloadForReview(nextId, accountId, epoch))
        } finally {
            finishFeedback.complete(Unit)
            completeSharedTaskReview(rejectionId)
            completeSharedTaskReview(nextId)
            clearSharedTaskPayload(rejectionId)
            clearSharedTaskPayload(nextId)
            deactivateSharedTaskIntake(accountId, epoch)
        }
    }

    @Test
    fun activeReviewSurvivesBackgroundAndStillBlocksNextAdmission() {
        val accountId = "review-lifecycle-account"
        val epoch = 804L
        val reviewId = 91_701L
        val nextId = 91_702L
        try {
            updateSharedTaskIntakeAppActive(true)
            publishSharedTaskPayload(reviewId, description = "under review")
            updateSharedTaskIntakeReadiness(accountId, epoch, isMounted = true, isUiBusy = false)
            assertNotNull(claimSharedTaskPayloadForReview(reviewId, accountId, epoch))

            updateSharedTaskIntakeAppActive(false)
            updateSharedTaskIntakeAppActive(true)

            assertEquals(reviewId, sharedTaskIntakeStatus.value.activeReviewId)
            assertNull(reserveSharedTaskIntake(nextId))
            assertTrue(completeSharedTaskReview(reviewId))
            assertNotNull(reserveSharedTaskIntake(nextId))
        } finally {
            completeSharedTaskReview(reviewId)
            releaseSharedTaskPayloadReservation(nextId)
            clearSharedTaskPayload(reviewId)
            deactivateSharedTaskIntake(accountId, epoch)
            updateSharedTaskIntakeAppActive(true)
        }
    }

    @Test
    fun legacyQueueCapacityRemainsBounded() {
        val ids = (0L until 64L).map { 92_000L + it }
        try {
            ids.forEach { assertTrue(reserveSharedTaskPayload(it)) }
            assertFalse(reserveSharedTaskPayload(92_100L))
        } finally {
            ids.forEach(::releaseSharedTaskPayloadReservation)
            releaseSharedTaskPayloadReservation(92_100L)
        }
    }

    private fun validIcs(uid: String): String = """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        UID:$uid
        SUMMARY:Shared event
        DTSTART;VALUE=DATE:20260504
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()
}
