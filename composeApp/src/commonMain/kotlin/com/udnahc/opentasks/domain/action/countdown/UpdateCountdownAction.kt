package com.udnahc.opentasks.domain.action.countdown

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
) {
    suspend operator fun invoke(countdown: Countdown) {
        log.d { "Updating countdown: ${countdown.id}" }
        val updated = countdown.copy(updatedAt = localNow())
        repository.update(updated)
        rebuildReminderQueueAction?.afterRecordChange { scheduleCountdownRemindersAction(updated.id) }
            ?: scheduleCountdownRemindersAction(updated.id)
    }
}
