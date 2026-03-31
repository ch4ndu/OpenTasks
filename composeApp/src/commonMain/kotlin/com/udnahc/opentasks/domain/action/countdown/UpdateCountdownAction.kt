package com.udnahc.opentasks.domain.action.countdown

import com.udnahc.opentasks.data.extensions.localNow
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.repository.CountdownRepository
import org.lighthousegames.logging.logging

private val log = logging("UpdateCountdownAction")

class UpdateCountdownAction(private val repository: CountdownRepository) {
    suspend operator fun invoke(countdown: Countdown) {
        log.d { "Updating countdown: ${countdown.id}" }
        repository.update(countdown.copy(updatedAt = localNow()))
    }
}
