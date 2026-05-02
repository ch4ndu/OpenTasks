package com.udnahc.opentasks.domain.action.countdown

import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.repository.CountdownRepository
import org.lighthousegames.logging.logging

private val log = logging("DeleteCountdownAction")

class DeleteCountdownAction(
    private val repository: CountdownRepository,
    private val scheduleCountdownRemindersAction: ScheduleCountdownRemindersAction,
) {
    suspend operator fun invoke(countdown: Countdown) {
        log.d { "Deleting countdown: ${countdown.id}" }
        repository.delete(countdown)
        scheduleCountdownRemindersAction(countdown.id)
    }
}
