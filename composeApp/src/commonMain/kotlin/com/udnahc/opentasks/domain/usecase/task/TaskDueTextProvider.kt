package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.extensions.formatDateShort
import com.udnahc.opentasks.data.extensions.formatTimeFromLocalMillis
import com.udnahc.opentasks.data.extensions.localMillisToLocalDate
import com.udnahc.opentasks.data.model.Task
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.apr
import opentasks.composeapp.generated.resources.aug
import opentasks.composeapp.generated.resources.dec
import opentasks.composeapp.generated.resources.feb
import opentasks.composeapp.generated.resources.jan
import opentasks.composeapp.generated.resources.jul
import opentasks.composeapp.generated.resources.jun
import opentasks.composeapp.generated.resources.mar
import opentasks.composeapp.generated.resources.may_short
import opentasks.composeapp.generated.resources.nov
import opentasks.composeapp.generated.resources.oct
import opentasks.composeapp.generated.resources.sep
import org.jetbrains.compose.resources.getString

interface TaskDueTextProvider {
    suspend fun listDueText(task: Task): String
    suspend fun matrixDueText(task: Task): String
}

/** Resource-free formatter for focused domain and ViewModel tests. */
object PlainTaskDueTextProvider : TaskDueTextProvider {
    override suspend fun listDueText(task: Task): String = task.formatListDueText(::formatDateShort)

    override suspend fun matrixDueText(task: Task): String = task.formatMatrixDueText(::formatDateShort)
}

class LocalizedTaskDueTextProvider : TaskDueTextProvider {
    override suspend fun listDueText(task: Task): String = task.formatListDueText(::localizedDateText)

    override suspend fun matrixDueText(task: Task): String = task.formatMatrixDueText(::localizedDateText)

    private suspend fun shortMonthName(month: Int): String = getString(
        when (month) {
            1 -> Res.string.jan
            2 -> Res.string.feb
            3 -> Res.string.mar
            4 -> Res.string.apr
            5 -> Res.string.may_short
            6 -> Res.string.jun
            7 -> Res.string.jul
            8 -> Res.string.aug
            9 -> Res.string.sep
            10 -> Res.string.oct
            11 -> Res.string.nov
            12 -> Res.string.dec
            else -> error("Invalid month: $month")
        },
    )

    private suspend fun localizedDateText(deadline: Long): String {
        val date = localMillisToLocalDate(deadline)
        return "${shortMonthName(date.monthNumber)} ${date.dayOfMonth}"
    }
}

private suspend fun Task.formatListDueText(dateText: suspend (Long) -> String): String {
    val deadline = deadline ?: return ""
    return "${dateText(deadline)}, ${formatTimeFromLocalMillis(deadline)}"
}

private suspend fun Task.formatMatrixDueText(dateText: suspend (Long) -> String): String {
    val deadline = deadline ?: return ""
    return dateText(deadline)
}
