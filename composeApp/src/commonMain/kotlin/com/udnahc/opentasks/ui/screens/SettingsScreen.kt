package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.udnahc.opentasks.data.auth.AuthenticatedAccount
import com.udnahc.opentasks.data.model.TextSizePreference
import com.udnahc.opentasks.data.model.ThemeMode
import com.udnahc.opentasks.data.notification.ExactReminderPermissionStatus
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.util.rememberCalendarPermissionLauncher
import com.udnahc.opentasks.ui.util.rememberFileExportLauncher
import com.udnahc.opentasks.ui.util.rememberNotificationPermissionLauncher
import com.udnahc.opentasks.viewmodel.ClearLocalDataStatus
import com.udnahc.opentasks.viewmodel.AccountOperation
import com.udnahc.opentasks.viewmodel.AccountUiError
import com.udnahc.opentasks.viewmodel.ExportResult
import com.udnahc.opentasks.viewmodel.SettingsViewModel
import com.udnahc.opentasks.viewmodel.SyncStatus
import com.udnahc.opentasks.viewmodel.displayLabel
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.account
import opentasks.composeapp.generated.resources.account_current
import opentasks.composeapp.generated.resources.account_email
import opentasks.composeapp.generated.resources.account_endpoint
import opentasks.composeapp.generated.resources.account_endpoint_read_only_value
import opentasks.composeapp.generated.resources.account_endpoint_read_only
import opentasks.composeapp.generated.resources.account_password
import opentasks.composeapp.generated.resources.account_switch
import opentasks.composeapp.generated.resources.account_switch_description
import opentasks.composeapp.generated.resources.appearance
import opentasks.composeapp.generated.resources.back
import opentasks.composeapp.generated.resources.calendar_access
import opentasks.composeapp.generated.resources.cancel
import opentasks.composeapp.generated.resources.checking_connection
import opentasks.composeapp.generated.resources.clear
import opentasks.composeapp.generated.resources.clear_local_data_error
import opentasks.composeapp.generated.resources.configured
import opentasks.composeapp.generated.resources.connected
import opentasks.composeapp.generated.resources.connection_failed
import opentasks.composeapp.generated.resources.sync_failed
import opentasks.composeapp.generated.resources.exact_reminder_available
import opentasks.composeapp.generated.resources.exact_reminder_not_required
import opentasks.composeapp.generated.resources.exact_reminder_permission_needed
import opentasks.composeapp.generated.resources.exact_reminder_timing
import opentasks.composeapp.generated.resources.export_csv
import opentasks.composeapp.generated.resources.export_error
import opentasks.composeapp.generated.resources.export_header
import opentasks.composeapp.generated.resources.export_ics
import opentasks.composeapp.generated.resources.export_success
import opentasks.composeapp.generated.resources.import_csv_ticktick
import opentasks.composeapp.generated.resources.import_from_calendar
import opentasks.composeapp.generated.resources.import_from_ics
import opentasks.composeapp.generated.resources.import_header
import opentasks.composeapp.generated.resources.logout
import opentasks.composeapp.generated.resources.logout_confirm_message
import opentasks.composeapp.generated.resources.logout_confirm_title
import opentasks.composeapp.generated.resources.logout_description
import opentasks.composeapp.generated.resources.loading
import opentasks.composeapp.generated.resources.not_configured
import opentasks.composeapp.generated.resources.notifications
import opentasks.composeapp.generated.resources.permission_granted
import opentasks.composeapp.generated.resources.permission_not_granted
import opentasks.composeapp.generated.resources.permissions
import opentasks.composeapp.generated.resources.pocketbase_url
import opentasks.composeapp.generated.resources.pocketbase_url_hint
import opentasks.composeapp.generated.resources.save
import opentasks.composeapp.generated.resources.settings
import opentasks.composeapp.generated.resources.sync
import opentasks.composeapp.generated.resources.syncing
import opentasks.composeapp.generated.resources.text_size
import opentasks.composeapp.generated.resources.text_size_large
import opentasks.composeapp.generated.resources.text_size_medium
import opentasks.composeapp.generated.resources.text_size_small
import opentasks.composeapp.generated.resources.theme
import opentasks.composeapp.generated.resources.theme_dark
import opentasks.composeapp.generated.resources.theme_light
import opentasks.composeapp.generated.resources.theme_system
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onImportCalendar: () -> Unit = {},
    onImportIcs: () -> Unit = {},
    onImportCsv: () -> Unit = {},
    currentAccount: AuthenticatedAccount? = null,
    currentEndpoint: String? = null,
    accountOperation: AccountOperation? = null,
    accountError: AccountUiError? = null,
    onSwitchAccount: (email: String, password: String) -> Unit = { _, _ -> },
    onClearAccountError: () -> Unit = {},
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
    val exportInProgress by viewModel.exportInProgress.collectAsState()
    val clearLocalDataStatus by viewModel.clearLocalDataStatus.collectAsState()

    val requestNotification = rememberNotificationPermissionLauncher { granted ->
        viewModel.recheckPermissions()
    }
    val requestCalendar = rememberCalendarPermissionLauncher { granted ->
        viewModel.onCalendarPermissionResult(granted)
    }
    val exportFile = rememberFileExportLauncher { result ->
        viewModel.onExportResult(result)
    }

    LifecycleResumeEffect(Unit) {
        viewModel.recheckPermissions()
        onPauseOrDispose { }
    }

    SettingsContent(
        currentUrl = currentEndpoint ?: currentUrl,
        syncStatus = syncStatus,
        themePreference = themePreference,
        textSizePreference = textSizePreference,
        notificationGranted = notificationGranted,
        exactReminderStatus = exactReminderStatus,
        calendarGranted = calendarGranted,
        exportResult = exportResult,
        exportInProgress = exportInProgress,
        clearLocalDataStatus = clearLocalDataStatus,
        currentAccount = currentAccount,
        accountOperation = accountOperation,
        accountError = accountError,
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
        onExportCsv = {
            viewModel.prepareCsvExport(exportFile)
        },
        onExportIcs = {
            viewModel.prepareIcsExport(exportFile)
        },
        onClearExportResult = { viewModel.clearExportResult() },
        onClearLocalDataErrorShown = { viewModel.clearLocalDataErrorShown() },
        onSwitchAccount = onSwitchAccount,
        onClearAccountError = onClearAccountError,
        onLogout = onLogout,
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
    exportInProgress: Boolean = false,
    clearLocalDataStatus: ClearLocalDataStatus = ClearLocalDataStatus.IDLE,
    currentAccount: AuthenticatedAccount? = null,
    accountOperation: AccountOperation? = null,
    accountError: AccountUiError? = null,
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
    onClearLocalDataErrorShown: () -> Unit = {},
    onSwitchAccount: (email: String, password: String) -> Unit = { _, _ -> },
    onClearAccountError: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val dimens = OpenTasksTheme.dimens
    var showUrlDialog by remember { mutableStateOf(false) }
    var showSwitchAccount by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showTextSizeDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val accountControls = accountControlAvailability(currentAccount, accountOperation)

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

            ExportResult.Idle -> { /* no-op */
            }
        }
    }

    LaunchedEffect(clearLocalDataStatus) {
        if (clearLocalDataStatus == ClearLocalDataStatus.ERROR) {
            snackbarHostState.showSnackbar(getString(Res.string.clear_local_data_error))
            onClearLocalDataErrorShown()
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
                    ExactReminderPermissionStatus.GRANTED -> stringResource(Res.string.exact_reminder_available)
                    ExactReminderPermissionStatus.NOT_GRANTED -> stringResource(Res.string.exact_reminder_permission_needed)
                    ExactReminderPermissionStatus.NOT_REQUIRED -> stringResource(Res.string.exact_reminder_not_required)
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
                val summary = if (currentAccount != null) {
                    stringResource(
                        Res.string.account_endpoint_read_only_value,
                        currentUrl ?: stringResource(Res.string.not_configured),
                    )
                } else {
                    currentUrl ?: stringResource(Res.string.not_configured)
                }
                SettingsRow(
                    title = stringResource(Res.string.pocketbase_url),
                    summary = summary,
                    onClick = { if (currentAccount == null) showUrlDialog = true },
                    enabled = currentAccount == null,
                )
            }
            if (currentUrl != null) {
                item(key = "sync_now") {
                    val summary = when (syncStatus) {
                        SyncStatus.SYNCING -> stringResource(Res.string.syncing)
                        SyncStatus.CHECKING -> stringResource(Res.string.checking_connection)
                        SyncStatus.ERROR -> stringResource(Res.string.connection_failed)
                        SyncStatus.SYNC_ERROR -> stringResource(Res.string.sync_failed)
                        SyncStatus.SUCCESS -> stringResource(Res.string.connected)
                        SyncStatus.IDLE -> stringResource(Res.string.configured)
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
                    enabled = !exportInProgress,
                )
            }
            item(key = "export_ics") {
                SettingsRow(
                    title = stringResource(Res.string.export_ics),
                    onClick = onExportIcs,
                    enabled = !exportInProgress,
                )
            }

            // ── Account ──
            item(key = "account_header") {
                SettingsCategoryHeader(stringResource(Res.string.account))
            }
            if (currentAccount != null) {
                item(key = "current_account") {
                    SettingsRow(
                        title = stringResource(Res.string.account_current),
                        summary = currentAccount.displayLabel(),
                        onClick = {},
                        enabled = false,
                    )
                }
                item(key = "switch_account") {
                    SettingsRow(
                        title = stringResource(Res.string.account_switch),
                        summary = stringResource(Res.string.account_switch_description),
                        onClick = { showSwitchAccount = true },
                        enabled = accountControls.canSwitchAccount,
                    )
                }
                if (accountError != null) {
                    item(key = "account_error") {
                        AccountErrorMessage(
                            error = accountError,
                            onClear = onClearAccountError,
                        )
                    }
                }
            }
            item(key = "logout") {
                SettingsRow(
                    title = stringResource(Res.string.logout),
                    summary = stringResource(Res.string.logout_description),
                    onClick = { showLogoutConfirm = true },
                    enabled = accountControls.canLogout,
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

    if (showSwitchAccount) {
        AccountSwitchDialog(
            endpoint = currentUrl,
            isBusy = accountOperation == AccountOperation.SWITCHING,
            onSubmit = { email, password ->
                showSwitchAccount = false
                onSwitchAccount(email, password)
            },
            onDismiss = { showSwitchAccount = false },
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
    enabled: Boolean = true,
) {
    val dimens = OpenTasksTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = dimens.paddingXLarge, vertical = dimens.paddingLarge),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.38f),
        )
        if (summary != null) {
            Spacer(Modifier.height(dimens.spacerTiny))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
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
private fun AccountSwitchDialog(
    endpoint: String?,
    isBusy: Boolean,
    onSubmit: (email: String, password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var emailInput by rememberSaveable(endpoint) { mutableStateOf("") }
    // Password deliberately remains ephemeral and is cleared before the action starts.
    var passwordInput by remember { mutableStateOf("") }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    fun submit() {
        val result = submitAccountSession(
            mode = AccountSessionEntryMode.REAUTHENTICATE,
            endpointInput = endpoint.orEmpty(),
            emailInput = emailInput,
            passwordInput = passwordInput,
            isBusy = isBusy,
        )
        passwordInput = result.passwordInput
        result.submission?.let { submission ->
            onSubmit(submission.email, submission.password)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.account_switch)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(OpenTasksTheme.dimens.spacerLarge)) {
                Text(stringResource(Res.string.account_switch_description))
                if (!endpoint.isNullOrBlank()) {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = {},
                        label = { Text(stringResource(Res.string.account_endpoint)) },
                        supportingText = {
                            Text(stringResource(Res.string.account_endpoint_read_only))
                        },
                        enabled = false,
                        singleLine = true,
                        colors = accountTextFieldColors(),
                    )
                }
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text(stringResource(Res.string.account_email)) },
                    enabled = !isBusy,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    colors = accountTextFieldColors(),
                )
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text(stringResource(Res.string.account_password)) },
                    enabled = !isBusy,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            submit()
                        },
                    ),
                    colors = accountTextFieldColors(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = ::submit,
                enabled = canSubmitAccountSession(
                    mode = AccountSessionEntryMode.REAUTHENTICATE,
                    endpointInput = endpoint.orEmpty(),
                    emailInput = emailInput,
                    passwordInput = passwordInput,
                    isBusy = isBusy,
                ),
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(OpenTasksTheme.dimens.iconDefault),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = OpenTasksTheme.dimens.dividerThick,
                    )
                    Spacer(Modifier.width(OpenTasksTheme.dimens.spacerLarge))
                }
                Text(
                    if (isBusy) stringResource(Res.string.loading)
                    else stringResource(Res.string.account_switch)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(stringResource(Res.string.cancel))
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
