package com.udnahc.opentasks.domain.action.countdown

import com.udnahc.opentasks.data.auth.AccountBoundaryExecutor
import com.udnahc.opentasks.data.auth.withForegroundActionBoundary
import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.repository.CountdownRepository
import com.udnahc.opentasks.domain.action.reminder.RebuildReminderQueueAction
import org.lighthousegames.logging.logging

private val log = logging("UpdateCountdownAction")

class UpdateCountdownAction(
    private val repository: CountdownRepository,
    private val scheduleCountdownRemindersAction: ScheduleCountdownRemindersAction,
    private val rebuildReminderQueueAction: RebuildReminderQueueAction? = null,
    internal val accountBoundaryExecutor: AccountBoundaryExecutor? = null,
) {
    suspend operator fun invoke(countdown: Countdown) = accountBoundaryExecutor.withForegroundActionBoundary {
        log.d { "Updating countdown: ${countdown.id}" }
        val updated = countdown.copy(updatedAt = maxOf(localNow(), countdown.updatedAt + 1))
        repository.update(updated)
        rebuildReminderQueueAction?.afterRecordChange { scheduleCountdownRemindersAction(updated.id) }
            ?: scheduleCountdownRemindersAction(updated.id)
    }
}
