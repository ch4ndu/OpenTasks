package com.udnahc.opentasks.viewmodel

import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.AccountBoundaryRejectedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.lighthousegames.logging.logging

private val log = logging("ForegroundMutationLauncher")

/**
 * Starts a foreground account mutation only after synchronously capturing the
 * active boundary. The same boundary is revalidated inside the launched
 * coroutine before any account-owned work begins.
 */
class ForegroundMutationLauncher(
    private val accountBoundaryExecutor: AccountBoundaryExecutor?,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun launch(
        onBoundaryRejected: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {},
        action: suspend () -> Unit,
    ) {
        val expectedBoundary = accountBoundaryExecutor?.captureForegroundBoundary()
        if (accountBoundaryExecutor != null && expectedBoundary == null) {
            onBoundaryRejected()
            return
        }
        scope.launch(dispatcher) {
            try {
                if (accountBoundaryExecutor == null) {
                    action()
                } else {
                    accountBoundaryExecutor.withForegroundBoundary(
                        expectedBoundary ?: throw AccountBoundaryRejectedException(),
                    ) {
                        action()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: AccountBoundaryRejectedException) {
                log.w { "Foreground mutation skipped because the account boundary changed" }
                onBoundaryRejected()
            } catch (error: Exception) {
                log.e(error) { "Foreground mutation failed" }
                onFailure(error)
            }
        }
    }

}
