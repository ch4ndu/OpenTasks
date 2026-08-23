package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.calendar.CalendarPermissionStatus
import com.udnahc.opentasks.data.model.TextSizePreference
import com.udnahc.opentasks.data.model.ThemeMode
import com.udnahc.opentasks.data.notification.ExactReminderPermissionStatus
import com.udnahc.opentasks.data.sync.SyncOutcome
import com.udnahc.opentasks.domain.action.settings.ClearLocalDataAction
import com.udnahc.opentasks.domain.action.settings.SaveTextSizePreferenceAction
import com.udnahc.opentasks.domain.action.settings.SaveThemePreferenceAction
import com.udnahc.opentasks.domain.action.settings.TriggerSyncAction
import com.udnahc.opentasks.domain.usecase.settings.CheckCalendarPermissionUseCase
import com.udnahc.opentasks.domain.usecase.settings.CheckNotificationPermissionUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveTextSizePreferenceUseCase
import com.udnahc.opentasks.domain.usecase.settings.ObserveThemePreferenceUseCase
import com.udnahc.opentasks.domain.usecase.task.GenerateCsvExportUseCase
import com.udnahc.opentasks.domain.usecase.task.GenerateIcsExportUseCase
import com.udnahc.opentasks.ui.util.FileExportRequest
import com.udnahc.opentasks.ui.util.FileExportResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lighthousegames.logging.logging

private val log = logging("SettingsViewModel")

enum class SyncStatus { IDLE, CHECKING, SYNCING, SUCCESS, ERROR, SYNC_ERROR }

sealed class ExportResult {
    data object Idle : ExportResult()
    data class Success(val count: Int) : ExportResult()
    data object Error : ExportResult()
}

enum class ClearLocalDataStatus { IDLE, CLEARING, ERROR }

class SettingsViewModel(
    observeThemePreference: ObserveThemePreferenceUseCase,
    observeTextSizePreference: ObserveTextSizePreferenceUseCase,
    private val triggerSyncAction: TriggerSyncAction,
    private val saveThemePreferenceAction: SaveThemePreferenceAction,
    private val saveTextSizePreferenceAction: SaveTextSizePreferenceAction,
    private val clearLocalDataAction: ClearLocalDataAction,
    private val checkNotificationPermission: CheckNotificationPermissionUseCase,
    private val checkCalendarPermission: CheckCalendarPermissionUseCase,
    private val generateCsvExport: GenerateCsvExportUseCase,
    private val generateIcsExport: GenerateIcsExportUseCase,
) : ViewModel() {

    val themePreference: StateFlow<ThemeMode> = observeThemePreference()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val textSizePreference: StateFlow<TextSizePreference> = observeTextSizePreference()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TextSizePreference.SMALL)

    val syncStatus: StateFlow<SyncStatus> = triggerSyncAction.outcome
        .map(::toSettingsSyncStatus)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.IDLE)

    private val _notificationGranted = MutableStateFlow(true)
    val notificationGranted: StateFlow<Boolean> = _notificationGranted.asStateFlow()

    private val _exactReminderStatus = MutableStateFlow(ExactReminderPermissionStatus.NOT_REQUIRED)
    val exactReminderStatus: StateFlow<ExactReminderPermissionStatus> =
        _exactReminderStatus.asStateFlow()

    private val _calendarGranted = MutableStateFlow(false)
    val calendarGranted: StateFlow<Boolean> = _calendarGranted.asStateFlow()

    private val _exportResult = MutableStateFlow<ExportResult>(ExportResult.Idle)
    val exportResult: StateFlow<ExportResult> = _exportResult.asStateFlow()
    private val _exportInProgress = MutableStateFlow(false)
    val exportInProgress: StateFlow<Boolean> = _exportInProgress.asStateFlow()
    private var pendingExportCount = 0

    private val _clearLocalDataStatus = MutableStateFlow(ClearLocalDataStatus.IDLE)
    val clearLocalDataStatus: StateFlow<ClearLocalDataStatus> =
        _clearLocalDataStatus.asStateFlow()

    init {
        recheckPermissions()
    }

    fun triggerSync() {
        log.d { "Manual sync triggered" }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                triggerSyncAction.syncNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Sync failed" }
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

    fun prepareCsvExport(onReady: (FileExportRequest) -> Unit) {
        if (!_exportInProgress.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch(Dispatchers.IO) {
            var handedOff = false
            try {
                val (content, count) = generateCsvExport()
                pendingExportCount = count
                withContext(Dispatchers.Main) {
                    onReady(FileExportRequest("opentasks_export.csv", content, "text/csv"))
                    handedOff = true
                }
            } catch (e: CancellationException) {
                if (!handedOff) resetExportPreparation()
                throw e
            } catch (e: Exception) {
                if (handedOff) return@launch
                log.e(e) { "CSV export failed" }
                _exportResult.value = ExportResult.Error
                resetExportPreparation()
            }
        }
    }

    fun prepareIcsExport(onReady: (FileExportRequest) -> Unit) {
        if (!_exportInProgress.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch(Dispatchers.IO) {
            var handedOff = false
            try {
                val (content, count) = generateIcsExport()
                pendingExportCount = count
                withContext(Dispatchers.Main) {
                    onReady(FileExportRequest("opentasks_export.ics", content, "text/calendar"))
                    handedOff = true
                }
            } catch (e: CancellationException) {
                if (!handedOff) resetExportPreparation()
                throw e
            } catch (e: Exception) {
                if (handedOff) return@launch
                log.e(e) { "ICS export failed" }
                _exportResult.value = ExportResult.Error
                resetExportPreparation()
            }
        }
    }

    fun onExportResult(result: FileExportResult) {
        _exportResult.value = result.toUiResult(pendingExportCount)
        pendingExportCount = 0
        _exportInProgress.value = false
    }

    fun clearExportResult() {
        _exportResult.value = ExportResult.Idle
    }

    private fun resetExportPreparation() {
        pendingExportCount = 0
        _exportInProgress.value = false
    }

    fun clearLocalData() {
        viewModelScope.launch(Dispatchers.IO) {
            _clearLocalDataStatus.value = ClearLocalDataStatus.CLEARING
            try {
                clearLocalDataAction()
                _clearLocalDataStatus.value = ClearLocalDataStatus.IDLE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Clear local data failed" }
                _clearLocalDataStatus.value = ClearLocalDataStatus.ERROR
            } finally {
                if (_clearLocalDataStatus.value == ClearLocalDataStatus.CLEARING) {
                    _clearLocalDataStatus.value = ClearLocalDataStatus.IDLE
                }
            }
        }
    }

    fun clearLocalDataErrorShown() {
        _clearLocalDataStatus.value = ClearLocalDataStatus.IDLE
    }
}

internal fun FileExportResult.toUiResult(count: Int): ExportResult = when (this) {
    FileExportResult.Completed -> ExportResult.Success(count)
    FileExportResult.Cancelled -> ExportResult.Idle
    is FileExportResult.Error -> ExportResult.Error
}

internal fun toSettingsSyncStatus(outcome: SyncOutcome): SyncStatus = when (outcome) {
    SyncOutcome.Idle -> SyncStatus.IDLE
    SyncOutcome.Syncing -> SyncStatus.SYNCING
    SyncOutcome.Success -> SyncStatus.SUCCESS
    SyncOutcome.Failed,
    SyncOutcome.ReauthenticationRequired -> SyncStatus.SYNC_ERROR
}
