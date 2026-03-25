package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.calendar.CalendarPermissionStatus
import com.udnahc.opentasks.data.calendar.CalendarProvider
import com.udnahc.opentasks.data.model.ThemeMode
import com.udnahc.opentasks.data.notification.NotificationPermissionChecker
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
import org.lighthousegames.logging.logging

private val log = logging("SettingsViewModel")

enum class SyncStatus { IDLE, SYNCING, SUCCESS, ERROR }

class SettingsViewModel(
    observePocketBaseUrl: ObservePocketBaseUrlUseCase,
    observeThemePreference: ObserveThemePreferenceUseCase,
    private val savePocketBaseUrlAction: SavePocketBaseUrlAction,
    private val clearPocketBaseUrlAction: ClearPocketBaseUrlAction,
    private val triggerSyncAction: TriggerSyncAction,
    private val saveThemePreferenceAction: SaveThemePreferenceAction,
    private val clearLocalDataAction: ClearLocalDataAction,
    private val notificationPermissionChecker: NotificationPermissionChecker,
    private val calendarProvider: CalendarProvider,
) : ViewModel() {

    val pocketBaseUrl: StateFlow<String?> = observePocketBaseUrl()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val themePreference: StateFlow<ThemeMode> = observeThemePreference()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _notificationGranted = MutableStateFlow(true)
    val notificationGranted: StateFlow<Boolean> = _notificationGranted.asStateFlow()

    private val _calendarGranted = MutableStateFlow(false)
    val calendarGranted: StateFlow<Boolean> = _calendarGranted.asStateFlow()

    init { recheckPermissions() }

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
        log.d { "Saving PocketBase URL: $normalized" }
        viewModelScope.launch(Dispatchers.IO) {
            _syncStatus.value = SyncStatus.SYNCING
            try {
                savePocketBaseUrlAction(normalized)
                _syncStatus.value = SyncStatus.SUCCESS
            } catch (e: Exception) {
                log.e { "Failed to save PocketBase URL: ${e.message}" }
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
        log.d { "Manual sync triggered" }
        viewModelScope.launch(Dispatchers.IO) {
            _syncStatus.value = SyncStatus.SYNCING
            try {
                triggerSyncAction()
                _syncStatus.value = SyncStatus.SUCCESS
            } catch (e: Exception) {
                log.e { "Sync failed: ${e.message}" }
                _syncStatus.value = SyncStatus.ERROR
            }
        }
    }

    fun saveThemePreference(mode: ThemeMode) {
        viewModelScope.launch(Dispatchers.IO) { saveThemePreferenceAction(mode) }
    }

    fun openNotificationSettings() {
        notificationPermissionChecker.openSettings()
    }

    fun recheckPermissions() {
        viewModelScope.launch(Dispatchers.IO) {
            _notificationGranted.value = notificationPermissionChecker.isGranted()
            _calendarGranted.value = calendarProvider.checkPermission() == CalendarPermissionStatus.GRANTED
        }
    }

    fun onCalendarPermissionResult(granted: Boolean) {
        _calendarGranted.value = granted
    }

    fun clearLocalData(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            clearLocalDataAction()
            _syncStatus.value = SyncStatus.IDLE
        }.invokeOnCompletion { onComplete() }
    }
}
