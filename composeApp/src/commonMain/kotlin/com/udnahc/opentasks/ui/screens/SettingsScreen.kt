package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.udnahc.opentasks.data.notification.ExactReminderPermissionStatus
import com.udnahc.opentasks.data.model.TextSizePreference
import com.udnahc.opentasks.data.model.ThemeMode
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.util.rememberCalendarPermissionLauncher
import com.udnahc.opentasks.ui.util.rememberNotificationPermissionLauncher
import com.udnahc.opentasks.viewmodel.ExportResult
import com.udnahc.opentasks.viewmodel.SettingsViewModel
import com.udnahc.opentasks.viewmodel.SyncStatus
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.back
import opentasks.composeapp.generated.resources.clear
import opentasks.composeapp.generated.resources.connected
import opentasks.composeapp.generated.resources.export_csv
import opentasks.composeapp.generated.resources.export_error
import opentasks.composeapp.generated.resources.export_header
import opentasks.composeapp.generated.resources.export_ics
import opentasks.composeapp.generated.resources.export_success
import opentasks.composeapp.generated.resources.cancel
import opentasks.composeapp.generated.resources.import_header
import opentasks.composeapp.generated.resources.appearance
import opentasks.composeapp.generated.resources.account
import opentasks.composeapp.generated.resources.import_csv_ticktick
import opentasks.composeapp.generated.resources.logout
import opentasks.composeapp.generated.resources.logout_confirm_message
import opentasks.composeapp.generated.resources.logout_confirm_title
import opentasks.composeapp.generated.resources.logout_description
import opentasks.composeapp.generated.resources.notifications
import opentasks.composeapp.generated.resources.exact_reminder_timing
import opentasks.composeapp.generated.resources.calendar_access
import opentasks.composeapp.generated.resources.permission_granted
import opentasks.composeapp.generated.resources.permission_not_granted
import opentasks.composeapp.generated.resources.permission_not_required
import opentasks.composeapp.generated.resources.permissions
import opentasks.composeapp.generated.resources.theme
import opentasks.composeapp.generated.resources.theme_dark
import opentasks.composeapp.generated.resources.theme_light
import opentasks.composeapp.generated.resources.theme_system
import opentasks.composeapp.generated.resources.import_from_calendar
import opentasks.composeapp.generated.resources.import_from_ics
import opentasks.composeapp.generated.resources.not_configured
import opentasks.composeapp.generated.resources.pocketbase_url
import opentasks.composeapp.generated.resources.pocketbase_url_hint
import opentasks.composeapp.generated.resources.save
import opentasks.composeapp.generated.resources.settings
import opentasks.composeapp.generated.resources.sync
import opentasks.composeapp.generated.resources.sync_error
import opentasks.composeapp.generated.resources.syncing
import opentasks.composeapp.generated.resources.text_size
import opentasks.composeapp.generated.resources.text_size_large
import opentasks.composeapp.generated.resources.text_size_medium
import opentasks.composeapp.generated.resources.text_size_small

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onImportCalendar: () -> Unit = {},
    onImportIcs: () -> Unit = {},
    onImportCsv: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val currentUrl by viewModel.pocketBaseUrl.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val themePreference by viewModel.themePreference.collectAsState()
    val textSizePreference by viewModel.textSizePreference.collectAsState()
    val notificationGranted by viewModel.notificationGranted.collectAsState()
    val exactReminderStatus by viewModel.exactReminderStatus.collectAsState()
    val calendarGranted by viewModel.calendarGranted.collectAsState()

    val exportResult by viewModel.exportResult.collectAsState()

    val requestNotification = rememberNotificationPermissionLauncher { granted ->
        viewModel.recheckPermissions()
    }
    val requestCalendar = rememberCalendarPermissionLauncher { granted ->
        viewModel.onCalendarPermissionResult(granted)
    }

    LifecycleResumeEffect(Unit) {
        viewModel.recheckPermissions()
        onPauseOrDispose { }
    }

    SettingsContent(
        currentUrl = currentUrl,
        syncStatus = syncStatus,
        themePreference = themePreference,
        textSizePreference = textSizePreference,
        notificationGranted = notificationGranted,
        exactReminderStatus = exactReminderStatus,
        calendarGranted = calendarGranted,
        exportResult = exportResult,
        onBack = onBack,
        onSaveUrl = { viewModel.savePocketBaseUrl(it) },
        onClearUrl = { viewModel.clearPocketBaseUrl() },
        onSyncNow = { viewModel.triggerSync() },
        onThemeChanged = { viewModel.saveThemePreference(it) },
        onTextSizeChanged = { viewModel.saveTextSizePreference(it) },
        onRequestNotificationPermission = {
            requestNotification()
        },
        onRequestExactReminderSettings = { viewModel.openExactReminderSettings() },
        onRequestCalendarPermission = requestCalendar,
        onImportCalendar = onImportCalendar,
        onImportIcs = onImportIcs,
        onImportCsv = onImportCsv,
        onExportCsv = { viewModel.exportCsv() },
        onExportIcs = { viewModel.exportIcs() },
        onClearExportResult = { viewModel.clearExportResult() },
        onLogout = { viewModel.clearLocalData(onLogout) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    currentUrl: String?,
    syncStatus: SyncStatus,
    themePreference: ThemeMode = ThemeMode.SYSTEM,
    textSizePreference: TextSizePreference = TextSizePreference.SMALL,
    notificationGranted: Boolean = true,
    exactReminderStatus: ExactReminderPermissionStatus = ExactReminderPermissionStatus.NOT_REQUIRED,
    calendarGranted: Boolean = false,
    exportResult: ExportResult = ExportResult.Idle,
    onBack: () -> Unit,
    onSaveUrl: (String) -> Unit,
    onClearUrl: () -> Unit,
    onSyncNow: () -> Unit = {},
    onThemeChanged: (ThemeMode) -> Unit = {},
    onTextSizeChanged: (TextSizePreference) -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onRequestExactReminderSettings: () -> Unit = {},
    onRequestCalendarPermission: () -> Unit = {},
    onImportCalendar: () -> Unit = {},
    onImportIcs: () -> Unit = {},
    onImportCsv: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onExportIcs: () -> Unit = {},
    onClearExportResult: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val dimens = OpenTasksTheme.dimens
    var showUrlDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showTextSizeDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(exportResult) {
        when (val result = exportResult) {
            is ExportResult.Success -> {
                val msg = getString(Res.string.export_success, result.count)
                snackbarHostState.showSnackbar(msg)
                onClearExportResult()
            }
            is ExportResult.Error -> {
                val msg = getString(Res.string.export_error)
                snackbarHostState.showSnackbar(msg)
                onClearExportResult()
            }
            ExportResult.Idle -> { /* no-op */ }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            OpenTasksTopBar(
                title = stringResource(Res.string.settings),
                navigationIcon = {
                    OpenTasksBackButton(onClick = onBack)
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── Appearance ──
            item(key = "appearance_header") {
                SettingsCategoryHeader(stringResource(Res.string.appearance))
            }
            item(key = "theme_preference") {
                val themeName = when (themePreference) {
                    ThemeMode.SYSTEM -> stringResource(Res.string.theme_system)
                    ThemeMode.LIGHT -> stringResource(Res.string.theme_light)
                    ThemeMode.DARK -> stringResource(Res.string.theme_dark)
                }
                SettingsRow(
                    title = stringResource(Res.string.theme),
                    summary = themeName,
                    onClick = { showThemeDialog = true },
                )
            }
            item(key = "text_size_preference") {
                SettingsRow(
                    title = stringResource(Res.string.text_size),
                    summary = textSizePreference.label(),
                    onClick = { showTextSizeDialog = true },
                )
            }

            // ── Permissions ──
            item(key = "permissions_header") {
                SettingsCategoryHeader(stringResource(Res.string.permissions))
            }
            item(key = "perm_notifications") {
                SettingsRow(
                    title = stringResource(Res.string.notifications),
                    summary = if (notificationGranted) stringResource(Res.string.permission_granted)
                    else stringResource(Res.string.permission_not_granted),
                    onClick = { if (!notificationGranted) onRequestNotificationPermission() },
                )
            }
            item(key = "perm_exact_reminders") {
                val exactSummary = when (exactReminderStatus) {
                    ExactReminderPermissionStatus.GRANTED -> stringResource(Res.string.permission_granted)
                    ExactReminderPermissionStatus.NOT_GRANTED -> stringResource(Res.string.permission_not_granted)
                    ExactReminderPermissionStatus.NOT_REQUIRED -> stringResource(Res.string.permission_not_required)
                }
                SettingsRow(
                    title = stringResource(Res.string.exact_reminder_timing),
                    summary = exactSummary,
                    onClick = {
                        if (exactReminderStatus == ExactReminderPermissionStatus.NOT_GRANTED) {
                            onRequestExactReminderSettings()
                        }
                    },
                )
            }
            item(key = "perm_calendar") {
                SettingsRow(
                    title = stringResource(Res.string.calendar_access),
                    summary = if (calendarGranted) stringResource(Res.string.permission_granted)
                    else stringResource(Res.string.permission_not_granted),
                    onClick = { if (!calendarGranted) onRequestCalendarPermission() },
                )
            }

            // ── Sync ──
            item(key = "sync_header") {
                SettingsCategoryHeader(stringResource(Res.string.sync))
            }
            item(key = "pocketbase_url") {
                val summary = currentUrl ?: stringResource(Res.string.not_configured)
                SettingsRow(
                    title = stringResource(Res.string.pocketbase_url),
                    summary = summary,
                    onClick = { showUrlDialog = true },
                )
            }
            if (currentUrl != null) {
                item(key = "sync_now") {
                    val summary = when (syncStatus) {
                        SyncStatus.SYNCING -> stringResource(Res.string.syncing)
                        SyncStatus.ERROR -> stringResource(Res.string.sync_error)
                        SyncStatus.SUCCESS -> stringResource(Res.string.connected)
                        SyncStatus.IDLE -> stringResource(Res.string.connected)
                    }
                    SettingsRow(
                        title = stringResource(Res.string.sync),
                        summary = summary,
                        onClick = onSyncNow,
                    )
                }
            }

            // ── Import ──
            item(key = "import_header") {
                SettingsCategoryHeader(stringResource(Res.string.import_header))
            }
            item(key = "import_calendar") {
                SettingsRow(
                    title = stringResource(Res.string.import_from_calendar),
                    onClick = onImportCalendar,
                )
            }
            item(key = "import_ics") {
                SettingsRow(
                    title = stringResource(Res.string.import_from_ics),
                    onClick = onImportIcs,
                )
            }
            item(key = "import_csv") {
                SettingsRow(
                    title = stringResource(Res.string.import_csv_ticktick),
                    onClick = onImportCsv,
                )
            }

            // ── Export ──
            item(key = "export_header") {
                SettingsCategoryHeader(stringResource(Res.string.export_header))
            }
            item(key = "export_csv") {
                SettingsRow(
                    title = stringResource(Res.string.export_csv),
                    onClick = onExportCsv,
                )
            }
            item(key = "export_ics") {
                SettingsRow(
                    title = stringResource(Res.string.export_ics),
                    onClick = onExportIcs,
                )
            }

            // ── Account ──
            item(key = "account_header") {
                SettingsCategoryHeader(stringResource(Res.string.account))
            }
            item(key = "logout") {
                SettingsRow(
                    title = stringResource(Res.string.logout),
                    summary = stringResource(Res.string.logout_description),
                    onClick = { showLogoutConfirm = true },
                )
            }
        }
    }

    // Logout confirmation dialog
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(Res.string.logout_confirm_title)) },
            text = { Text(stringResource(Res.string.logout_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    onLogout()
                }) {
                    Text(
                        stringResource(Res.string.logout),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    // Theme picker dialog
    if (showThemeDialog) {
        ThemePickerDialog(
            currentTheme = themePreference,
            onThemeSelected = { mode ->
                onThemeChanged(mode)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    // Text size picker dialog
    if (showTextSizeDialog) {
        TextSizePickerDialog(
            currentTextSize = textSizePreference,
            onTextSizeSelected = { preference ->
                onTextSizeChanged(preference)
                showTextSizeDialog = false
            },
            onDismiss = { showTextSizeDialog = false },
        )
    }

    // PocketBase URL dialog
    if (showUrlDialog) {
        PocketBaseUrlDialog(
            currentUrl = currentUrl,
            onSave = { url ->
                onSaveUrl(url)
                showUrlDialog = false
            },
            onClear = {
                onClearUrl()
                showUrlDialog = false
            },
            onDismiss = { showUrlDialog = false },
        )
    }
}

// ── Reusable settings composables ────────────────────────────────────────────

@Composable
internal fun SettingsCategoryHeader(title: String) {
    val dimens = OpenTasksTheme.dimens
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = PrimaryBlue,
        modifier = Modifier.padding(
            start = dimens.paddingXLarge,
            end = dimens.paddingXLarge,
            top = dimens.paddingXLarge,
            bottom = dimens.paddingSmall,
        ),
    )
}

@Composable
internal fun SettingsRow(
    title: String,
    summary: String? = null,
    onClick: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.paddingXLarge, vertical = dimens.paddingLarge),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (summary != null) {
            Spacer(Modifier.height(dimens.spacerTiny))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(horizontal = dimens.paddingXLarge),
    )
}

@Composable
private fun PocketBaseUrlDialog(
    currentUrl: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var urlInput by rememberSaveable(currentUrl) { mutableStateOf(currentUrl ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.pocketbase_url)) },
        text = {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                placeholder = { Text(stringResource(Res.string.pocketbase_url_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(urlInput) },
                enabled = urlInput.isNotBlank(),
            ) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            Row {
                if (currentUrl != null) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(Res.string.clear))
                    }
                    Spacer(Modifier.width(OpenTasksTheme.dimens.spacerSmall))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.back))
                }
            }
        },
    )
}

@Composable
private fun ThemePickerDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.theme)) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.SYSTEM -> stringResource(Res.string.theme_system)
                        ThemeMode.LIGHT -> stringResource(Res.string.theme_light)
                        ThemeMode.DARK -> stringResource(Res.string.theme_dark)
                    }
                    RadioOptionRow(
                        label = label,
                        isSelected = mode == currentTheme,
                        onClick = { onThemeSelected(mode) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun TextSizePickerDialog(
    currentTextSize: TextSizePreference,
    onTextSizeSelected: (TextSizePreference) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.text_size)) },
        text = {
            Column {
                TextSizePreference.entries.forEach { preference ->
                    RadioOptionRow(
                        label = preference.label(),
                        isSelected = preference == currentTextSize,
                        onClick = { onTextSizeSelected(preference) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun TextSizePreference.label(): String =
    when (this) {
        TextSizePreference.SMALL -> stringResource(Res.string.text_size_small)
        TextSizePreference.MEDIUM -> stringResource(Res.string.text_size_medium)
        TextSizePreference.LARGE -> stringResource(Res.string.text_size_large)
    }
