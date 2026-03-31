package com.udnahc.opentasks.domain.action.countdown

import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.repository.CountdownRepository
import org.lighthousegames.logging.logging

private val log = logging("AddCountdownAction")

class AddCountdownAction(private val repository: CountdownRepository) {
    suspend operator fun invoke(countdown: Countdown) {
        log.d { "Adding countdown: '${countdown.title}'" }
        val now = localNow()
        repository.insert(countdown.copy(createdAt = now, updatedAt = now))
    }
}
