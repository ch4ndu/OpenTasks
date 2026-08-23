package com.udnahc.opentasks.data.notification

import com.udnahc.opentasks.data.model.COUNTDOWN_ID_PREFIX
import kotlin.coroutines.cancellation.CancellationException

/** Canonical, pre-async delivery identity extracted from an alarm payload. */
internal data class DeliveryCommandPayload(
    val eventId: String,
    val semanticKey: String,
    val occurrenceUtcMillis: Long?,
    val accountId: String?,
    val boundaryEpoch: Long,
)

internal data class ValidatedDeliveryCommand(
    val eventId: String,
    val semanticKey: String,
    val occurrenceUtcMillis: Long,
    val accountId: String,
    val boundaryEpoch: Long,
)

/**
 * Alarm delivery has no action string, so its event namespace selects the
 * canonical command kind before any asynchronous account or repository work.
 */
internal fun DeliveryCommandPayload.validatedDeliveryCommand(): ValidatedDeliveryCommand? {
    val command = if (eventId.startsWith(COUNTDOWN_ID_PREFIX)) {
        ReminderCommand.COUNTDOWN_DELIVERY
    } else {
        ReminderCommand.DELIVERY
    }
    val validation = validateReminderCommand(
        command = command,
        semanticKey = semanticKey,
        eventId = eventId,
        occurrenceUtcMillis = occurrenceUtcMillis,
        accountId = accountId,
        boundaryEpoch = boundaryEpoch,
    )
    if (validation !is ReminderCommandValidation.Accepted) return null
    return ValidatedDeliveryCommand(
        eventId = eventId,
        semanticKey = semanticKey,
        occurrenceUtcMillis = occurrenceUtcMillis ?: return null,
        accountId = accountId ?: return null,
        boundaryEpoch = boundaryEpoch,
    )
}

/** The result of looking up and validating current persisted reminder truth. */
internal sealed interface ReminderDeliveryResolution {
    data object DiscardExact : ReminderDeliveryResolution
    data object DiscardAll : ReminderDeliveryResolution

    data class Deliver(
        val prepareCurrentDisplay: suspend () -> Int?,
        val chainNextOccurrence: suspend () -> Unit,
        val displayCurrent: suspend (notificationId: Int) -> Boolean,
    ) : ReminderDeliveryResolution
}

/**
 * Runs delivery only after current persisted truth is confirmed. Prior-display
 * cleanup and recurrence chaining are maintenance: each is best effort and
 * neither can authorize or suppress the one current display attempt.
 */
internal suspend fun runValidatedReminderDelivery(
    resolveCurrent: suspend () -> ReminderDeliveryResolution,
    cleanupPriorDisplays: suspend () -> Unit,
    discardExact: suspend () -> Unit,
    discardAll: suspend () -> Unit,
    logOperationalFailure: (phase: String, error: Exception) -> Unit,
) {
    val resolution = try {
        resolveCurrent()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        logOperationalFailure("persisted-truth lookup", error)
        discardBestEffort(discardExact, "lookup cleanup", logOperationalFailure)
        return
    }
    when (resolution) {
        ReminderDeliveryResolution.DiscardExact -> {
            discardBestEffort(discardExact, "exact cleanup", logOperationalFailure)
            return
        }

        ReminderDeliveryResolution.DiscardAll -> {
            discardBestEffort(discardAll, "event cleanup", logOperationalFailure)
            return
        }

        is ReminderDeliveryResolution.Deliver -> {
            val notificationId = resolution.prepareCurrentDisplay()
            if (notificationId == null) {
                discardBestEffort(discardExact, "missing allocation cleanup", logOperationalFailure)
                return
            }
            runBestEffort(cleanupPriorDisplays, "prior display cleanup", logOperationalFailure)
            runBestEffort(resolution.chainNextOccurrence, "next occurrence chaining", logOperationalFailure)
            if (!resolution.displayCurrent(notificationId)) {
                discardBestEffort(discardExact, "denied display cleanup", logOperationalFailure)
            }
        }
    }
}

private suspend fun runBestEffort(
    block: suspend () -> Unit,
    phase: String,
    logOperationalFailure: (phase: String, error: Exception) -> Unit,
) {
    try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        logOperationalFailure(phase, error)
    }
}

private suspend fun discardBestEffort(
    discard: suspend () -> Unit,
    phase: String,
    logOperationalFailure: (phase: String, error: Exception) -> Unit,
) = runBestEffort(discard, phase, logOperationalFailure)
