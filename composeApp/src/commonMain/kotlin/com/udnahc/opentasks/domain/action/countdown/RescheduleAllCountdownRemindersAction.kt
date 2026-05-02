package com.udnahc.opentasks.domain.action.countdown

import com.udnahc.opentasks.data.repository.CountdownRepository
import org.lighthousegames.logging.logging

private val log = logging("RescheduleAllCountdownRemindersAction")

class RescheduleAllCountdownRemindersAction(
    private val countdownRepository: CountdownRepository,
    private val scheduleCountdownRemindersAction: ScheduleCountdownRemindersAction,
) {
    suspend operator fun invoke() {
        val countdowns = countdownRepository.getCountdownsWithTargetsUtc()
        log.d { "Rescheduling reminders for ${countdowns.size} countdowns" }
        countdowns.forEach { scheduleCountdownRemindersAction.invokeWithUtcCountdown(it) }
    }
}
