package com.udnahc.opentasks.ui.preview

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.ui.screens.CreateTaskBottomBar
import com.udnahc.opentasks.ui.screens.CreateTaskTopBar
import com.udnahc.opentasks.ui.screens.DateReminderRow
import com.udnahc.opentasks.ui.screens.PriorityDropdown
import com.udnahc.opentasks.ui.screens.ReminderOption
import com.udnahc.opentasks.ui.screens.TaskDetailFields
import com.udnahc.opentasks.ui.theme.OpenTasksTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@LightDarkPreview
private fun CreateTaskTopBarPreview() {
    OpenTasksTheme {
        CreateTaskTopBar(
            listName = "Work",
            priority = TaskPriority.HIGH,
            showPriorityMenu = false,
            onShowPriorityMenu = {},
            onPrioritySelected = {},
            onBack = {},
            onListClick = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@LightDarkPreview
private fun CreateTaskTopBarMediumPriorityPreview() {
    OpenTasksTheme {
        CreateTaskTopBar(
            listName = "Personal",
            priority = TaskPriority.MEDIUM,
            showPriorityMenu = false,
            onShowPriorityMenu = {},
            onPrioritySelected = {},
            onBack = {},
            onListClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun PriorityDropdownExpandedPreview() {
    OpenTasksTheme {
        PriorityDropdown(
            expanded = true,
            currentPriority = TaskPriority.HIGH,
            onDismiss = {},
            onSelected = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun DateReminderRowNoDatePreview() {
    OpenTasksTheme {
        DateReminderRow(
            selectedDay = 0,
            selectedMonth = 0,
            selectedYear = 0,
            selectedHour = 8,
            selectedMinute = 0,
            selectedReminders = emptySet(),
            selectedRecurrence = RecurrenceType.NONE,
            isCompleted = false,
            onToggleComplete = {},
            onClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun DateReminderRowWithDatePreview() {
    OpenTasksTheme {
        DateReminderRow(
            selectedDay = 23,
            selectedMonth = 3,
            selectedYear = 2026,
            selectedHour = 14,
            selectedMinute = 30,
            selectedReminders = setOf(ReminderOption.ON_TIME),
            selectedRecurrence = RecurrenceType.WEEKLY,
            isCompleted = false,
            onToggleComplete = {},
            onClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun DateReminderRowCompletedPreview() {
    OpenTasksTheme {
        DateReminderRow(
            selectedDay = 0,
            selectedMonth = 0,
            selectedYear = 0,
            selectedHour = 8,
            selectedMinute = 0,
            selectedReminders = emptySet(),
            selectedRecurrence = RecurrenceType.NONE,
            isCompleted = true,
            onToggleComplete = {},
            onClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun TaskDetailFieldsCollapsedPreview() {
    OpenTasksTheme {
        TaskDetailFields(
            showDetails = false,
            onToggleDetails = {},
            section = "",
            onSectionChange = {},
            location = "",
            onLocationChange = {},
            onOpenInMaps = {},
            taskUrl = "",
            onUrlChange = {},
            organizer = "",
            onOrganizerChange = {},
            eventStatus = "",
            onStatusChange = {},
            attendees = "",
            onAttendeesChange = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun TaskDetailFieldsExpandedPreview() {
    OpenTasksTheme {
        TaskDetailFields(
            showDetails = true,
            onToggleDetails = {},
            section = "Design",
            onSectionChange = {},
            location = "123 Main St, Springfield",
            onLocationChange = {},
            onOpenInMaps = {},
            taskUrl = "https://example.com/task",
            onUrlChange = {},
            organizer = "Jane Doe",
            onOrganizerChange = {},
            eventStatus = "Confirmed",
            onStatusChange = {},
            attendees = "alice@example.com, bob@example.com",
            onAttendeesChange = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun CreateTaskBottomBarPreview() {
    OpenTasksTheme {
        CreateTaskBottomBar(
            isSubtaskMode = false,
            onToggleSubtaskMode = {},
            onDone = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun CreateTaskBottomBarSubtaskModePreview() {
    OpenTasksTheme {
        CreateTaskBottomBar(
            isSubtaskMode = true,
            onToggleSubtaskMode = {},
            onDone = {},
        )
    }
}
