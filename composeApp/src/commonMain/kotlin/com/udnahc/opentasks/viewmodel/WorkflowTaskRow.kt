package com.udnahc.opentasks.viewmodel

import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.domain.usecase.task.TaskDueTextProvider

data class WorkflowTaskRow(val task: Task, val detail: String)

internal suspend fun workflowRows(tasks: List<Task>, categories: List<Category>, due: TaskDueTextProvider): List<WorkflowTaskRow> {
    val names = categories.associate { it.id to it.name }
    return tasks.map { task -> WorkflowTaskRow(task, listOfNotNull(names[task.categoryId], due.listDueText(task).takeIf { it.isNotBlank() }).joinToString(" · ")) }
}
