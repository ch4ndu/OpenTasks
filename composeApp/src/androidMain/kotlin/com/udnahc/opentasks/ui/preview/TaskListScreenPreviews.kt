package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.TaskListFilter
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.ui.screens.CompletedTaskRow
import com.udnahc.opentasks.ui.screens.TaskListContent
import com.udnahc.opentasks.ui.screens.TaskListTopBar
import com.udnahc.opentasks.ui.screens.TaskOverviewContent
import com.udnahc.opentasks.ui.screens.TaskRow
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.viewmodel.TaskListViewModel.ActiveTaskListSection
import com.udnahc.opentasks.viewmodel.ListCount
import com.udnahc.opentasks.viewmodel.SmartViewCount
import com.udnahc.opentasks.viewmodel.TaskListViewModel.SectionGroup
import com.udnahc.opentasks.viewmodel.TaskOverviewState

@Composable
@LightDarkPreview
private fun TaskListScreenPreview() {
    OpenTasksTheme {
        val activeTasks = PreviewSampleData.sampleTasks.filter { it.status != TaskStatus.DONE }
        TaskListContent(
            listName = "Inbox",
            completedTasks = PreviewSampleData.sampleTasks.filter { it.status == TaskStatus.DONE },
            groupedActiveTasks = listOf(SectionGroup(ActiveTaskListSection.UPCOMING, activeTasks)),
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
            listName = "Inbox",
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
            task = PreviewSampleData.sampleTasks.first { it.status == TaskStatus.DONE },
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
            onAllViewsClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun TaskOverviewPreview() {
    OpenTasksTheme {
        TaskOverviewContent(
            state = TaskOverviewState(
                smartViews = listOf(
                    SmartViewCount(TaskListFilter.Today, 4),
                    SmartViewCount(TaskListFilter.Overdue, 1),
                    SmartViewCount(TaskListFilter.Starred, 3),
                    SmartViewCount(TaskListFilter.NoDate, 8),
                    SmartViewCount(TaskListFilter.HighPriority, 2),
                    SmartViewCount(TaskListFilter.DueThisWeek, 9),
                ),
                lists = listOf(
                    ListCount(Category(id = "inbox", name = "Inbox", icon = "inbox"), 6),
                    ListCount(Category(id = "work", name = "Work"), 12),
                ),
            ),
            onViewClick = {},
            onNewListClick = {},
            onSettingsClick = {},
        )
    }
}
