package com.udnahc.opentasks.domain.usecase.task

import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskStatus

fun List<Task>.sortedByStatusAndDeadline(): List<Task> =
    sortedWith(
        compareBy<Task> { it.status == TaskStatus.DONE }
            .thenBy { it.deadline == null }
            .thenBy { it.deadline ?: Long.MAX_VALUE }
    )
