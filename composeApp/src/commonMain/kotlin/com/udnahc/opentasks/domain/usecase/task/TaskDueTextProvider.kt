package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import com.udnahc.opentasks.domain.time.DateTimeTextFormatter
import com.udnahc.opentasks.domain.time.EnglishDateTimeFormatter

interface TaskDueTextProvider {
    val formattingContextKey: String get() = "plain"
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
    override val formattingContextKey: String get() = dateTimeFormatter.formattingContextKey

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

/** Filter entity inputs only; callers also observe the formatter's context independently. */
fun Flow<List<Task>>.distinctDueTextInputs(): Flow<List<Task>> =
    distinctUntilChangedBy { tasks -> tasks.associate { it.id to it.deadline } }
