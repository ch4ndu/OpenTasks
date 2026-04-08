package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.ui.screens.calendar.CalendarTopBar
import com.udnahc.opentasks.ui.screens.calendar.CalendarViewType
import com.udnahc.opentasks.ui.screens.calendar.ListDisplayMode
import com.udnahc.opentasks.ui.screens.calendar.ViewPickerDropdown
import com.udnahc.opentasks.ui.theme.OpenTasksTheme

@Composable
@LightDarkPreview
private fun CalendarTopBarPreview() {
    OpenTasksTheme {
        CalendarTopBar(
            title = "March",
            currentView = CalendarViewType.MONTH,
            showBackButton = false,
            onBack = {},
            listDisplayMode = ListDisplayMode.TIMELINE,
            onToggleDisplayMode = {},
            showViewPicker = false,
            onViewPickerToggle = {},
            onViewSelected = {},
            onViewPickerDismiss = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun ViewPickerDropdownPreview() {
    OpenTasksTheme {
        ViewPickerDropdown(
            expanded = true,
            currentView = CalendarViewType.MONTH,
            onViewSelected = {},
            onDismiss = {},
        )
    }
}
