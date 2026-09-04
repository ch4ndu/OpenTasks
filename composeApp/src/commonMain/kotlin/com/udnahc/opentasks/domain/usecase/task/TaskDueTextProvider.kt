package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.domain.time.DateTimeTextFormatter
import com.udnahc.opentasks.domain.time.EnglishDateTimeFormatter

interface TaskDueTextProvider {
    suspend fun listDueText(task: Task): String
    suspend fun matrixDueText(task: Task): String
}

/** Resource-free formatter for focused domain and ViewModel tests. */
object PlainTaskDueTextProvider : TaskDueTextProvider {
    override suspend fun listDueText(task: Task): String =
        task.formatListDueText(EnglishDateTimeFormatter)

    override suspend fun matrixDueText(task: Task): String =
        task.formatMatrixDueText(EnglishDateTimeFormatter)
}

class LocalizedTaskDueTextProvider(
    private val dateTimeFormatter: DateTimeTextFormatter = EnglishDateTimeFormatter,
) : TaskDueTextProvider {
    override suspend fun listDueText(task: Task): String = task.formatListDueText(dateTimeFormatter)

    override suspend fun matrixDueText(task: Task): String = task.formatMatrixDueText(dateTimeFormatter)
}

private fun Task.formatListDueText(formatter: DateTimeTextFormatter): String {
    val deadline = deadline ?: return ""
    return "${formatter.formatShortDate(deadline)}, ${formatter.formatTime(deadline)}"
}

private fun Task.formatMatrixDueText(formatter: DateTimeTextFormatter): String {
    val deadline = deadline ?: return ""
    return formatter.formatShortDate(deadline)
}
