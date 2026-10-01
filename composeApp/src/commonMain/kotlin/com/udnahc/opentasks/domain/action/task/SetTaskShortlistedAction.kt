package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.data.repository.TaskRepository

class SetTaskShortlistedAction(
    private val repository: TaskRepository,
    internal val accountBoundaryExecutor: AccountBoundaryExecutor? = null,
) {
    private val coordinator = TaskWriteCoordinator(repository)

    suspend operator fun invoke(taskId: String, shortlisted: Boolean): TaskWriteResult =
        accountBoundaryExecutor.withForegroundActionBoundary {
            coordinator.write(taskId, TaskWriteIntent.SetShortlisted(shortlisted)).value
        }
}
