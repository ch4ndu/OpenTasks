package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.model.TaskFormData
import com.udnahc.opentasks.data.model.TaskStatus

enum class FormCompletionScope { OCCURRENCE, SERIES }

sealed interface TaskWriteIntent {
    data class FormUpdate(val formData: TaskFormData) : TaskWriteIntent
    data class ApplyFormAndComplete(
        val formData: TaskFormData,
        val expectedOccurrence: Long,
        val scope: FormCompletionScope,
    ) : TaskWriteIntent
    data class SetStatus(val status: TaskStatus) : TaskWriteIntent
    data object ToggleStar : TaskWriteIntent
    data object ToggleCompletion : TaskWriteIntent
    data class CompleteOccurrence(val expectedOccurrence: Long) : TaskWriteIntent
    data class CompleteSeries(val expectedOccurrence: Long? = null) : TaskWriteIntent
    data class NotificationMarkDone(val expectedOccurrence: Long? = null) : TaskWriteIntent
    data object Delete : TaskWriteIntent
}
