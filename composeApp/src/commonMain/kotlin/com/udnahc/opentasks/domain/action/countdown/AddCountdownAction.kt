package com.udnahc.opentasks.domain.action.countdown

import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.repository.CountdownRepository
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import org.lighthousegames.logging.logging

private val log = logging("AddCountdownAction")

class AddCountdownAction(
    private val repository: CountdownRepository,
    private val scheduleCountdownRemindersAction: ScheduleCountdownRemindersAction,
    private val rebuildReminderQueueAction: RebuildReminderQueueAction? = null,
    internal val accountBoundaryExecutor: AccountBoundaryExecutor? = null,
) {
    suspend operator fun invoke(countdown: Countdown) = accountBoundaryExecutor.withForegroundActionBoundary {
        log.d { "Adding countdown" }
        val now = localNow()
        val created = countdown.copy(createdAt = now, updatedAt = now)
        repository.insert(created)
        rebuildReminderQueueAction?.afterRecordChange { scheduleCountdownRemindersAction(created.id) }
            ?: scheduleCountdownRemindersAction(created.id)
    }
}
