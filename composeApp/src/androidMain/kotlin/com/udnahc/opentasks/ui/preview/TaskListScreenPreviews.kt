package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.ui.screens.CompletedTaskRow
import com.udnahc.opentasks.ui.screens.TaskListContent
import com.udnahc.opentasks.ui.screens.TaskListTopBar
import com.udnahc.opentasks.ui.screens.TaskRow
import com.udnahc.opentasks.ui.theme.OpenTasksTheme

@Composable
@LightDarkPreview
private fun TaskListScreenPreview() {
    OpenTasksTheme {
        TaskListContent(
            activeTasks = PreviewSampleData.sampleTasks.filter { !it.isCompleted },
            completedTasks = PreviewSampleData.sampleTasks.filter { it.isCompleted },
            onTaskClick = {},
            onToggleComplete = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun TaskListScreenEmptyPreview() {
    OpenTasksTheme {
        TaskListContent(
            onTaskClick = {},
            onToggleComplete = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun TaskRowPreview() {
    OpenTasksTheme {
        TaskRow(
            task = PreviewSampleData.sampleTasks.first(),
            onToggleComplete = {},
            onClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun CompletedTaskRowPreview() {
    OpenTasksTheme {
        CompletedTaskRow(
            task = PreviewSampleData.sampleTasks.first { it.isCompleted },
            onToggleComplete = {},
            onClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun TaskListTopBarPreview() {
    OpenTasksTheme {
        TaskListTopBar(
            listName = "Inbox",
            onListClick = {},
            onSettingsClick = {},
        )
    }
}
