package com.udnahc.opentasks.ui.preview

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.ui.screens.EisenhowerMatrixContent
import com.udnahc.opentasks.ui.screens.MatrixHeader
import com.udnahc.opentasks.ui.screens.QuadrantCard
import com.udnahc.opentasks.ui.screens.QuadrantTaskRow
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PriorityHigh

@Composable
@LightDarkPreview
private fun EisenhowerMatrixScreenPreview() {
    OpenTasksTheme {
        EisenhowerMatrixContent(
            tasksByPriority = PreviewSampleData.sampleTasks.groupBy { it.priority },
            onTaskClick = {},
            onToggleComplete = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun QuadrantCardPreview() {
    OpenTasksTheme {
        QuadrantCard(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            title = "Urgent & Important",
            badge = "I",
            color = PriorityHigh,
            tasks = PreviewSampleData.sampleTasks.filter { it.priority == TaskPriority.HIGH },
            onTaskClick = {},
            onToggleComplete = {},
            onCardClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun QuadrantTaskRowPreview() {
    OpenTasksTheme {
        QuadrantTaskRow(
            task = PreviewSampleData.sampleTasks.first(),
            color = PriorityHigh,
            onToggleComplete = {},
            onClick = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun MatrixHeaderPreview() {
    OpenTasksTheme {
        MatrixHeader(onSettingsClick = {})
    }
}
