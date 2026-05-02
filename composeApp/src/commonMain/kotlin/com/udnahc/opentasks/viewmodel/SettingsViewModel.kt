package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.calendar.CalendarPermissionStatus
import com.udnahc.opentasks.data.notification.ExactReminderPermissionStatus
import com.udnahc.opentasks.data.model.TextSizePreference
import com.udnahc.opentasks.data.model.ThemeMode
import com.udnahc.opentasks.domain.action.settings.ClearLocalDataAction
import com.udnahc.opentasks.domain.action.settings.ClearPocketBaseUrlAction
import com.udnahc.opentasks.domain.action.settings.SavePocketBaseUrlAction
import com.udnahc.opentasks.domain.action.settings.SaveTextSizePreferenceAction
import com.udnahc.opentasks.domain.action.settings.SaveThemePreferenceAction
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import com.udnahc.opentasks.domain.usecase.settings.CheckCalendarPermissionUseCase
import com.udnahc.opentasks.domain.usecase.settings.CheckNotificationPermissionUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObservePocketBaseUrlUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveTextSizePreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveThemePreferenceUseCase
import com.udnahc.opentasks.domain.action.task.GenerateCsvExportAction
import com.udnahc.opentasks.domain.action.task.GenerateIcsExportAction
import com.udnahc.opentasks.ui.util.FileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.lighthousegames.logging.logging

private val log = logging("SettingsViewModel")

enum class SyncStatus { IDLE, SYNCING, SUCCESS, ERROR }

sealed class ExportResult {
    data object Idle : ExportResult()
    data class Success(val count: Int) : ExportResult()
    data object Error : ExportResult()
}

class SettingsViewModel(
    observePocketBaseUrl: ObservePocketBaseUrlUseCase,
    observeThemePreference: ObserveThemePreferenceUseCase,
    observeTextSizePreference: ObserveTextSizePreferenceUseCase,
    private val savePocketBaseUrlAction: SavePocketBaseUrlAction,
    private val clearPocketBaseUrlAction: ClearPocketBaseUrlAction,
    private val triggerSyncAction: TriggerSyncAction,
    private val saveThemePreferenceAction: SaveThemePreferenceAction,
    private val saveTextSizePreferenceAction: SaveTextSizePreferenceAction,
    private val clearLocalDataAction: ClearLocalDataAction,
    private val checkNotificationPermission: CheckNotificationPermissionUseCase,
    private val checkCalendarPermission: CheckCalendarPermissionUseCase,
    private val generateCsvExport: GenerateCsvExportAction,
    private val generateIcsExport: GenerateIcsExportAction,
    private val fileSaver: FileSaver,
) : ViewModel() {

    val pocketBaseUrl: StateFlow<String?> = observePocketBaseUrl()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val themePreference: StateFlow<ThemeMode> = observeThemePreference()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val textSizePreference: StateFlow<TextSizePreference> = observeTextSizePreference()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TextSizePreference.SMALL)

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _notificationGranted = MutableStateFlow(true)
    val notificationGranted: StateFlow<Boolean> = _notificationGranted.asStateFlow()

    private val _exactReminderStatus = MutableStateFlow(ExactReminderPermissionStatus.NOT_REQUIRED)
    val exactReminderStatus: StateFlow<ExactReminderPermissionStatus> = _exactReminderStatus.asStateFlow()

    private val _calendarGranted = MutableStateFlow(false)
    val calendarGranted: StateFlow<Boolean> = _calendarGranted.asStateFlow()

    private val _exportResult = MutableStateFlow<ExportResult>(ExportResult.Idle)
    val exportResult: StateFlow<ExportResult> = _exportResult.asStateFlow()

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

    fun saveTextSizePreference(preference: TextSizePreference) {
        viewModelScope.launch(Dispatchers.IO) { saveTextSizePreferenceAction(preference) }
    }

    fun openNotificationSettings() {
        checkNotificationPermission.openSettings()
    }

    fun openExactReminderSettings() {
        checkNotificationPermission.openExactReminderSettings()
    }

    fun recheckPermissions() {
        viewModelScope.launch(Dispatchers.IO) {
            _notificationGranted.value = checkNotificationPermission()
            _exactReminderStatus.value = checkNotificationPermission.exactReminderStatus()
            _calendarGranted.value = checkCalendarPermission() == CalendarPermissionStatus.GRANTED
        }
    }

    fun onCalendarPermissionResult(granted: Boolean) {
        _calendarGranted.value = granted
    }

    fun exportCsv() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (content, count) = generateCsvExport()
                val fileName = "opentasks_export.csv"
                val saved = fileSaver.save(fileName, content, "text/csv")
                _exportResult.value = if (saved) ExportResult.Success(count) else ExportResult.Error
            } catch (e: Exception) {
                log.e { "CSV export failed: ${e.message}" }
                _exportResult.value = ExportResult.Error
            }
        }
    }

    fun exportIcs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (content, count) = generateIcsExport()
                val fileName = "opentasks_export.ics"
                val saved = fileSaver.save(fileName, content, "text/calendar")
                _exportResult.value = if (saved) ExportResult.Success(count) else ExportResult.Error
            } catch (e: Exception) {
                log.e { "ICS export failed: ${e.message}" }
                _exportResult.value = ExportResult.Error
            }
        }
    }

    fun clearExportResult() {
        _exportResult.value = ExportResult.Idle
    }

    fun clearLocalData(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                clearLocalDataAction()
                _syncStatus.value = SyncStatus.IDLE
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                log.e { "Clear local data failed: ${e.message}" }
            }
        }
    }
}
