package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.data.calendar.CalendarPermissionStatus
import com.udnahc.opentasks.ui.screens.ImportCalendarDialogContent
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.viewmodel.ImportCalendarUiState

@Composable
@LightDarkPreview
private fun ImportCalendarDialogPreview() {
    OpenTasksTheme {
        ImportCalendarDialogContent(
            uiState = ImportCalendarUiState(
                permissionStatus = CalendarPermissionStatus.GRANTED,
            ),
            isAvailable = true,
            onRangeValueChange = {},
            onRangeUnitChange = {},
            onRequestPermission = {},
            onImport = {},
            onDismiss = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun ImportCalendarDialogSuccessPreview() {
    OpenTasksTheme {
        ImportCalendarDialogContent(
            uiState = ImportCalendarUiState(
                permissionStatus = CalendarPermissionStatus.GRANTED,
                importedCount = 12,
            ),
            isAvailable = true,
            onRangeValueChange = {},
            onRangeUnitChange = {},
            onRequestPermission = {},
            onImport = {},
            onDismiss = {},
        )
    }
}
