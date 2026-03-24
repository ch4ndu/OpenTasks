package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.ThemeMode
import com.udnahc.opentasks.domain.action.settings.ClearLocalDataAction
import com.udnahc.opentasks.domain.action.settings.ClearPocketBaseUrlAction
import com.udnahc.opentasks.domain.action.settings.SavePocketBaseUrlAction
import com.udnahc.opentasks.domain.action.settings.SaveThemePreferenceAction
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import com.udnahc.opentasks.domain.usecase.settings.ObservePocketBaseUrlUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveThemePreferenceUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SyncStatus { IDLE, SYNCING, SUCCESS, ERROR }

class SettingsViewModel(
    observePocketBaseUrl: ObservePocketBaseUrlUseCase,
    observeThemePreference: ObserveThemePreferenceUseCase,
    private val savePocketBaseUrlAction: SavePocketBaseUrlAction,
    private val clearPocketBaseUrlAction: ClearPocketBaseUrlAction,
    private val triggerSyncAction: TriggerSyncAction,
    private val saveThemePreferenceAction: SaveThemePreferenceAction,
    private val clearLocalDataAction: ClearLocalDataAction,
) : ViewModel() {

    val pocketBaseUrl: StateFlow<String?> = observePocketBaseUrl()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val themePreference: StateFlow<ThemeMode> = observeThemePreference()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

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

    fun saveThemePreference(mode: ThemeMode) {
        viewModelScope.launch(Dispatchers.IO) { saveThemePreferenceAction(mode) }
    }

    fun clearLocalData(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            clearLocalDataAction()
            _syncStatus.value = SyncStatus.IDLE
        }.invokeOnCompletion { onComplete() }
    }
}
