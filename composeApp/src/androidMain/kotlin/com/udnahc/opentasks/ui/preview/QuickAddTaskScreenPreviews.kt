package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.domain.usecase.task.QuickTaskParseResult
import com.udnahc.opentasks.domain.usecase.task.QuickTaskToken
import com.udnahc.opentasks.domain.usecase.task.QuickTaskTokenKind
import com.udnahc.opentasks.ui.screens.QuickAddTaskContent
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.viewmodel.QuickAddTaskUiState

@Composable
@Preview(name = "Quick Add Compact", widthDp = 360, heightDp = 640)
private fun QuickAddTaskCompactPreview() {
    QuickAddTaskPreview()
}

@Composable
@Preview(name = "Quick Add Expanded", widthDp = 900, heightDp = 640)
private fun QuickAddTaskExpandedPreview() {
    QuickAddTaskPreview()
}

@Composable
private fun QuickAddTaskPreview() {
    OpenTasksTheme {
        QuickAddTaskContent(
            state = QuickAddTaskUiState(
                input = "Water plants every Monday",
                parseResult = QuickTaskParseResult(
                    rawInput = "Water plants every Monday",
                    cleanedTitle = "Water plants",
                    deadline = 1L,
                    isAllDay = true,
                    recurrenceType = RecurrenceType.WEEKLY,
                    recognizedTokens = listOf(
                        QuickTaskToken(
                            kind = QuickTaskTokenKind.RECURRENCE,
                            sourceRange = 13..24,
                            sourceText = "every Monday",
                            signature = "RECURRENCE:every monday",
                            recurrenceType = RecurrenceType.WEEKLY,
                        )
                    ),
                ),
            ),
            onInputChanged = {},
            onDismissToken = {},
            onBack = {},
            onAdd = {},
        )
    }
}
