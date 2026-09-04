package com.udnahc.opentasks.viewmodel

import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.AccountBoundaryRejectedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.lighthousegames.logging.logging

private val log = logging("ForegroundMutationLauncher")

enum class TaskMutationFailureReason {
    BOUNDARY_CHANGED,
    OPERATION_FAILED,
}

data class TaskMutationFailureEvent(
    val id: Long,
    val reason: TaskMutationFailureReason,
)

internal class TaskMutationFailureEventStore {
    private val lastId = MutableStateFlow(0L)
    private val _event = MutableStateFlow<TaskMutationFailureEvent?>(null)
    val event: StateFlow<TaskMutationFailureEvent?> = _event.asStateFlow()

    fun publish(reason: TaskMutationFailureReason) {
        val id = nextId()
        val next = TaskMutationFailureEvent(id, reason)
        while (true) {
            val current = _event.value
            if (current != null && current.id >= id) return
            if (_event.compareAndSet(current, next)) return
        }
    }

    fun consume(event: TaskMutationFailureEvent): Boolean =
        _event.compareAndSet(expect = event, update = null)

    private fun nextId(): Long {
        while (true) {
            val current = lastId.value
            val next = current + 1L
            if (lastId.compareAndSet(current, next)) return next
        }
    }
}

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
                log.e { "Foreground mutation failed" }
                onFailure(error)
            }
        }
    }

}
