package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.domain.action.settings.ClearPocketBaseUrlAction
import com.udnahc.opentasks.domain.action.settings.SavePocketBaseUrlAction
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import com.udnahc.opentasks.domain.usecase.settings.ObservePocketBaseUrlUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SyncStatus { IDLE, SYNCING, SUCCESS, ERROR }

class SettingsViewModel(
    observePocketBaseUrl: ObservePocketBaseUrlUseCase,
    private val savePocketBaseUrlAction: SavePocketBaseUrlAction,
    private val clearPocketBaseUrlAction: ClearPocketBaseUrlAction,
    private val triggerSyncAction: TriggerSyncAction,
) : ViewModel() {

    val pocketBaseUrl: StateFlow<String?> = observePocketBaseUrl()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    fun savePocketBaseUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            clearPocketBaseUrl()
            return
        }
        val normalized = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "http://$trimmed"
        } else {
            trimmed
        }
        viewModelScope.launch(Dispatchers.IO) {
            _syncStatus.value = SyncStatus.SYNCING
            try {
                savePocketBaseUrlAction(normalized)
                _syncStatus.value = SyncStatus.SUCCESS
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.ERROR
            }
        }
    }

    fun clearPocketBaseUrl() {
        viewModelScope.launch(Dispatchers.IO) {
            clearPocketBaseUrlAction()
            _syncStatus.value = SyncStatus.IDLE
        }
    }

    fun triggerSync() {
        viewModelScope.launch(Dispatchers.IO) {
            _syncStatus.value = SyncStatus.SYNCING
            try {
                triggerSyncAction()
                _syncStatus.value = SyncStatus.SUCCESS
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.ERROR
            }
        }
    }
}
