package com.udnahc.opentasks.domain.action.task

import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.repository.TaskRepository
import com.udnahc.opentasks.data.repository.CategoryRepository
import kotlinx.coroutines.CancellationException

sealed interface BulkTaskChange {
    data class Move(val categoryId: String) : BulkTaskChange
    data class Priority(val priority: TaskPriority) : BulkTaskChange
    data class Shortlist(val selected: Boolean) : BulkTaskChange
}

data class BulkTaskResult(val succeeded: Set<String>, val failed: Set<String>)

class BulkUpdateTasksAction(
    repository: TaskRepository,
    private val categories: CategoryRepository,
    private val boundary: AccountBoundaryExecutor? = null,
) {
    private val coordinator = TaskWriteCoordinator(repository)

    suspend operator fun invoke(ids: Set<String>, change: BulkTaskChange): BulkTaskResult =
        boundary.withForegroundActionBoundary {
            val intent = when (change) {
                is BulkTaskChange.Move -> {
                    require(categories.getCategoryById(change.categoryId) != null)
                    TaskWriteIntent.SetCategory(change.categoryId)
                }
                is BulkTaskChange.Priority -> TaskWriteIntent.SetPriority(change.priority)
                is BulkTaskChange.Shortlist -> TaskWriteIntent.SetShortlisted(change.selected)
            }
            val succeeded = mutableSetOf<String>()
            val failed = mutableSetOf<String>()
            for (id in ids) {
                try {
                    when (coordinator.write(id, intent).value) {
                        is TaskWriteResult.Updated, TaskWriteResult.NoOp -> succeeded += id
                        else -> failed += id
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    failed += id
                }
            }
            BulkTaskResult(succeeded, failed)
        }
}
