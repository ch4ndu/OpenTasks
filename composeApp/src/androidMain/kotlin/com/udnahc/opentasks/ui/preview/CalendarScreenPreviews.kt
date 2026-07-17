package com.udnahc.opentasks.ui.preview

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
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
            canNavigatePrevious = false,
            onNavigatePrevious = {},
            canNavigateNext = true,
            onNavigateNext = {},
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
@Preview(name = "Calendar List Top Bar Compact", widthDp = 320)
@Preview(
    name = "Calendar List Top Bar Compact Dark",
    widthDp = 320,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
private fun CalendarListTopBarCompactPreview() {
    OpenTasksTheme {
        CalendarTopBar(
            title = "September",
            currentView = CalendarViewType.LIST,
            showBackButton = false,
            onBack = {},
            canNavigatePrevious = true,
            onNavigatePrevious = {},
            canNavigateNext = true,
            onNavigateNext = {},
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
private fun CalendarYearTopBarPreview() {
    OpenTasksTheme {
        CalendarTopBar(
            title = "2026",
            currentView = CalendarViewType.YEAR,
            showBackButton = true,
            onBack = {},
            canNavigatePrevious = true,
            onNavigatePrevious = {},
            canNavigateNext = true,
            onNavigateNext = {},
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
