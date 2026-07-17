package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val syncNow: suspend () -> Unit = { triggerSyncAction.syncNow() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun triggerSync() {
        if (!_isRefreshing.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch(ioDispatcher) {
            try {
                syncNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Pull-to-refresh sync failed" }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
