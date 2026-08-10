package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.AccountBoundaryRejectedException
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.lighthousegames.logging.logging

private val log = logging("AppViewModel")

class AppViewModel(
    triggerSyncAction: TriggerSyncAction,
    private val accountBoundaryExecutor: AccountBoundaryExecutor? = null,
    private val syncNow: suspend () -> Unit = { triggerSyncAction.syncNow() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun triggerSync() {
        val expectedBoundary = accountBoundaryExecutor?.captureForegroundBoundary()
        if (accountBoundaryExecutor != null && expectedBoundary == null) return
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
