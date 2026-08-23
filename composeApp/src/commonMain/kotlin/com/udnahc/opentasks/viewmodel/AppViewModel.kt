package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.SharedTaskPayload
import com.udnahc.opentasks.data.auth.AccountBoundary
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.AccountBoundaryRejectedException
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.domain.action.task.ImportCalendarEventsAction
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import com.udnahc.opentasks.domain.usecase.task.ParseIcsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lighthousegames.logging.logging

private val log = logging("AppViewModel")

sealed interface SharedIcsImportResult {
    val payloadId: Long

    data class Success(
        override val payloadId: Long,
        val importedCount: Int,
    ) : SharedIcsImportResult

    data class Failed(
        override val payloadId: Long,
        val cause: Throwable? = null,
    ) : SharedIcsImportResult
}

class AppViewModel(
    triggerSyncAction: TriggerSyncAction,
    private val parseIcsUseCase: ParseIcsUseCase,
    private val importCalendarEventsAction: ImportCalendarEventsAction,
    private val accountBoundaryExecutor: AccountBoundaryExecutor,
    private val syncNow: suspend () -> Unit = { triggerSyncAction.syncNow() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private data class PendingSharedIcs(
        val payload: SharedTaskPayload,
        val expectedBoundary: AccountBoundary?,
    )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _sharedIcsImportResult = MutableStateFlow<SharedIcsImportResult?>(null)
    val sharedIcsImportResult: StateFlow<SharedIcsImportResult?> = _sharedIcsImportResult.asStateFlow()
    private val sharedIcsPayloadIds = mutableSetOf<Long>()
    private val pendingSharedIcs = ArrayDeque<PendingSharedIcs>()
    private var activeSharedIcs: PendingSharedIcs? = null
    private var sharedIcsImportJob: Job? = null
    private var isEpochAlive = true

    /**
     * Starts the shared ICS workflow only after synchronously capturing the
     * foreground boundary. The result remains pending until the host consumes
     * it. Claimed payloads are owned by this epoch, so they are never returned
     * to the process-global handoff when this ViewModel is cleared.
     */
    fun importSharedIcs(payload: SharedTaskPayload) {
        if (!isEpochAlive || !sharedIcsPayloadIds.add(payload.id)) return

        // Capture before queueing or coroutine dispatch: every claimed
        // payload gets its own exact account/epoch boundary.
        val expectedBoundary = accountBoundaryExecutor.captureForegroundBoundary()
        pendingSharedIcs += PendingSharedIcs(payload, expectedBoundary)
        startNextSharedIcsIfIdle()
    }

    fun consumeSharedIcsImportResult(result: SharedIcsImportResult): Boolean {
        if (!_sharedIcsImportResult.compareAndSet(result, null)) return false
        if (activeSharedIcs?.payload?.id == result.payloadId) {
            activeSharedIcs = null
            sharedIcsImportJob = null
        }
        startNextSharedIcsIfIdle()
        return true
    }

    private fun startNextSharedIcsIfIdle() {
        if (!isEpochAlive || activeSharedIcs != null || _sharedIcsImportResult.value != null) return
        val next = pendingSharedIcs.removeFirstOrNull() ?: return
        activeSharedIcs = next
        val boundary = next.expectedBoundary
        if (boundary == null) {
            _sharedIcsImportResult.value = SharedIcsImportResult.Failed(
                payloadId = next.payload.id,
                cause = AccountBoundaryRejectedException(),
            )
            return
        }

        val payloadId = next.payload.id
        sharedIcsImportJob = viewModelScope.launch(ioDispatcher) {
            try {
                val events = parseIcsUseCase(next.payload.icsContent)
                if (events.isEmpty()) {
                    throw IllegalArgumentException("The shared ICS payload contains no events")
                }
                val importedCount = accountBoundaryExecutor.withForegroundActionBoundary(boundary) {
                    importCalendarEventsAction(events)
                }
                publishSharedIcsResult(
                    SharedIcsImportResult.Success(
                        payloadId = payloadId,
                        importedCount = importedCount,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Shared ICS import failed" }
                publishSharedIcsResult(SharedIcsImportResult.Failed(payloadId, e))
            } finally {
                withContext(NonCancellable) {
                    finishSharedIcs(payloadId)
                }
            }
        }
    }

    private suspend fun publishSharedIcsResult(result: SharedIcsImportResult) {
        withContext(Dispatchers.Main.immediate) {
            if (isEpochAlive && activeSharedIcs?.payload?.id == result.payloadId) {
                _sharedIcsImportResult.value = result
            }
        }
    }

    private suspend fun finishSharedIcs(payloadId: Long) {
        withContext(Dispatchers.Main.immediate) {
            if (!isEpochAlive) return@withContext
            if (activeSharedIcs?.payload?.id != payloadId) return@withContext

            // A terminal result remains owned until the host consumes it. A
            // child cancellation has no result, so it advances immediately.
            sharedIcsImportJob = null
            if (_sharedIcsImportResult.value == null) {
                activeSharedIcs = null
                startNextSharedIcsIfIdle()
            }
        }
    }

    override fun onCleared() {
        isEpochAlive = false
        pendingSharedIcs.clear()
        activeSharedIcs = null
        _sharedIcsImportResult.value = null
        sharedIcsImportJob?.cancel()
        sharedIcsImportJob = null
        sharedIcsPayloadIds.clear()
        super.onCleared()
    }

    fun triggerSync() {
        val expectedBoundary = accountBoundaryExecutor.captureAuthenticatedForegroundBoundary()
        if (expectedBoundary == null) return
        if (!_isRefreshing.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch(ioDispatcher) {
            try {
                accountBoundaryExecutor.withForegroundActionBoundary(expectedBoundary) {
                    syncNow()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: AccountBoundaryRejectedException) {
                log.w { "Pull-to-refresh skipped because the foreground account boundary changed" }
            } catch (e: Exception) {
                log.e(e) { "Pull-to-refresh sync failed" }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
