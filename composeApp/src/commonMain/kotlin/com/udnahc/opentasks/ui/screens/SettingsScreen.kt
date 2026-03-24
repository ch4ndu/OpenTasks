package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.viewmodel.SettingsViewModel
import com.udnahc.opentasks.viewmodel.SyncStatus
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.back
import opentasks.composeapp.generated.resources.clear
import opentasks.composeapp.generated.resources.connected
import opentasks.composeapp.generated.resources.ic_arrow_back
import opentasks.composeapp.generated.resources.not_configured
import opentasks.composeapp.generated.resources.pocketbase_url
import opentasks.composeapp.generated.resources.pocketbase_url_hint
import opentasks.composeapp.generated.resources.save
import opentasks.composeapp.generated.resources.settings
import opentasks.composeapp.generated.resources.sync
import opentasks.composeapp.generated.resources.sync_error
import opentasks.composeapp.generated.resources.syncing

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val currentUrl by viewModel.pocketBaseUrl.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    SettingsContent(
        currentUrl = currentUrl,
        syncStatus = syncStatus,
        onBack = onBack,
        onSaveUrl = { viewModel.savePocketBaseUrl(it) },
        onClearUrl = { viewModel.clearPocketBaseUrl() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    currentUrl: String?,
    syncStatus: SyncStatus,
    onBack: () -> Unit,
    onSaveUrl: (String) -> Unit,
    onClearUrl: () -> Unit,
) {
    var urlInput by rememberSaveable(currentUrl) { mutableStateOf(currentUrl ?: "") }

    val dimens = OpenTasksTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // Top bar
        TopAppBar(
            title = { Text(stringResource(Res.string.settings)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_arrow_back),
                        contentDescription = stringResource(Res.string.back),
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.paddingXLarge),
        ) {
            // Sync section header
            Spacer(Modifier.height(dimens.spacerXLarge))
            Text(
                text = stringResource(Res.string.sync),
                style = MaterialTheme.typography.titleMedium,
                color = PrimaryBlue,
            )
            Spacer(Modifier.height(dimens.spacerLarge))

            // PocketBase URL field
            Text(
                text = stringResource(Res.string.pocketbase_url),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(dimens.spacerSmall))
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                placeholder = { Text(stringResource(Res.string.pocketbase_url_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(dimens.spacerLarge))

            // Status + Save row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Status text
                val statusText = when {
                    syncStatus == SyncStatus.SYNCING -> stringResource(Res.string.syncing)
                    syncStatus == SyncStatus.ERROR -> stringResource(Res.string.sync_error)
                    currentUrl != null -> stringResource(Res.string.connected)
                    else -> stringResource(Res.string.not_configured)
                }
                val statusColor = when {
                    syncStatus == SyncStatus.SYNCING -> MaterialTheme.colorScheme.onSurfaceVariant
                    syncStatus == SyncStatus.ERROR -> MaterialTheme.colorScheme.error
                    currentUrl != null -> PrimaryBlue
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacerSmall)) {
                    if (currentUrl != null) {
                        OutlinedButton(onClick = {
                            urlInput = ""
                            onClearUrl()
                        }) {
                            Text(stringResource(Res.string.clear))
                        }
                    }
                    Button(
                        onClick = { onSaveUrl(urlInput) },
                        enabled = urlInput.isNotBlank(),
                    ) {
                        Text(stringResource(Res.string.save))
                    }
                }
            }
        }
    }
}

@Composable
@Preview
private fun SettingsContentPreview() {
    OpenTasksTheme {
        SettingsContent(
            currentUrl = null,
            syncStatus = SyncStatus.IDLE,
            onBack = {},
            onSaveUrl = {},
            onClearUrl = {},
        )
    }
}

@Composable
@Preview
private fun SettingsContentConnectedPreview() {
    OpenTasksTheme {
        SettingsContent(
            currentUrl = "http://192.168.1.100:8090",
            syncStatus = SyncStatus.SUCCESS,
            onBack = {},
            onSaveUrl = {},
            onClearUrl = {},
        )
    }
}
