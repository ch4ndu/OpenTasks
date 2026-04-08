package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.data.extensions.MILLIS_PER_DAY
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.ui.screens.DetailTaskRow
import com.udnahc.opentasks.ui.screens.QuadrantDetailContent
import com.udnahc.opentasks.ui.theme.OpenTasksTheme

@Composable
@LightDarkPreview
private fun QuadrantDetailPreview() {
    OpenTasksTheme {
        QuadrantDetailContent(
            title = "Urgent & Important",
            priority = TaskPriority.HIGH,
            tasks = PreviewSampleData.sampleTasks.filter { it.priority == TaskPriority.HIGH },
            onBack = {},
            onTaskClick = {},
            onToggleComplete = {},
            now = PreviewSampleData.sampleTodayMillis,
            startOfToday = PreviewSampleData.sampleTodayMillis,
            startOfTomorrow = PreviewSampleData.sampleTodayMillis + MILLIS_PER_DAY,
            endOfNext7Days = PreviewSampleData.sampleTodayMillis + 7 * MILLIS_PER_DAY,
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
            onToggleComplete = {},
            onClick = {},
        )
    }
}
