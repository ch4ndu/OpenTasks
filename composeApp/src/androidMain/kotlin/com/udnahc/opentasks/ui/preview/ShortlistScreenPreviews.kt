package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.data.model.TaskStatus
import com.udnahc.opentasks.domain.usecase.task.ShortlistSuggestion
import com.udnahc.opentasks.ui.screens.ChooseShortlistTasksContent
import com.udnahc.opentasks.ui.screens.ShortlistContent
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.viewmodel.ShortlistChooserState
import com.udnahc.opentasks.viewmodel.ShortlistRow
import com.udnahc.opentasks.viewmodel.ShortlistSuggestionGroup
import com.udnahc.opentasks.viewmodel.ShortlistUiState

private val previewRows = PreviewSampleData.sampleTasks
    .filter { it.status != TaskStatus.DONE }
    .mapIndexed { index, task ->
        ShortlistRow(task = task, metaText = "Work · Friday", isOverdue = index == 0)
    }

@Composable
@LightDarkPreview
private fun ShortlistScreenPreview() {
    OpenTasksTheme {
        ShortlistContent(
            state = ShortlistUiState(toTackle = previewRows, dueOutsideCount = 4),
            pendingTaskIds = emptySet(),
            onAllViewsClick = {},
            onChooseTasksClick = {},
            onViewTodayClick = {},
            onTaskClick = {},
            onToggleComplete = {},
            onRemove = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun ShortlistEmptyPreview() {
    OpenTasksTheme {
        ShortlistContent(
            state = ShortlistUiState(),
            pendingTaskIds = emptySet(),
            onAllViewsClick = {},
            onChooseTasksClick = {},
            onViewTodayClick = {},
            onTaskClick = {},
            onToggleComplete = {},
            onRemove = {},
        )
    }
}

@Composable
@LightDarkPreview
private fun ChooseShortlistTasksPreview() {
    OpenTasksTheme {
        ChooseShortlistTasksContent(
            state = ShortlistChooserState(
                suggestions = listOf(
                    ShortlistSuggestionGroup(ShortlistSuggestion.IMPORTANT_NOT_URGENT, previewRows.take(1)),
                    ShortlistSuggestionGroup(ShortlistSuggestion.DUE_TODAY, previewRows.drop(1)),
                ),
                allTasks = previewRows,
            ),
            pendingTaskIds = emptySet(),
            onAdd = {},
            onDone = {},
        )
    }
}
