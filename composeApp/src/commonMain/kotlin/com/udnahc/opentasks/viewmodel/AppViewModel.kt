package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.lighthousegames.logging.logging

private val log = logging("AppViewModel")

class AppViewModel(
    private val triggerSyncAction: TriggerSyncAction,
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun triggerSync() {
        if (_isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                triggerSyncAction.syncNow()
            } catch (e: Exception) {
                log.e(e) { "Pull-to-refresh sync failed" }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
