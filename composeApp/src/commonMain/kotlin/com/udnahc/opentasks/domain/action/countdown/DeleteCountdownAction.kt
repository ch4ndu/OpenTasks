package com.udnahc.opentasks.domain.action.countdown

import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.repository.CountdownRepository
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import org.lighthousegames.logging.logging

private val log = logging("DeleteCountdownAction")

class DeleteCountdownAction(
    private val repository: CountdownRepository,
    private val scheduleCountdownRemindersAction: ScheduleCountdownRemindersAction,
    private val rebuildReminderQueueAction: RebuildReminderQueueAction? = null,
    internal val accountBoundaryExecutor: AccountBoundaryExecutor? = null,
) {
    suspend operator fun invoke(countdown: Countdown) = accountBoundaryExecutor.withForegroundActionBoundary {
        log.d { "Deleting countdown: ${countdown.id}" }
        val deleted = countdown.copy(isDeleted = true, updatedAt = localNow())
        repository.update(deleted)
        rebuildReminderQueueAction?.afterRecordChange { scheduleCountdownRemindersAction(deleted.id) }
            ?: scheduleCountdownRemindersAction(deleted.id)
    }
}
