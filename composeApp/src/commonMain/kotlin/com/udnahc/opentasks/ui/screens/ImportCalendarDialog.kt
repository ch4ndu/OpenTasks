package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.udnahc.opentasks.data.calendar.CalendarPermissionStatus
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import com.udnahc.opentasks.ui.util.rememberCalendarPermissionLauncher
import com.udnahc.opentasks.viewmodel.ImportCalendarUiState
import com.udnahc.opentasks.viewmodel.ImportCalendarViewModel
import com.udnahc.opentasks.viewmodel.ImportRangeUnit
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.calendar_not_available
import opentasks.composeapp.generated.resources.calendar_permission_denied
import opentasks.composeapp.generated.resources.cancel
import opentasks.composeapp.generated.resources.days
import opentasks.composeapp.generated.resources.done
import opentasks.composeapp.generated.resources.grant_calendar_permission
import opentasks.composeapp.generated.resources.import_button
import opentasks.composeapp.generated.resources.import_error
import opentasks.composeapp.generated.resources.import_from_calendar
import opentasks.composeapp.generated.resources.import_range_label
import opentasks.composeapp.generated.resources.import_success
import opentasks.composeapp.generated.resources.importing
import opentasks.composeapp.generated.resources.months
import opentasks.composeapp.generated.resources.weeks
import opentasks.composeapp.generated.resources.years
import org.jetbrains.compose.resources.stringResource

@Composable
fun ImportCalendarDialog(
    viewModel: ImportCalendarViewModel,
    onDismiss: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val requestPermission = rememberCalendarPermissionLauncher { granted ->
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(Unit) { viewModel.checkPermission() }

    ImportCalendarDialogContent(
        uiState = uiState,
        isAvailable = viewModel.isAvailable,
        onRangeValueChange = { viewModel.updateRangeValue(it) },
        onRangeUnitChange = { viewModel.updateRangeUnit(it) },
        onRequestPermission = requestPermission,
        onImport = { viewModel.importEvents() },
        onDismiss = {
            viewModel.resetState()
            onDismiss()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportCalendarDialogContent(
    uiState: ImportCalendarUiState,
    isAvailable: Boolean,
    onRangeValueChange: (Int) -> Unit,
    onRangeUnitChange: (ImportRangeUnit) -> Unit,
    onRequestPermission: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = OpenTasksTheme.dimens

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.import_from_calendar),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    !isAvailable -> {
                        Text(
                            text = stringResource(Res.string.calendar_not_available),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    uiState.permissionStatus == CalendarPermissionStatus.DENIED -> {
                        Text(
                            text = stringResource(Res.string.calendar_permission_denied),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    uiState.permissionStatus != CalendarPermissionStatus.GRANTED -> {
                        Text(
                            text = stringResource(Res.string.grant_calendar_permission),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    uiState.isLoading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.width(dimens.spacerXLarge))
                            Text(stringResource(Res.string.importing))
                        }
                    }
                    uiState.importedCount != null -> {
                        Text(
                            text = stringResource(Res.string.import_success, uiState.importedCount),
                            color = PrimaryBlue,
                        )
                    }
                    uiState.error != null -> {
                        Text(
                            text = stringResource(Res.string.import_error, uiState.error),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    else -> {
                        // Range picker
                        Text(
                            text = stringResource(Res.string.import_range_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(dimens.spacerLarge))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = uiState.rangeValue.toString(),
                                onValueChange = { text ->
                                    text.toIntOrNull()?.let { onRangeValueChange(it) }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(dimens.spacerLarge))
                            RangeUnitDropdown(
                                selectedUnit = uiState.rangeUnit,
                                onUnitSelected = onRangeUnitChange,
                                modifier = Modifier.weight(2f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                !isAvailable -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.done), color = PrimaryBlue)
                    }
                }
                uiState.importedCount != null -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.done), color = PrimaryBlue)
                    }
                }
                uiState.permissionStatus != CalendarPermissionStatus.GRANTED -> {
                    TextButton(onClick = onRequestPermission) {
                        Text(stringResource(Res.string.grant_calendar_permission), color = PrimaryBlue)
                    }
                }
                uiState.isLoading -> { /* No button while loading */ }
                else -> {
                    TextButton(onClick = onImport) {
                        Text(stringResource(Res.string.import_button), color = PrimaryBlue)
                    }
                }
            }
        },
        dismissButton = {
            if (!uiState.isLoading) {
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(Res.string.cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeUnitDropdown(
    selectedUnit: ImportRangeUnit,
    onUnitSelected: (ImportRangeUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    val unitLabels = mapOf(
        ImportRangeUnit.DAYS to stringResource(Res.string.days),
        ImportRangeUnit.WEEKS to stringResource(Res.string.weeks),
        ImportRangeUnit.MONTHS to stringResource(Res.string.months),
        ImportRangeUnit.YEARS to stringResource(Res.string.years),
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = unitLabels[selectedUnit] ?: "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ImportRangeUnit.entries.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unitLabels[unit] ?: unit.name) },
                    onClick = {
                        onUnitSelected(unit)
                        expanded = false
                    },
                )
            }
        }
    }
}

