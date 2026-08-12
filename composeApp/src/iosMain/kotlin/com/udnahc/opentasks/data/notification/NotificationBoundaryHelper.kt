package com.udnahc.opentasks.data.notification

import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("NotificationBoundaryHelper")

/**
 * Bridges the suspend account-boundary check to the Swift notification
 * delegate. A valid callback is invoked from inside the held boundary so the
 * presentation decision cannot race an account transition.
 */
object NotificationBoundaryHelper : KoinComponent {
    private val accountBoundaryExecutor: AccountBoundaryExecutor by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Restores and proves the current boundary before native delivered-notification
     * maintenance begins. The returned account and epoch must be used together
     * when the native notification lookup completes later.
     */
    fun currentBoundary(completion: (String?, String?) -> Unit) {
        scope.launch {
            var completed = false
            fun finish(accountId: String?, boundaryEpoch: String?) {
                if (completed) return
                completed = true
                completion(accountId, boundaryEpoch)
            }

            try {
                val boundary = accountBoundaryExecutor.currentBoundary()
                finish(boundary?.accountId, boundary?.boundaryEpoch?.toString())
            } catch (error: CancellationException) {
                finish(null, null)
            } catch (error: Exception) {
                log.e(error) { "Unable to restore the account boundary for delivered-notification cleanup" }
                finish(null, null)
            }
        }
    }

    fun validate(
        accountId: String?,
        boundaryEpoch: Long,
        completion: (Boolean) -> Unit,
    ) {
        scope.launch {
            var completed = false
            fun finish(allowed: Boolean) {
                if (completed) return
                completed = true
                completion(allowed)
            }

            try {
                val accepted = accountBoundaryExecutor.withActiveCacheBoundary(
                    expectedAccountId = accountId,
                    expectedBoundaryEpoch = boundaryEpoch,
                ) {
                    finish(true)
                    true
                }
                if (accepted != true) finish(false)
            } catch (error: CancellationException) {
                finish(false)
            } catch (error: Exception) {
                log.e(error) { "Unable to validate the account boundary for notification presentation" }
                finish(false)
            }
        }
    }
}
