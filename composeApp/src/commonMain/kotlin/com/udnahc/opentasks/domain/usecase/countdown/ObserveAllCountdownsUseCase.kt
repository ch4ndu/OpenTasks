package com.udnahc.opentasks.domain.usecase.countdown

import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.repository.CountdownRepository
import kotlinx.coroutines.flow.Flow

class ObserveAllCountdownsUseCase(private val repository: CountdownRepository) {
    operator fun invoke(): Flow<List<Countdown>> = repository.getAllCountdowns()
}
