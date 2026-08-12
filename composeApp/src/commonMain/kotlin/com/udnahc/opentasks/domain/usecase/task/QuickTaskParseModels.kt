package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.AppConstants
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.TaskPriority
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class QuickTaskCreationContext(
    val categoryId: String = AppConstants.DEFAULT_INBOX_ID,
    val priority: TaskPriority = TaskPriority.NONE,
    val fallbackDate: LocalDate? = null,
)

enum class QuickTaskTokenKind {
    DATE,
    TIME,
    RECURRENCE,
}

data class QuickTaskToken(
    val kind: QuickTaskTokenKind,
    val sourceRange: IntRange,
    val sourceText: String,
    val signature: String,
    val resolvedDate: LocalDate? = null,
    val resolvedTime: LocalTime? = null,
    val recurrenceType: RecurrenceType = RecurrenceType.NONE,
    val isActive: Boolean = true,
)

data class QuickTaskParseResult(
    val rawInput: String,
    val cleanedTitle: String,
    val deadline: Long?,
    val isAllDay: Boolean,
    val recurrenceType: RecurrenceType,
    val recognizedTokens: List<QuickTaskToken>,
)
