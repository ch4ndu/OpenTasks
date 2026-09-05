package com.udnahc.opentasks.data.sync

import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.AccountBoundaryRejectedException
import com.udnahc.opentasks.data.auth.CacheMode
import kotlin.coroutines.cancellation.CancellationException

suspend fun runScheduledSyncMaintenance(
    capturedBoundary: AccountBoundary,
    syncNetwork: suspend (AccountBoundary) -> Unit,
    withRevalidatedBoundary: suspend (
        AccountBoundary,
        suspend (AccountBoundary) -> Unit,
    ) -> Unit,
    maintenanceSteps: List<suspend (AccountBoundary) -> Unit>,
) {
    var syncFailure: Exception? = null
    if (capturedBoundary.mode == CacheMode.POCKETBASE) {
        try {
            withRevalidatedBoundary(capturedBoundary) { boundary ->
                syncNetwork(boundary)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: AccountBoundaryRejectedException) {
            throw error
        } catch (error: Exception) {
            error.rethrowSyncAuthenticationRejected()
            syncFailure = error
        }
    }

    val maintenanceFailures = mutableListOf<Exception>()
    try {
        withRevalidatedBoundary(capturedBoundary) { boundary ->
            maintenanceSteps.forEach { step ->
                try {
                    step(boundary)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: AccountBoundaryRejectedException) {
                    throw error
                } catch (error: Exception) {
                    error.rethrowSyncAuthenticationRejected()
                    maintenanceFailures += error
                }
            }
        }
    } catch (error: CancellationException) {
        maintenanceFailures.forEach(error::addSuppressed)
        throw error
    } catch (error: Exception) {
        val authenticationRejection = error.findSyncAuthenticationRejected()
        if (authenticationRejection != null) {
            maintenanceFailures.forEach(authenticationRejection::addSuppressed)
            throw authenticationRejection
        }
        val originalSyncFailure = syncFailure
        if (originalSyncFailure != null) {
            maintenanceFailures.forEach(originalSyncFailure::addSuppressed)
            originalSyncFailure.addSuppressed(error)
            throw originalSyncFailure
        }
        maintenanceFailures.forEach(error::addSuppressed)
        throw error
    }

    val originalSyncFailure = syncFailure
    if (originalSyncFailure != null) {
        maintenanceFailures.forEach(originalSyncFailure::addSuppressed)
        throw originalSyncFailure
    }
    if (maintenanceFailures.isNotEmpty()) {
        val firstFailure = maintenanceFailures.first()
        maintenanceFailures.drop(1).forEach(firstFailure::addSuppressed)
        throw firstFailure
    }
}
