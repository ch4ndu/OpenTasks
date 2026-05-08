package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.data.model.TaskCategory
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.ui.screens.DetailTaskRow
import com.udnahc.opentasks.ui.screens.QuadrantDetailContent
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.viewmodel.MatrixViewModel

@Composable
@LightDarkPreview
private fun QuadrantDetailPreview() {
    OpenTasksTheme {
        QuadrantDetailContent(
            title = "Urgent & Important",
            priority = TaskPriority.HIGH,
            defaultCategoryName = "Inbox",
            categorizedTasks = listOf(
                MatrixViewModel.TaskCategoryGroup(
                    TaskCategory.TODAY,
                    PreviewSampleData.sampleTasks.filter { it.priority == TaskPriority.HIGH },
                )
            ),
            onBack = {},
            onTaskClick = {},
            onToggleComplete = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun DetailTaskRowPreview() {
    OpenTasksTheme {
        DetailTaskRow(
            task = PreviewSampleData.sampleTasks.first(),
            priority = TaskPriority.HIGH,
            isOverdue = false,
            categoryName = "",
            onToggleComplete = {},
            onClick = {},
        )
    }
}
